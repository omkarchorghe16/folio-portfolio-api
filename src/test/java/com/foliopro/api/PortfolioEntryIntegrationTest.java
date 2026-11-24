package com.foliopro.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioEntryIntegrationTest {
    private static final String BASE = "/api/v1/users/{userId}/portfolio-entries/{market}";
    private static final String MONTH = BASE + "/months/{month}";
    private static final String SUMMARY = BASE + "/summary";
    private static final String SUB_A = "SUB_0123456789ABCDEF0123456789ABCDEF";
    private static final String SUB_B = "SUB_FEDCBA9876543210FEDCBA9876543210";
    private static final String CUSTOM = "CUSTOM_0123456789ABCDEF0123456789ABCDEF";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM portfolio_entries_usa");
        jdbc.update("DELETE FROM portfolio_entries_india");
        jdbc.update("DELETE FROM portfolio_sub_asset_types_usa");
        jdbc.update("DELETE FROM portfolio_sub_asset_types_india");
        jdbc.update("DELETE FROM portfolio_asset_types_usa");
        jdbc.update("DELETE FROM portfolio_asset_types_india");
    }

    private ResultActions putMonth(long userId, String market, String month, String entriesJson) throws Exception {
        return mvc.perform(put(MONTH, userId, market, month).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":" + entriesJson + "}"));
    }

    private static String entry(String assetType, String invested, String value) {
        return "{\"assetType\":\"" + assetType + "\",\"investedAmount\":" + invested
                + ",\"currentValue\":" + value + "}";
    }

    private ResultActions putWithAssetTypes(long userId, String market, String entriesJson, String assetTypesJson)
            throws Exception {
        return mvc.perform(put(MONTH, userId, market, "2026-10").contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":" + entriesJson + ",\"assetTypes\":" + assetTypesJson + "}"));
    }

    private static String assetType(String code, String name) {
        return "{\"assetType\":\"" + code + "\",\"name\":\"" + name + "\"}";
    }

    private ResultActions putWithSubAssetTypes(long userId, String market, String month, String entriesJson,
                                               String subAssetTypesJson) throws Exception {
        return mvc.perform(put(MONTH, userId, market, month).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":" + entriesJson + ",\"subAssetTypes\":" + subAssetTypesJson + "}"));
    }

    private static String subEntry(String assetType, String subAssetType, String invested, String value) {
        return "{\"assetType\":\"" + assetType + "\",\"subAssetType\":\"" + subAssetType
                + "\",\"investedAmount\":" + invested + ",\"currentValue\":" + value + "}";
    }

    private static String subAssetType(String code, String assetType, String name) {
        return "{\"subAssetType\":\"" + code + "\",\"assetType\":\"" + assetType + "\",\"name\":\"" + name + "\"}";
    }

    @Test
    void oneAssetTypeHasManySubAssetTypes() throws Exception {
        putWithSubAssetTypes(1, "US", "2026-10",
                "[" + subEntry("LAND", SUB_A, "100", "150") + "," + subEntry("LAND", SUB_B, "200", "210") + "]",
                "[" + subAssetType(SUB_A, "LAND", " Farm ") + "," + subAssetType(SUB_B, "LAND", "Plot") + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].subAssetType").value(SUB_A))
                .andExpect(jsonPath("$.subAssetTypes[0].name").value("Farm"))
                .andExpect(jsonPath("$.subAssetTypes[1].assetType").value("LAND"));
        mvc.perform(get(SUMMARY, 1, "US")).andExpect(jsonPath("$.assets", hasSize(1)))
                .andExpect(jsonPath("$.assets[0].currentValue").value(360))
                .andExpect(jsonPath("$.assets[0].subAssets", hasSize(2)))
                .andExpect(jsonPath("$.assets[0].subAssets[0].name").value("Farm"))
                .andExpect(jsonPath("$.totals.profit").value(60));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_sub_asset_types_usa s "
                + "JOIN portfolio_asset_types_usa a ON a.id = s.asset_type_id AND a.user_id = s.user_id "
                + "WHERE a.code = 'LAND' AND s.user_id = 1", Integer.class));
        // Rename one and drop the other's amounts; the sub asset type itself stays.
        putWithSubAssetTypes(1, "US", "2026-10", "[" + subEntry("LAND", SUB_A, "100", "160") + "]",
                "[" + subAssetType(SUB_A, "LAND", "Orchard") + "]").andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.subAssetTypes", hasSize(2)))
                .andExpect(jsonPath("$.subAssetTypes[0].name").value("Orchard"));
    }

    @Test
    void ownAmountsCanBeReplacedBySubAssetTypesAndBack() throws Exception {
        putMonth(1, "India", "2026-10", "[" + entry("CASH", "5", "5") + "]").andExpect(status().isOk());
        putWithSubAssetTypes(1, "India", "2026-10", "[" + subEntry("CASH", SUB_A, "5", "5") + "]",
                "[" + subAssetType(SUB_A, "CASH", "Savings") + "]").andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_entries_india", Integer.class));
        putMonth(1, "India", "2026-10", "[" + entry("CASH", "6", "6") + "]").andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].subAssetType").doesNotExist())
                .andExpect(jsonPath("$.subAssetTypes", hasSize(1)));
    }

    @Test
    void customAssetTypesCanHaveSubAssetTypesInTheSameRequest() throws Exception {
        mvc.perform(put(MONTH, 1, "US", "2026-10").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[" + subEntry(CUSTOM, SUB_A, "1", "2") + "],\"assetTypes\":["
                                + assetType(CUSTOM, "Retirement") + "],\"subAssetTypes\":["
                                + subAssetType(SUB_A, CUSTOM, "401(k)") + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].assetType").value(CUSTOM));
        mvc.perform(get(MONTH, 1, "US", "2026-11")).andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.entries[0].subAssetType").value(SUB_A));
    }

    @Test
    void hiddenTypesKeepPastMonthsAndAreNotSuggested() throws Exception {
        putWithSubAssetTypes(1, "US", "2026-09", "[" + subEntry("LAND", SUB_A, "1", "2") + ","
                        + subEntry("LAND", SUB_B, "3", "4") + "," + entry("CASH", "5", "5") + "]",
                "[" + subAssetType(SUB_A, "LAND", "Farm") + "," + subAssetType(SUB_B, "LAND", "Plot") + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.subAssetTypes[0].hidden").value(false));
        String hide = "{\"entries\":[" + subEntry("LAND", SUB_A, "1", "2") + "," + subEntry("LAND", SUB_B, "3", "4")
                + "," + entry("CASH", "5", "5") + "],\"assetTypes\":[{\"assetType\":\"CASH\",\"name\":\"Cash\","
                + "\"hidden\":true}],\"subAssetTypes\":[{\"subAssetType\":\"" + SUB_A
                + "\",\"assetType\":\"LAND\",\"name\":\"Farm\",\"hidden\":true}]}";
        mvc.perform(put(MONTH, 1, "US", "2026-09").contentType(MediaType.APPLICATION_JSON).content(hide))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries", hasSize(3)))
                .andExpect(jsonPath("$.assetTypes[8].hidden").value(true))
                .andExpect(jsonPath("$.subAssetTypes[0].hidden").value(true));
        mvc.perform(get(MONTH, 1, "US", "2026-09")).andExpect(jsonPath("$.entries", hasSize(3)));
        mvc.perform(get(SUMMARY, 1, "US")).andExpect(jsonPath("$.totals.currentValue").value(11));
        mvc.perform(get(MONTH, 1, "US", "2026-10")).andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].subAssetType").value(SUB_B));
        // A rename without "hidden" does not show it again; hidden=false does.
        putWithSubAssetTypes(1, "US", "2026-08", "[]", "[" + subAssetType(SUB_A, "LAND", "Old farm") + "]")
                .andExpect(jsonPath("$.subAssetTypes[0].hidden").value(true));
        putWithAssetTypes(1, "US", "[]", "[{\"assetType\":\"CASH\",\"name\":\"Cash\",\"hidden\":false}]")
                .andExpect(jsonPath("$.assetTypes[8].hidden").value(false));
    }

    @Test
    void hidingAnAssetTypeLeavesItsSubAssetTypesOutOfSuggestions() throws Exception {
        putWithSubAssetTypes(1, "US", "2026-09", "[" + subEntry("LAND", SUB_A, "1", "2") + "]",
                "[" + subAssetType(SUB_A, "LAND", "Farm") + "]").andExpect(status().isOk());
        putWithAssetTypes(1, "US", "[]", "[{\"assetType\":\"LAND\",\"name\":\"Land\",\"hidden\":true}]")
                .andExpect(status().isOk());
        mvc.perform(get(MONTH, 1, "US", "2026-10")).andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void sameSubNameIsAllowedUnderAnotherAssetType() throws Exception {
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType(SUB_A, "LAND", "Other") + ","
                + subAssetType(SUB_B, "CASH", "Other") + "]").andExpect(status().isOk());
    }

    @Test
    void invalidSubAssetTypesSaveNothing() throws Exception {
        String both = "[" + entry("LAND", "1", "1") + "," + subEntry("LAND", SUB_A, "1", "1") + "]";
        String farm = "[" + subAssetType(SUB_A, "LAND", "Farm") + "]";
        putWithSubAssetTypes(1, "US", "2026-10", both, farm).andExpect(status().isBadRequest());
        putWithSubAssetTypes(1, "US", "2026-10", "[" + subEntry("CASH", SUB_A, "1", "1") + "]", farm)
                .andExpect(status().isBadRequest());
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType(SUB_A, "BOATS", "x") + "]")
                .andExpect(status().isBadRequest());
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType("SUB_1", "LAND", "x") + "]")
                .andExpect(status().isBadRequest());
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType(SUB_A, "LAND", "Farm") + ","
                + subAssetType(SUB_B, "LAND", "farm") + "]").andExpect(status().isBadRequest());
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType(SUB_A, "LAND", " ") + "]")
                .andExpect(status().isBadRequest());
        String twice = "[" + subEntry("LAND", SUB_A, "1", "1") + "," + subEntry("LAND", SUB_A, "2", "2") + "]";
        putWithSubAssetTypes(1, "US", "2026-10", twice, farm).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_sub_asset_types_usa", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_asset_types_usa", Integer.class));
        // Once saved it cannot move, and other users and countries cannot use it.
        putWithSubAssetTypes(1, "US", "2026-10", "[]", farm).andExpect(status().isOk());
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType(SUB_A, "CASH", "Farm") + "]")
                .andExpect(status().isBadRequest());
        putMonth(2, "US", "2026-10", "[" + subEntry("LAND", SUB_A, "1", "1") + "]").andExpect(status().isBadRequest());
        putMonth(1, "India", "2026-10", "[" + subEntry("LAND", SUB_A, "1", "1") + "]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseRejectsAnotherUsersAssetTypeAndDuplicateOwnEntries() throws Exception {
        putWithSubAssetTypes(1, "US", "2026-10", "[]", "[" + subAssetType(SUB_A, "LAND", "Farm") + "]")
                .andExpect(status().isOk());
        Long assetTypeId = jdbc.queryForObject("SELECT id FROM portfolio_asset_types_usa", Long.class);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO portfolio_sub_asset_types_usa (user_id, asset_type_id, code, name) VALUES (2, ?, 'X', 'X')",
                assetTypeId));
        String insert = "INSERT INTO portfolio_entries_usa (user_id, entry_month, asset_type, invested_amount, "
                + "current_value) VALUES (1, DATE '2026-10-01', 'CASH', 1, 1)";
        jdbc.update(insert);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert));
    }

    @Test
    void defaultAssetTypesAreListedPerCountry() throws Exception {
        mvc.perform(get(MONTH, 1, "US", "2026-10")).andExpect(jsonPath("$.assetTypes", hasSize(11)))
                .andExpect(jsonPath("$.assetTypes[0].name").value("Stocks"))
                .andExpect(jsonPath("$.assetTypes[8].name").value("Cash"));
        mvc.perform(get(MONTH, 1, "India", "2026-10"))
                .andExpect(jsonPath("$.assetTypes[8].name").value("Cash / fixed deposits"));
    }

    @Test
    void customTypesAndRenamesAreSavedWithTheMonth() throws Exception {
        putWithAssetTypes(1, "US", "[" + entry(CUSTOM, "10", "12") + "," + entry("STOCKS", "1", "2") + "]",
                "[" + assetType(CUSTOM, " 401(k) ") + "," + assetType("STOCKS", "Equities") + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetTypes", hasSize(12)))
                .andExpect(jsonPath("$.assetTypes[11].name").value("401(k)"));
        mvc.perform(get(SUMMARY, 1, "US")).andExpect(jsonPath("$.assets[0].name").value("Equities"))
                .andExpect(jsonPath("$.assets[1].name").value("401(k)"))
                .andExpect(jsonPath("$.assets[1].assetType").value(CUSTOM));
        // Renaming a custom type and repeating a request keep one row per type.
        putWithAssetTypes(1, "US", "[" + entry(CUSTOM, "10", "12") + "]", "[" + assetType(CUSTOM, "IRA") + "]")
                .andExpect(status().isOk());
        putMonth(1, "US", "2026-09", "[" + entry(CUSTOM, "5", "6") + "]").andExpect(status().isOk())
                .andExpect(jsonPath("$.assetTypes[11].name").value("IRA"))
                .andExpect(jsonPath("$.assetTypes[0].name").value("Equities"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_asset_types_usa", Integer.class));
    }

    @Test
    void assetTypesBelongToOneUserAndCountry() throws Exception {
        putWithAssetTypes(1, "US", "[]", "[" + assetType(CUSTOM, "401(k)") + "]").andExpect(status().isOk());
        putMonth(1, "India", "2026-10", "[" + entry(CUSTOM, "1", "1") + "]").andExpect(status().isBadRequest());
        putMonth(2, "US", "2026-10", "[" + entry(CUSTOM, "1", "1") + "]").andExpect(status().isBadRequest());
        mvc.perform(get(MONTH, 2, "US", "2026-10")).andExpect(jsonPath("$.assetTypes", hasSize(11)));
    }

    @Test
    void namesMayBeSwappedInOneRequest() throws Exception {
        putWithAssetTypes(1, "US", "[]", "[" + assetType("STOCKS", "ETFs") + "," + assetType("ETFS", "Stocks") + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.assetTypes[0].name").value("ETFs"));
    }

    @Test
    void invalidAssetTypesSaveNothing() throws Exception {
        putWithAssetTypes(1, "US", "[]", "[" + assetType(CUSTOM, "land") + "]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Asset type land already exists"));
        putWithAssetTypes(1, "US", "[]", "[" + assetType("BOATS", "Boats") + "]").andExpect(status().isBadRequest());
        putWithAssetTypes(1, "US", "[]", "[" + assetType(CUSTOM, " ") + "]").andExpect(status().isBadRequest());
        putWithAssetTypes(1, "US", "[]", "[" + assetType(CUSTOM, "x".repeat(61)) + "]")
                .andExpect(status().isBadRequest());
        // A bad entry also undoes the new asset type.
        putWithAssetTypes(1, "US", "[" + entry("BOATS", "1", "1") + "]", "[" + assetType(CUSTOM, "Boat") + "]")
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_asset_types_usa", Integer.class));
    }

    @Test
    void emptySummaryHasNoTotals() throws Exception {
        mvc.perform(get(SUMMARY, 1, "India")).andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("India"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.latestMonth").doesNotExist())
                .andExpect(jsonPath("$.totals").doesNotExist())
                .andExpect(jsonPath("$.assets", hasSize(0)))
                .andExpect(jsonPath("$.history", hasSize(0)));
    }

    @Test
    void savedMonthIsReturnedAndSummarized() throws Exception {
        putMonth(1, "usa", "2026-10", "[" + entry("STOCKS", "900", "1000") + "]").andExpect(status().isOk());
        putMonth(1, "US", "2026-11", "[" + entry("ETFS", "100.50", "120.75") + "," + entry("STOCKS", "900", "1100")
                + "]").andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.entries[0].assetType").value("STOCKS"));
        mvc.perform(get(SUMMARY, 1, "US")).andExpect(status().isOk())
                .andExpect(jsonPath("$.latestMonth").value("2026-11"))
                .andExpect(jsonPath("$.totals.currentValue").value(1220.75))
                .andExpect(jsonPath("$.totals.profit").value(220.25))
                .andExpect(jsonPath("$.totals.monthlyReturnPercent").value(12.03))
                .andExpect(jsonPath("$.assets", hasSize(2)))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].month").value("2026-10"));
    }

    @Test
    void savingAgainUpdatesInsertsAndDeletes() throws Exception {
        putMonth(1, "US", "2026-10", "[" + entry("STOCKS", "1", "2") + "," + entry("LAND", "3", "4") + "]")
                .andExpect(status().isOk());
        Long stocksId = jdbc.queryForObject(
                "SELECT id FROM portfolio_entries_usa WHERE asset_type='STOCKS'", Long.class);
        putMonth(1, "US", "2026-10", "[" + entry("STOCKS", "5", "6") + "," + entry("CASH", "7", "7") + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries", hasSize(2)));
        assertEquals(stocksId, jdbc.queryForObject(
                "SELECT id FROM portfolio_entries_usa WHERE asset_type='STOCKS' AND current_value=6", Long.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM portfolio_entries_usa WHERE asset_type='LAND'", Integer.class));
        putMonth(1, "US", "2026-10", "[]").andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(false));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_entries_usa", Integer.class));
    }

    @Test
    void usersAndCountriesAreSeparate() throws Exception {
        putMonth(1, "US", "2026-10", "[" + entry("STOCKS", "1", "2") + "]").andExpect(status().isOk());
        putMonth(1, "India", "2026-10", "[" + entry("LAND", "3", "4") + "]").andExpect(status().isOk());
        putMonth(2, "US", "2026-10", "[]").andExpect(status().isOk());
        assertEquals("US/USD", jdbc.queryForObject(
                "SELECT country || '/' || currency FROM portfolio_entries_usa WHERE user_id=1", String.class));
        assertEquals("India/INR", jdbc.queryForObject(
                "SELECT country || '/' || currency FROM portfolio_entries_india WHERE user_id=1", String.class));
        mvc.perform(get(SUMMARY, 2, "US")).andExpect(jsonPath("$.assets", hasSize(0)));
        mvc.perform(get(SUMMARY, 1, "US")).andExpect(jsonPath("$.assets", hasSize(1)))
                .andExpect(jsonPath("$.assets[0].assetType").value("STOCKS"));
    }

    @Test
    void unsavedMonthSuggestsTheLatestEarlierMonth() throws Exception {
        mvc.perform(get(MONTH, 1, "US", "2026-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.sourceMonth").doesNotExist())
                .andExpect(jsonPath("$.entries", hasSize(0)));
        putMonth(1, "US", "2026-05", "[" + entry("LAND", "1", "1") + "]").andExpect(status().isOk());
        putMonth(1, "US", "2026-08", "[" + entry("STOCKS", "1", "2") + "]").andExpect(status().isOk());
        putMonth(1, "US", "2026-11", "[" + entry("CASH", "1", "1") + "]").andExpect(status().isOk());
        mvc.perform(get(MONTH, 1, "US", "2026-10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.sourceMonth").value("2026-08"))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].assetType").value("STOCKS"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_entries_usa", Integer.class));
    }

    @Test
    void invalidRequestsReturn400AndSaveNothing() throws Exception {
        putMonth(1, "US", YearMonth.now().plusMonths(1).toString(), "[]").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-13", "[]").andExpect(status().isBadRequest());
        putMonth(1, "UK", "2026-10", "[]").andExpect(status().isBadRequest());
        putMonth(0, "US", "2026-10", "[]").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-10", "null").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-10", "[" + entry("BOATS", "1", "1") + "]").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-10", "[" + entry("LAND", "-1", "1") + "]").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-10", "[" + entry("LAND", "1.001", "1") + "]").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-10", "[" + entry("LAND", "1", "null") + "]").andExpect(status().isBadRequest());
        putMonth(1, "US", "2026-10", "[" + entry("LAND", "1", "1") + "," + entry("LAND", "2", "2") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate asset type: LAND"));
        mvc.perform(get(SUMMARY, 1, "UK")).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_entries_usa", Integer.class));
    }
}
