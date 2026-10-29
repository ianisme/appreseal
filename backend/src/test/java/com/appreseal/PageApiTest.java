package com.appreseal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务、用户、审计日志按页返回，筛选计数不跟着当前页走。 */
class PageApiTest extends ApiTestSupport {

    @Test
    void taskPagesDoNotOverlapAndKeepCounts() throws Exception {
        String token = userToken();
        var first = getJson("/api/v1/tasks?page=1&size=2", token);
        var second = getJson("/api/v1/tasks?page=2&size=2", token);

        assertEquals(2, first.path("items").size());
        assertEquals(1, first.path("page").asInt());
        assertTrue(first.path("total").asLong() >= 4);
        assertTrue(first.path("pages").asInt() >= 2);
        assertTrue(first.path("succeeded").asLong() >= 1);
        assertNotSameItem(first.path("items").get(0).path("id").asText(), second.path("items").get(0).path("id").asText());

        var running = getJson("/api/v1/tasks?status=SUCCEEDED&page=1&size=1", token);
        assertEquals(1, running.path("items").size());
        assertEquals("SUCCEEDED", running.path("items").get(0).path("status").asText());
        assertEquals(first.path("succeeded").asLong(), running.path("total").asLong());
    }

    @Test
    void adminUsersAndLogsArePaged() throws Exception {
        String admin = adminToken();
        var users = getJson("/api/v1/admin/users?page=1&size=2", admin);
        assertEquals(2, users.path("items").size());
        assertTrue(users.path("registered").asLong() >= users.path("total").asLong());
        assertTrue(users.path("registered").asLong() > 2);

        var logs = getJson("/api/v1/admin/logs?page=1&size=1", admin);
        assertEquals(1, logs.path("items").size());
        assertTrue(logs.path("totalAll").asLong() >= logs.path("total").asLong());
        assertTrue(logs.path("pages").asInt() >= 1);
    }

    private static void assertNotSameItem(String a, String b) {
        assertTrue(!a.equals(b), a);
    }
}
