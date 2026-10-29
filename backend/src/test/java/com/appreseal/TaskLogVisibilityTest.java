package com.appreseal;

import com.appreseal.common.LogSanitizer;
import com.appreseal.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskLogVisibilityTest extends ApiTestSupport {

    @Autowired
    TaskService taskService;

    @Test
    void normalUserOnlySeesSanitizedLogs() throws Exception {
        String token = userToken();
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.logs", "appName", "日志测试"));
        String id = created.path("id").asText();

        taskService.log(id, "INFO", "公开：正在处理");
        taskService.log(id, "INFO", "兜底 /opt/fairplay/fairplay-tools/appget.sh 由 us@example.com 执行");
        taskService.logInternal(id, "INFO", "内部：/app/data/work/" + id
                + "/out/app.ipa 由 apple@example.com 通过 appget.sh 处理");

        String userText = messages(getJson("/api/v1/tasks/" + id, token));
        assertTrue(userText.contains("公开：正在处理"), userText);
        assertFalse(userText.contains("内部："), userText);
        assertFalse(userText.contains("/app/data"), userText);
        assertFalse(userText.contains("/opt/fairplay"), userText);
        assertFalse(userText.contains("apple@example.com"), userText);
        assertFalse(userText.contains("us@example.com"), userText);
        assertFalse(userText.contains("appget.sh"), userText);

        String adminText = messages(getJson("/api/v1/tasks/" + id, adminToken()));
        assertTrue(adminText.contains("内部：/app/data/work/" + id), adminText);
        assertTrue(adminText.contains("apple@example.com"), adminText);
        assertTrue(adminText.contains("appget.sh"), adminText);

        postJson("/api/v1/tasks/" + id + "/cancel", token, null);
    }

    @Test
    void errorFieldIsSanitizedForUsers() throws Exception {
        String token = userToken();
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.logs2", "appName", "错误脱敏"));
        String id = created.path("id").asText();

        taskService.fail(id, "执行 /app/data/work/x 失败，账号 apple@example.com，脚本 appget.sh");

        JsonNode userTask = getJson("/api/v1/tasks/" + id, token);
        String error = userTask.path("error").asText();
        assertTrue(error.contains("执行"), error);
        assertFalse(error.contains("/app/data"), error);
        assertFalse(error.contains("apple@example.com"), error);
        assertFalse(error.contains("appget.sh"), error);

        JsonNode adminTask = getJson("/api/v1/tasks/" + id, adminToken());
        assertTrue(adminTask.path("error").asText().contains("/app/data/work/x"));
        assertEquals("FAILED", userTask.path("status").asText());
    }

    @Test
    void sanitizerHandlesPathsEmailsAndToolNames() {
        assertEquals("（内部路径） 完成", LogSanitizer.forUser("/app/data/work/t_1/out/a.ipa 完成").trim());
        assertEquals("由 已绑定账号 执行", LogSanitizer.forUser("由 apple@example.com 执行").trim());
        assertEquals("调用 服务组件 完成", LogSanitizer.forUser("调用 appget.sh 完成").trim());
        assertEquals("凭证与这个包不匹配",
                LogSanitizer.forUser("credential does not decode this SINF priv record"));
        assertFalse(LogSanitizer.forUser("/opt/fairplay/fairplay-decrypt/decrypt.py")
                .contains("/opt"));
    }

    private static String messages(JsonNode task) {
        List<String> out = new ArrayList<>();
        for (JsonNode log : task.path("logs")) {
            out.add(log.path("message").asText());
        }
        return String.join("\n", out);
    }
}
