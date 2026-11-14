package com.foliopro.api;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatchlistColumnPreferencesIntegrationTest {
    private static final String URL = "/api/v1/users/{userId}/watchlist-column-preferences";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM watchlist_column_preferences");
    }

    private ResultActions putColumns(long userId, String columnsJson) throws Exception {
        return mvc.perform(put(URL, userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"columns\":" + columnsJson + "}"));
    }

    private static String col(String key, int order) {
        return "{\"columnKey\":\"" + key + "\",\"displayOrder\":" + order + "}";
    }

    @Test
    void getWithoutSavedRowsReturns404() throws Exception {
        mvc.perform(get(URL, 1)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No watchlist column preferences saved"));
    }

    @Test
    void putThenGetReturnsSameOrder() throws Exception {
        putColumns(1, "[" + col("price", 2) + "," + col("symbol", 1) + "," + col("change", 3) + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.columns", hasSize(3)))
                .andExpect(jsonPath("$.columns[0].columnKey").value("symbol"))
                .andExpect(jsonPath("$.updatedAt").exists());
        mvc.perform(get(URL, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.columns[0].columnKey").value("symbol"))
                .andExpect(jsonPath("$.columns[0].displayOrder").value(1))
                .andExpect(jsonPath("$.columns[1].columnKey").value("price"))
                .andExpect(jsonPath("$.columns[1].displayOrder").value(2))
                .andExpect(jsonPath("$.columns[2].columnKey").value("change"))
                .andExpect(jsonPath("$.columns[2].displayOrder").value(3))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void reorderingPutWinsWithoutConstraintError() throws Exception {
        putColumns(1, "[" + col("symbol", 1) + "," + col("price", 2) + "," + col("change", 3) + "]")
                .andExpect(status().isOk());
        putColumns(1, "[" + col("change", 1) + "," + col("symbol", 2) + "," + col("price", 3) + "]")
                .andExpect(status().isOk());
        mvc.perform(get(URL, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(3)))
                .andExpect(jsonPath("$.columns[0].columnKey").value("change"))
                .andExpect(jsonPath("$.columns[1].columnKey").value("symbol"))
                .andExpect(jsonPath("$.columns[2].columnKey").value("price"));
    }

    @Test
    void putWithFewerColumnsRemovesDroppedOnes() throws Exception {
        putColumns(1, "[" + col("symbol", 1) + "," + col("price", 2) + "," + col("change", 3) + "]")
                .andExpect(status().isOk());
        putColumns(1, "[" + col("price", 1) + "]").andExpect(status().isOk());
        mvc.perform(get(URL, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(1)))
                .andExpect(jsonPath("$.columns[0].columnKey").value("price"))
                .andExpect(jsonPath("$.columns[0].displayOrder").value(1));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM watchlist_column_preferences WHERE user_id=1", Integer.class));
    }

    @Test
    void usersAreIsolated() throws Exception {
        putColumns(1, "[" + col("symbol", 1) + "," + col("price", 2) + "]").andExpect(status().isOk());
        putColumns(2, "[" + col("trend", 1) + "]").andExpect(status().isOk());
        putColumns(1, "[" + col("price", 1) + "," + col("symbol", 2) + "]").andExpect(status().isOk());
        mvc.perform(get(URL, 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.columns", hasSize(1)))
                .andExpect(jsonPath("$.columns[0].columnKey").value("trend"));
        mvc.perform(get(URL, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.columns", hasSize(2)))
                .andExpect(jsonPath("$.columns[0].columnKey").value("price"));
        mvc.perform(get(URL, 3)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateColumnKeyIsRejected() throws Exception {
        putColumns(1, "[" + col("symbol", 1) + "," + col("symbol", 2) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate column key: symbol"));
        mvc.perform(get(URL, 1)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateDisplayOrderIsRejected() throws Exception {
        putColumns(1, "[" + col("symbol", 1) + "," + col("price", 1) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate display order: 1"));
    }

    @Test
    void emptyColumnsAreRejected() throws Exception {
        putColumns(1, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.columns").exists());
    }

    @Test
    void blankColumnKeyIsRejected() throws Exception {
        putColumns(1, "[" + col(" ", 1) + "]").andExpect(status().isBadRequest());
        putColumns(1, "[" + col("", 1) + "]").andExpect(status().isBadRequest());
    }

    @Test
    void invalidColumnKeyAndOrderAreRejected() throws Exception {
        putColumns(1, "[" + col("bad-key", 1) + "]").andExpect(status().isBadRequest());
        putColumns(1, "[" + col("symbol", 0) + "]").andExpect(status().isBadRequest());
        putColumns(1, "[{\"columnKey\":\"symbol\"}]").andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveUserIdIsRejected() throws Exception {
        mvc.perform(get(URL, 0)).andExpect(status().isBadRequest());
        putColumns(0, "[" + col("symbol", 1) + "]").andExpect(status().isBadRequest());
    }

    @Test
    void gapsAreNormalizedToContiguousOrder() throws Exception {
        putColumns(1, "[" + col("price", 5) + "," + col("symbol", 2) + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].columnKey").value("symbol"))
                .andExpect(jsonPath("$.columns[0].displayOrder").value(1))
                .andExpect(jsonPath("$.columns[1].columnKey").value("price"))
                .andExpect(jsonPath("$.columns[1].displayOrder").value(2));
        assertEquals(2, jdbc.queryForObject(
                "SELECT display_order FROM watchlist_column_preferences WHERE user_id=1 AND column_key='price'",
                Integer.class));
    }

    @Test
    void unknownColumnKeysAreAccepted() throws Exception {
        putColumns(1, "[" + col("someFutureColumn_1", 1) + "]").andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].columnKey").value(containsString("someFutureColumn")));
    }
}
