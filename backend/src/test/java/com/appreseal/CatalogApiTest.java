package com.appreseal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogApiTest extends ApiTestSupport {

    @Test
    void openMarketsFollowTheAppleIdPool() throws Exception {
        var markets = getJson("/api/v1/store/markets", userToken());
        var codes = new java.util.ArrayList<String>();
        markets.forEach(node -> codes.add(node.path("code").asText()));
        assertEquals(java.util.List.of("cn", "us", "jp"), codes);
    }

    @Test
    void publicAppListOnlyContainsOnSale() throws Exception {
        mvc.perform(get("/api/v1/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[0].bundleId").isNotEmpty());
    }

    @Test
    void searchAndCategoryFilters() throws Exception {
        mvc.perform(get("/api/v1/apps").param("q", "青笺"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bundleId").value("com.example.qingjian"));

        var byCategory = getJson("/api/v1/apps?category=社交", userToken());
        assertTrue(byCategory.size() >= 4);
    }

    @Test
    void siteIsPublicAndSucceededTaskHasArtifact() throws Exception {
        mvc.perform(get("/api/v1/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance").value(false))
                .andExpect(jsonPath("$.announcement").isNotEmpty());

        var result = mvc.perform(get("/api/v1/tasks/t_seed_1/artifact")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("com.example.qingjian"));
    }

    @Test
    void appDetailAndPackagesArePublic() throws Exception {
        mvc.perform(get("/api/v1/apps/com.example.qingjian"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("青笺"))
                .andExpect(jsonPath("$.priceDecryptCents").value(0));

        mvc.perform(get("/api/v1/packages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("团队版不提供钱包和充值"));
    }

    @Test
    void downloadRequiresLoginAndDoesNotCharge() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/apps/com.example.noteboard/download"))
                .andExpect(status().isUnauthorized());

        String token = userToken();
        var owned = postJson("/api/v1/apps/com.example.stargaze/download", token, null);
        assertEquals(0, owned.path("balanceCents").asLong());

        var added = postJson("/api/v1/apps/com.example.melodybox/download", token, null);
        assertEquals(0, added.path("balanceCents").asLong());
        assertTrue(added.path("libraryId").asText().length() > 0);
    }
}
