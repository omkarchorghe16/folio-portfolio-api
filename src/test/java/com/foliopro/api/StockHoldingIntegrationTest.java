package com.foliopro.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockHoldingIntegrationTest {
    private static final String BASE = "/api/v1/users/{userId}/stock-holdings/{market}";
    private static final String ONE = BASE + "/{id}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM stock_holdings_usa");
        jdbc.update("DELETE FROM stock_holdings_india");
    }

    private ResultActions postHolding(long userId, String market, String symbol, String assetType, String quantity,
                                      String averageCost) throws Exception {
        return mvc.perform(post(BASE, userId, market).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"" + symbol + "\",\"name\":\"Test company\",\"assetType\":\"" + assetType
                        + "\",\"quantity\":" + quantity + ",\"averageCost\":" + averageCost + "}"));
    }

    private ResultActions putPosition(long userId, String market, long id, String quantity, String averageCost)
            throws Exception {
        return mvc.perform(put(ONE, userId, market, id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + ",\"averageCost\":" + averageCost + "}"));
    }

    private long idOf(ResultActions created) throws Exception {
        return mapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void createsAndListsHoldingsBySymbol() throws Exception {
        postHolding(1, "US", "nvda", "STOCK", "2.5", "100.25").andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("NVDA"))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.exchange").doesNotExist());
        postHolding(1, "USA", "BRK.B", "STOCK", "1", "0").andExpect(status().isCreated());
        mvc.perform(get(BASE, 1, "US")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("BRK.B"))
                .andExpect(jsonPath("$[1].quantity").value(2.5))
                .andExpect(jsonPath("$[1].averageCost").value(100.25));
    }

    @Test
    void indiaHoldingsUseNseSymbolsAndTheirOwnTable() throws Exception {
        postHolding(1, "India", "m&m.ns", "STOCK", "10", "2500").andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("M&M"))
                .andExpect(jsonPath("$.exchange").value("NSE"))
                .andExpect(jsonPath("$.country").value("India"))
                .andExpect(jsonPath("$.currency").value("INR"));
        mvc.perform(get(BASE, 1, "US")).andExpect(jsonPath("$", hasSize(0)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_holdings_india", Integer.class));
    }

    @Test
    void duplicateSymbolReturns409ForTheSameUserOnly() throws Exception {
        postHolding(1, "India", "NIFTYBEES", "ETF", "5", "250").andExpect(status().isCreated());
        postHolding(1, "India", "NIFTYBEES.NS", "ETF", "5", "250").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("NIFTYBEES is already in your holdings"));
        postHolding(2, "India", "NIFTYBEES", "ETF", "5", "250").andExpect(status().isCreated());
    }

    @Test
    void updatesAndDeletesOnlyTheOwnersHolding() throws Exception {
        long id = idOf(postHolding(1, "US", "VOO", "ETF", "1", "400").andExpect(status().isCreated()));
        putPosition(2, "US", id, "9", "9").andExpect(status().isNotFound());
        putPosition(1, "India", id, "9", "9").andExpect(status().isNotFound());
        mvc.perform(delete(ONE, 2, "US", id)).andExpect(status().isNotFound());
        putPosition(1, "US", id, "3", "410.5").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
        mvc.perform(get(BASE, 1, "US")).andExpect(jsonPath("$[0].averageCost").value(410.5));
        mvc.perform(delete(ONE, 1, "US", id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE, 1, "US")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void invalidRequestsReturn400() throws Exception {
        postHolding(1, "US", "ABR$D", "STOCK", "1", "1").andExpect(status().isBadRequest());
        postHolding(1, "India", "A B", "STOCK", "1", "1").andExpect(status().isBadRequest());
        postHolding(1, "UK", "VOD", "STOCK", "1", "1").andExpect(status().isBadRequest());
        postHolding(1, "US", "AAPL", "BOND", "1", "1").andExpect(status().isBadRequest());
        postHolding(1, "US", "AAPL", "STOCK", "0", "1").andExpect(status().isBadRequest());
        postHolding(1, "US", "AAPL", "STOCK", "1", "-1").andExpect(status().isBadRequest());
        postHolding(1, "US", "", "STOCK", "1", "1").andExpect(status().isBadRequest());
        mvc.perform(get(BASE, 1, "UK")).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stock_holdings_usa", Integer.class));
    }
}
