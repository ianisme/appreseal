package com.appreseal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskWalletApiTest extends ApiTestSupport {

    @Test
    void createTaskDoesNotChargeAndCancelKeepsItFree() throws Exception {
        String token = userToken();

        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE",
                "bundleId", "com.example.qingjian",
                "appName", "青笺",
                "version", "8.0.54"));
        String id = created.path("id").asText();
        assertEquals("QUEUED", created.path("status").asText());
        assertEquals(0, created.path("priceCents").asLong());
        assertTrue(created.path("logs").size() > 0);

        var canceled = postJson("/api/v1/tasks/" + id + "/cancel", token, null);
        assertEquals("CANCELED", canceled.path("status").asText());
        assertEquals(0, canceled.path("refundedCents").asLong());

        var detail = getJson("/api/v1/tasks/" + id, token);
        assertEquals("CANCELED", detail.path("status").asText());
    }

    @Test
    void retryDoesNotCharge() throws Exception {
        String token = userToken();

        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.noteboard"));
        String id = created.path("id").asText();
        postJson("/api/v1/tasks/" + id + "/cancel", token, null);

        var retried = postJson("/api/v1/tasks/" + id + "/retry", token, null);
        assertEquals("QUEUED", retried.path("status").asText());
        assertEquals(0, retried.path("priceCents").asLong());

        postJson("/api/v1/tasks/" + id + "/cancel", token, null);
    }

    @Test
    void rechargeIsRejected() throws Exception {
        String token = userToken();
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/recharges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("packageId", "pkg_100", "method", "ALIPAY"))))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .contains("不提供钱包"));
    }

    @Test
    void zeroBalanceUserCanStillCreateTask() throws Exception {
        String token = login("chen@example.com", "demo1234");
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.qingjian"));
        assertEquals("QUEUED", created.path("status").asText());
        assertEquals(0, created.path("priceCents").asLong());
        postJson("/api/v1/tasks/" + created.path("id").asText() + "/cancel", token, null);
    }
}
