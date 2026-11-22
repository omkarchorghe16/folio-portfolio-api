package com.foliopro.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foliopro.api.dto.ApiDtos.*;
import com.foliopro.api.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ApiIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired WatchlistRepository watchlists; @Autowired JdbcTemplate jdbc;
 @Test void createsAndPagesWatchlists() throws Exception {
   String response=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test list\",\"market\":\"US\",\"userId\":1}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.id").exists()).andReturn().getResponse().getContentAsString();
   long id=mapper.readTree(response).get("id").asLong();
   org.junit.jupiter.api.Assertions.assertEquals(1L,
     jdbc.queryForObject("SELECT user_id FROM watchlists_usa WHERE id=?",Long.class,id));
   org.junit.jupiter.api.Assertions.assertEquals(1L,mapper.readTree(response).get("userId").asLong());
  mvc.perform(post("/api/v1/watchlists/"+id+"/items").contentType(MediaType.APPLICATION_JSON)
      .content("{\"items\":[{\"symbol\":\"AAPL\",\"name\":\"Apple Inc.\",\"isFavorite\":true}]}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$[0].isFavorite").value(true));
  org.junit.jupiter.api.Assertions.assertEquals(1,
    jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa WHERE watchlist_id=? AND is_favorite=TRUE",Integer.class,id));
  mvc.perform(get("/api/v1/watchlists?market=US")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
 }
 @Test void defaultsUsaWatchlistUserIdToOneWhenOmitted() throws Exception {
   String response=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
       .content("{\"name\":\"Default user list\",\"market\":\"US\"}"))
     .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(1))
     .andReturn().getResponse().getContentAsString();
   long id=mapper.readTree(response).get("id").asLong();
   org.junit.jupiter.api.Assertions.assertEquals(1L,
     jdbc.queryForObject("SELECT user_id FROM watchlists_usa WHERE id=?",Long.class,id));
 }
 @Test void listsAllWatchlistsWithAllTickerItemsWithoutPagination() throws Exception {
  String response=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
      .content("{\"name\":\"Paginated ticker list\",\"market\":\"US\"}"))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  long id=mapper.readTree(response).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/"+id+"/items").contentType(MediaType.APPLICATION_JSON)
      .content("{\"items\":[{\"symbol\":\"AAPL\",\"name\":\"Apple Inc.\",\"isFavorite\":true},{\"symbol\":\"MSFT\",\"name\":\"Microsoft Corporation\"}]}"))
    .andExpect(status().isOk());

  // More watchlists than the old 100-per-page cap, and more items than the old 15-per-page default.
  for(int n=0;n<101;n++){
   mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
     .content("{\"name\":\"Bulk "+n+"\",\"market\":\"US\"}")).andExpect(status().isOk());
  }
  StringBuilder items=new StringBuilder();
  for(int n=0;n<20;n++){ items.append(n==0?"":",").append("{\"symbol\":\"T").append(n).append("\"}"); }
  mvc.perform(post("/api/v1/watchlists/"+id+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":["+items+"]}")).andExpect(status().isOk());
  long usaLists=jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_usa",Long.class);
  String all=mvc.perform(get("/api/v1/watchlists").param("market","US"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value((int)usaLists))
    .andReturn().getResponse().getContentAsString();
  var content=mapper.readTree(all);
  mvc.perform(get("/api/v1/watchlists/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(22));
  var matching=java.util.stream.StreamSupport.stream(content.spliterator(),false)
      .filter(entry->entry.get("id").asLong()==id).findFirst().orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("Paginated ticker list",matching.get("name").asText());
  org.junit.jupiter.api.Assertions.assertEquals("AAPL",matching.get("items").get(0).get("symbol").asText());
  org.junit.jupiter.api.Assertions.assertEquals("Apple Inc.",matching.get("items").get(0).get("name").asText());
  org.junit.jupiter.api.Assertions.assertTrue(matching.get("items").get(0).get("isFavorite").asBoolean());
  org.junit.jupiter.api.Assertions.assertEquals("MSFT",matching.get("items").get(1).get("symbol").asText());
 }
 @Test void validatesWatchlistAndBulkDuplicateSymbols() throws Exception {
  mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"market\":\"bad\"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").exists());
  mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Dups\",\"market\":\"US\"}"));
  Long id=watchlists.findAll().get(0).getId();
  mvc.perform(post("/api/v1/watchlists/"+id+"/items").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"symbol\":\"AAPL\",\"name\":\"Apple\"},{\"symbol\":\"aapl\",\"name\":\"Apple duplicate\"}]}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Duplicate symbol")));
 }
 @Test void importsColumnOrientedWatchlistsFromCsv() throws Exception {
  long usaBefore=jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_usa",Long.class);
  long usaItemsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa",Long.class);
  MockMultipartFile file=new MockMultipartFile("file","watchlists.csv","text/csv",
   "\uFEFFGrowth Picks,\"Semiconductors, AI\"\r\nAAPL,NVDA\r\nMSFT,\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   mvc.perform(multipart("/api/v1/watchlists/import?market=USA&userId=1").file(file))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.watchlistsCreated").value(2))
   .andExpect(jsonPath("$.itemsCreated").value(3))
   .andExpect(jsonPath("$.watchlistNames[0]").value("Growth Picks"))
   .andExpect(jsonPath("$.watchlistNames[1]").value("Semiconductors, AI"));
  org.junit.jupiter.api.Assertions.assertEquals(usaBefore+2,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_usa",Long.class));
   org.junit.jupiter.api.Assertions.assertEquals(usaItemsBefore+3,
    jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa",Long.class));
   org.junit.jupiter.api.Assertions.assertEquals(1L,
    jdbc.queryForObject("SELECT MIN(user_id) FROM watchlists_usa",Long.class));
 }
 @Test void importsIntoIndiaWatchlistTable() throws Exception {
  long indiaBefore=jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_india",Long.class);
  long indiaItemsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_india",Long.class);
  MockMultipartFile file=new MockMultipartFile("file","india.csv","text/csv",
   "India Picks\nRELIANCE.NS\nM&M.NS\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  mvc.perform(multipart("/api/v1/watchlists/import?market=India&userId=1").file(file))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.watchlistsCreated").value(1))
   .andExpect(jsonPath("$.itemsCreated").value(2));
  org.junit.jupiter.api.Assertions.assertEquals(indiaBefore+1,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_india",Long.class));
  org.junit.jupiter.api.Assertions.assertEquals(indiaItemsBefore+2,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_india",Long.class));
  org.junit.jupiter.api.Assertions.assertEquals(1L,
   jdbc.queryForObject("SELECT user_id FROM watchlists_india WHERE name='India Picks'",Long.class));
 }
 @Test void rejectsDuplicateSymbolsBeforeCreatingImportedWatchlists() throws Exception {
  long before=watchlists.count();
  MockMultipartFile file=new MockMultipartFile("file","duplicates.csv","text/csv",
   "Duplicate Test\nAAPL\naapl\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  mvc.perform(multipart("/api/v1/watchlists/import").file(file))
   .andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Duplicate symbol")));
  org.junit.jupiter.api.Assertions.assertEquals(before,watchlists.count());
 }
 @Test void togglesFavoriteOnUsaAndIndiaWatchlists() throws Exception {
  for(String market:new String[]{"US","India"}){
   String created=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
     .content("{\"name\":\"Fav "+market+"\",\"market\":\""+market+"\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.isFavorite").value(false)).andReturn().getResponse().getContentAsString();
   long id=mapper.readTree(created).get("id").asLong();
   String table=market.equals("US")?"watchlists_usa":"watchlists_india";
   mvc.perform(patch("/api/v1/watchlists/"+id+"/favorite?userId=1").contentType(MediaType.APPLICATION_JSON).content("{\"isFavorite\":true}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.isFavorite").value(true)).andExpect(jsonPath("$.market").value(market));
   org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE,
    jdbc.queryForObject("SELECT is_favorite FROM "+table+" WHERE id=?",Boolean.class,id));
   mvc.perform(patch("/api/v1/watchlists/"+id+"/favorite").contentType(MediaType.APPLICATION_JSON).content("{\"isFavorite\":false}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.isFavorite").value(false));
   org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE,
    jdbc.queryForObject("SELECT is_favorite FROM "+table+" WHERE id=?",Boolean.class,id));
   mvc.perform(patch("/api/v1/watchlists/"+id+"/favorite?userId=2").contentType(MediaType.APPLICATION_JSON).content("{\"isFavorite\":true}"))
    .andExpect(status().isNotFound());
   mvc.perform(patch("/api/v1/watchlists/"+id+"/favorite").contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isBadRequest());
  }
 }
 @Test void listsFavoriteWatchlistsFirstForUsaAndIndia() throws Exception {
  for(String market:new String[]{"US","India"}){
   long first=0,second=0;
   for(int n=0;n<2;n++){
    String created=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
      .content("{\"name\":\"Order "+market+" "+n+"\",\"market\":\""+market+"\"}"))
     .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    long id=mapper.readTree(created).get("id").asLong();
    if(n==0)first=id;else second=id;
   }
   // Star only the newer list: it must now be listed before the older, unstarred one.
   mvc.perform(patch("/api/v1/watchlists/"+second+"/favorite").contentType(MediaType.APPLICATION_JSON).content("{\"isFavorite\":true}"))
    .andExpect(status().isOk());
   var content=mapper.readTree(mvc.perform(get("/api/v1/watchlists?market="+market))
     .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   boolean seenNonFavorite=false; long previousId=-1; boolean previousFavorite=true; int firstIndex=-1,secondIndex=-1;
   for(int i=0;i<content.size();i++){
    var w=content.get(i); boolean fav=w.get("isFavorite").asBoolean(); long id=w.get("id").asLong();
    org.junit.jupiter.api.Assertions.assertEquals(market,w.get("market").asText());
    if(fav) org.junit.jupiter.api.Assertions.assertFalse(seenNonFavorite,"favorite listed after a non-favorite");
    else seenNonFavorite=true;
    if(fav==previousFavorite) org.junit.jupiter.api.Assertions.assertTrue(id>previousId,"ids not ascending within a group");
    previousId=id; previousFavorite=fav;
    if(id==first)firstIndex=i; if(id==second)secondIndex=i;
   }
   org.junit.jupiter.api.Assertions.assertTrue(secondIndex>=0&&firstIndex>secondIndex);
  }
 }
 @Test void addsStocksToOwnedIndiaWatchlistOnly() throws Exception {
  String created=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"India adds\",\"market\":\"India\",\"userId\":1}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  long id=mapper.readTree(created).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/india/"+id+"/items?userId=1").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"reliance\",\"name\":\"Reliance Industries Limited\"},{\"symbol\":\"M&M\",\"name\":\"Mahindra & Mahindra Limited\"},{\"symbol\":\"NIFTYBEES\",\"name\":\"Nippon India ETF Nifty 50 BeES\"}]}"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(3))
   .andExpect(jsonPath("$[0].symbol").value("RELIANCE")).andExpect(jsonPath("$[0].name").value("Reliance Industries Limited"));
  org.junit.jupiter.api.Assertions.assertEquals(3,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_india WHERE watchlist_id=?",Integer.class,id));
  // Already in the list: rejected, and nothing from the request is saved.
  mvc.perform(post("/api/v1/watchlists/india/"+id+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"TCS\",\"name\":\"TCS\"},{\"symbol\":\"RELIANCE\",\"name\":\"Reliance\"}]}"))
   .andExpect(status().isBadRequest());
  org.junit.jupiter.api.Assertions.assertEquals(3,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_india WHERE watchlist_id=?",Integer.class,id));
  // Another user's list.
  mvc.perform(post("/api/v1/watchlists/india/"+id+"/items?userId=2").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"INFY\"}]}")).andExpect(status().isNotFound());
  // A USA watchlist is not reachable through the India endpoint.
  String usa=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"USA not India\",\"market\":\"US\"}")).andReturn().getResponse().getContentAsString();
  mvc.perform(post("/api/v1/watchlists/india/"+mapper.readTree(usa).get("id").asLong()+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"AAPL\"}]}")).andExpect(status().isNotFound());
 }
 @Test void removesStockFromOwnedIndiaWatchlistOnly() throws Exception {
  String created=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"India removes\",\"market\":\"India\"}")).andReturn().getResponse().getContentAsString();
  long id=mapper.readTree(created).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/india/"+id+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"M&M\"},{\"symbol\":\"TCS\"}]}")).andExpect(status().isCreated());
  // Another user's list, an unknown symbol and a USA list are all 404 and delete nothing.
  mvc.perform(delete("/api/v1/watchlists/india/"+id+"/items/TCS?userId=2")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/v1/watchlists/india/"+id+"/items/INFY")).andExpect(status().isNotFound());
  String usa=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"USA keep\",\"market\":\"US\"}")).andReturn().getResponse().getContentAsString();
  long usaId=mapper.readTree(usa).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/"+usaId+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"TCS\"}]}")).andExpect(status().isOk());
  mvc.perform(delete("/api/v1/watchlists/india/"+usaId+"/items/TCS")).andExpect(status().isNotFound());
  org.junit.jupiter.api.Assertions.assertEquals(1,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa WHERE watchlist_id=?",Integer.class,usaId));
  // Owner deletes, case-insensitively and with & in the symbol (URL-encoded as the UI sends it).
  mvc.perform(delete("/api/v1/watchlists/india/"+id+"/items/tcs?userId=1")).andExpect(status().isNoContent());
  mvc.perform(delete("/api/v1/watchlists/india/{id}/items/{symbol}",id,"M&M")).andExpect(status().isNoContent());
  org.junit.jupiter.api.Assertions.assertEquals(0,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_india WHERE watchlist_id=?",Integer.class,id));
 }
 @Test void addsAndRemovesStocksOnOwnedUsaWatchlistOnly() throws Exception {
  String created=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"USA adds\",\"market\":\"US\"}")).andReturn().getResponse().getContentAsString();
  long id=mapper.readTree(created).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/us/"+id+"/items?userId=1").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"aapl\",\"name\":\"Apple Inc. - Common Stock\"},{\"symbol\":\"BRK.B\"},{\"symbol\":\"ABR$D\",\"name\":\"Arbor Realty Trust Preferred\"}]}"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(3))
   .andExpect(jsonPath("$[0].symbol").value("AAPL")).andExpect(jsonPath("$[1].name").value("BRK.B"));
  org.junit.jupiter.api.Assertions.assertEquals(3,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa WHERE watchlist_id=?",Integer.class,id));
  mvc.perform(post("/api/v1/watchlists/us/"+id+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"MSFT\"},{\"symbol\":\"AAPL\"}]}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/watchlists/us/"+id+"/items?userId=2").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"MSFT\"}]}")).andExpect(status().isNotFound());
  String india=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"India not USA\",\"market\":\"India\"}")).andReturn().getResponse().getContentAsString();
  long indiaId=mapper.readTree(india).get("id").asLong();
  mvc.perform(post("/api/v1/watchlists/us/"+indiaId+"/items").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"MSFT\"}]}")).andExpect(status().isNotFound());
  org.junit.jupiter.api.Assertions.assertEquals(3,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa WHERE watchlist_id=?",Integer.class,id));
  mvc.perform(delete("/api/v1/watchlists/us/"+id+"/items/AAPL?userId=2")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/v1/watchlists/us/"+indiaId+"/items/AAPL")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/v1/watchlists/us/"+id+"/items/MSFT")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/v1/watchlists/us/"+id+"/items/aapl?userId=1")).andExpect(status().isNoContent());
  mvc.perform(delete("/api/v1/watchlists/us/{id}/items/{symbol}",id,"ABR$D")).andExpect(status().isNoContent());
  mvc.perform(delete("/api/v1/watchlists/us/{id}/items/{symbol}",id,"BRK.B")).andExpect(status().isNoContent());
  org.junit.jupiter.api.Assertions.assertEquals(0,
   jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa WHERE watchlist_id=?",Integer.class,id));
 }

    @Test
    void reimportAddsNewSymbolsToSameNamedWatchlistOfSameUser() throws Exception {
        importCsv("USA", 7, "Merge Tech,Merge Banks\nAAPL,JPM\nMSFT,\n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.watchlistsCreated").value(2))
                .andExpect(jsonPath("$.watchlistsUpdated").value(0))
                .andExpect(jsonPath("$.itemsCreated").value(3))
                .andExpect(jsonPath("$.itemsSkipped").value(0));
        long techId = jdbc.queryForObject(
                "SELECT id FROM watchlists_usa WHERE user_id=7 AND name='Merge Tech'", Long.class);

        // Same name in different case, one symbol already present, one new symbol, one new watchlist.
        importCsv("US", 7, "merge tech,Merge Energy\nmsft,XOM\nGOOG,\n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.watchlistsCreated").value(1))
                .andExpect(jsonPath("$.watchlistsUpdated").value(1))
                .andExpect(jsonPath("$.itemsCreated").value(2))
                .andExpect(jsonPath("$.itemsSkipped").value(1))
                .andExpect(jsonPath("$.watchlistNames[0]").value("Merge Tech"))
                .andExpect(jsonPath("$.watchlistNames[1]").value("Merge Energy"));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_usa WHERE user_id=7 AND LOWER(name)='merge tech'",
                        Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("AAPL", "MSFT", "GOOG"),
                jdbc.queryForList("SELECT symbol FROM watchlist_items_usa WHERE watchlist_id=? ORDER BY id",
                        String.class, techId));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa i JOIN watchlists_usa w "
                        + "ON i.watchlist_id=w.id WHERE w.user_id=7 AND w.name='Merge Banks'", Integer.class));
    }

    @Test
    void reimportCreatesSeparateWatchlistForDifferentUserOrMarket() throws Exception {
        importCsv("USA", 8, "Shared Name\nAAPL\n").andExpect(status().isCreated());
        importCsv("USA", 9, "Shared Name\nAAPL\n")
                .andExpect(jsonPath("$.watchlistsCreated").value(1))
                .andExpect(jsonPath("$.watchlistsUpdated").value(0));
        importCsv("India", 8, "Shared Name\nTCS.NS\n")
                .andExpect(jsonPath("$.watchlistsCreated").value(1))
                .andExpect(jsonPath("$.watchlistsUpdated").value(0));

        org.junit.jupiter.api.Assertions.assertEquals(2,
                jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_usa WHERE name='Shared Name'", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_india WHERE name='Shared Name'", Integer.class));
    }

    @Test
    void acceptsUsaAsAliasOfUsForCreateAndList() throws Exception {
        mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alias list\",\"market\":\"USA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("US"))
                .andExpect(jsonPath("$.currency").value("USD"));
        mvc.perform(get("/api/v1/watchlists").param("market", "USA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].market", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("US"))));
        importCsv("us", 1, "Lowercase Alias\nAAPL\n").andExpect(status().isCreated());
        importCsv("Europe", 1, "Bad Market\nAAPL\n").andExpect(status().isBadRequest());
    }

    @Test
    void createReturnsExistingWatchlistForSameUserNameAndMarket() throws Exception {
        long firstId = createWatchlistId("Long Term", "US", 11);
        org.junit.jupiter.api.Assertions.assertEquals(firstId, createWatchlistId("  long term ", "USA", 11));
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, createWatchlistId("Long Term", "US", 12));
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, createWatchlistId("Long Term", "India", 11));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM watchlists_usa WHERE user_id=11 AND LOWER(name)='long term'",
                        Integer.class));

        // A later import for the same user fills the same watchlist instead of creating another one.
        importCsv("USA", 11, "Long Term\nAAPL\n")
                .andExpect(jsonPath("$.watchlistsCreated").value(0))
                .andExpect(jsonPath("$.watchlistsUpdated").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM watchlist_items_usa WHERE watchlist_id=?", Integer.class,
                        firstId));
    }

    @Test
    void renameRejectsNameAlreadyUsedBySameUser() throws Exception {
        long keepId = createWatchlistId("Rename Keep", "US", 13);
        long otherId = createWatchlistId("Rename Other", "US", 13);
        mvc.perform(patch("/api/v1/watchlists/" + otherId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"rename keep\",\"market\":\"US\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
        // Changing only the letter case of its own name is allowed.
        mvc.perform(patch("/api/v1/watchlists/" + keepId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RENAME KEEP\",\"market\":\"US\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("RENAME KEEP"));
    }

    private long createWatchlistId(String name, String market, long userId) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"market\":\"" + market + "\",\"userId\":" + userId + "}";
        String response = mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }

    @Test
    void exportWritesNamesInFirstRowTickersBelowAndFormulasAsPlainText() throws Exception {
        long tech = createWatchlistId("Tech", "US", 21);
        long formula = createWatchlistId("=HYPERLINK(\\\"http://x\\\")", "US", 21);
        long banks = createWatchlistId("Banks", "US", 21);
        addUsItems(tech, 21, "AAPL", "MSFT", "NVDA");
        addUsItems(formula, 21, "IBM");
        addUsItems(banks, 21, "JPM", "BAC");

        var response = mvc.perform(get("/api/v1/watchlists/export").param("market", "USA").param("userId", "21"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        String csv = response.getContentAsString();

        // Row 1: one watchlist name per column. Rows 2+: each watchlist's tickers in its own column.
        org.junit.jupiter.api.Assertions.assertEquals("\uFEFF"
                + "Tech,\"'=HYPERLINK(\"\"http://x\"\")\",Banks\r\n"
                + "AAPL,IBM,JPM\r\n"
                + "MSFT,,BAC\r\n"
                + "NVDA,,\r\n", csv);

        // Importing the file for another user restores the original name without the apostrophe.
        MockMultipartFile file = new MockMultipartFile("file", "export.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/watchlists/import?market=US&userId=22").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.watchlistNames[1]").value("=HYPERLINK(\"http://x\")"));
    }

    private void addUsItems(long watchlistId, long userId, String... symbols) throws Exception {
        String items = java.util.Arrays.stream(symbols)
                .map(symbol -> "{\"symbol\":\"" + symbol + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post("/api/v1/watchlists/us/" + watchlistId + "/items").param("userId", String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[" + items + "]}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions importCsv(String market, long userId, String csv)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "watchlists.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/v1/watchlists/import")
                .file(file).param("market", market).param("userId", String.valueOf(userId)));
    }
 @Test void exportsWatchlistsInImportFormatAndRoundTrips() throws Exception {
  // User 7 has only these lists, so "all watchlists" output is predictable.
  long first=createList("Core, India",7), second=createList("Small \"caps\"",7);
  mvc.perform(post("/api/v1/watchlists/india/"+first+"/items?userId=7").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"RELIANCE\"},{\"symbol\":\"M&M\"},{\"symbol\":\"TCS\"}]}")).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/watchlists/india/"+second+"/items?userId=7").contentType(MediaType.APPLICATION_JSON)
    .content("{\"items\":[{\"symbol\":\"IRFC\"}]}")).andExpect(status().isCreated());

  var one=mvc.perform(get("/api/v1/watchlists/export?market=India&userId=7&watchlistId="+first))
    .andExpect(status().isOk())
    .andExpect(header().string("Content-Type",org.hamcrest.Matchers.startsWith("text/csv")))
    .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("india-core-india.csv")))
    .andReturn().getResponse();
  one.setCharacterEncoding("UTF-8");
  org.junit.jupiter.api.Assertions.assertEquals("\uFEFF\"Core, India\"\r\nRELIANCE\r\nM&M\r\nTCS\r\n",one.getContentAsString());

  var all=mvc.perform(get("/api/v1/watchlists/export?market=India&userId=7")).andExpect(status().isOk()).andReturn().getResponse();
  all.setCharacterEncoding("UTF-8");
  String csv=all.getContentAsString();
  org.junit.jupiter.api.Assertions.assertEquals(
   "\uFEFF\"Core, India\",\"Small \"\"caps\"\"\"\r\nRELIANCE,IRFC\r\nM&M,\r\nTCS,\r\n",csv);

  // Another user, the other market, and an unknown id are 404; a bad market is 400.
  mvc.perform(get("/api/v1/watchlists/export?market=India&userId=8&watchlistId="+first)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/watchlists/export?market=US&userId=7&watchlistId="+first)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/watchlists/export?market=Mars&userId=7")).andExpect(status().isBadRequest());

  // Importing the export for a new user recreates the same lists and symbols.
  MockMultipartFile file=new MockMultipartFile("file","export.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  mvc.perform(multipart("/api/v1/watchlists/import?market=India&userId=9").file(file))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.watchlistsCreated").value(2)).andExpect(jsonPath("$.itemsCreated").value(4));
  var reexport=mvc.perform(get("/api/v1/watchlists/export?market=India&userId=9")).andExpect(status().isOk()).andReturn().getResponse();
  reexport.setCharacterEncoding("UTF-8");
  org.junit.jupiter.api.Assertions.assertEquals(csv,reexport.getContentAsString());
 }
 private long createList(String name,long userId) throws Exception {
  String body=mapper.writeValueAsString(java.util.Map.of("name",name,"market","India","userId",userId));
  String created=mvc.perform(post("/api/v1/watchlists").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return mapper.readTree(created).get("id").asLong();
 }
}
