package com.appreseal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiTest extends ApiTestSupport {

    @Test
    void normalUserCannotSeeAdminOverview() throws Exception {
        mvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("没有管理员权限"));
    }

    @Test
    void adminOverviewShape() throws Exception {
        var overview = getJson("/api/v1/admin/overview", adminToken());
        assertTrue(overview.path("series").size() == 14);
        assertTrue(overview.path("distribution").size() == 4);
        assertEquals(2, overview.path("queue").path("concurrency").asInt());
        assertTrue(overview.path("successRate").asDouble() > 0);
    }

    @Test
    void adminUsersIncludeBalanceAndTaskCount() throws Exception {
        var users = getJson("/api/v1/admin/users?q=demo", adminToken());
        assertEquals(1, users.path("items").size());
        assertEquals("demo@appreseal.com", users.path("items").get(0).path("email").asText());
        assertTrue(users.path("items").get(0).path("tasks").asLong() >= 5);
    }

    @Test
    void balanceAdjustmentIsGone() throws Exception {
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/users/u_demo/balance")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("deltaCents", 1000, "remark", "不应再调账"))))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .contains("不提供钱包"));
    }

    @Test
    void markingStorefrontMatchesAppStoreSearch() throws Exception {
        String admin = adminToken();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/apple-accounts/aa_1")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"storefront\":\"us\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storefront").value("us"));

        var search = getJson("/api/v1/store/search?q=&country=us", userToken());
        assertEquals("us", search.path("storefront").asText());
        assertTrue(search.path("storefrontName").asText().length() > 0);
        assertTrue(search.path("matchedAccounts").asInt() >= 2);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/apple-accounts/aa_1")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"storefront\":\"cn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storefront").value("cn"));
    }

    @Test
    void bindCredentialRequiresSixteenBytes() throws Exception {
        var bad = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/admin/apple-accounts/credential")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "credential.bin", "application/octet-stream", new byte[]{1, 2, 3}))
                        .param("email", "id-01@appreseal.com")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        assertEquals(400, bad.getResponse().getStatus());

        byte[] credential = new byte[16];
        java.util.Arrays.fill(credential, (byte) 7);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/admin/apple-accounts/credential")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "credential.bin", "application/octet-stream", credential))
                        .param("email", "id-01@appreseal.com")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("id-01@appreseal.com"))
                .andExpect(jsonPath("$.credentialOk").value(true));
    }

    @Test
    void appleLoginWithoutIpatoolIsRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/apple-accounts/login")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "email", "id-01@appreseal.com", "password", "not-stored"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前环境没有 ipatool，无法生成文件密钥"));
    }

    @Test
    void cannotBanSelf() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/users/u_admin")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "BANNED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能停用当前登录的管理员"));
    }

    @Test
    void settingsArePersistedWithoutPricing() throws Exception {
        String token = adminToken();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/pricing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("团队版不提供订单和定价"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("maintenance", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance").value(true));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("maintenance", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance").value(false));
    }
}
