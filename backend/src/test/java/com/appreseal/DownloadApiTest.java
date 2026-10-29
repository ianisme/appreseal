package com.appreseal;

import com.appreseal.domain.Task;
import com.appreseal.repo.TaskRepository;
import com.appreseal.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 短时签名下载 + Range 断点续传。 */
class DownloadApiTest extends ApiTestSupport {

    @Autowired
    TaskService taskService;

    @Autowired
    TaskRepository tasks;

    @Test
    void signedLinkSupportsRangeAndNeedsNoLogin() throws Exception {
        byte[] content = new byte[4096];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        String id = succeededTask(content);

        String token = userToken();
        var link = postJson("/api/v1/tasks/" + id + "/download-link", token, null);
        String url = link.path("url").asText();
        assertTrue(url.startsWith("/api/v1/dl/"), url);
        assertTrue(link.path("expiresAt").asText().length() > 10);

        // 不带 Authorization，直接下载整包
        var full = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        assertArrayEquals(content, full.getResponse().getContentAsByteArray());
        assertEquals("bytes", full.getResponse().getHeader("Accept-Ranges"));
        assertTrue(full.getResponse().getHeader("ETag").startsWith("\""));
        String etag = full.getResponse().getHeader("ETag");

        // 下载器常先 HEAD 拿大小
        var head = mvc.perform(head(url)).andExpect(status().isOk()).andReturn();
        assertEquals(content.length, head.getResponse().getContentLength());
        assertEquals("bytes", head.getResponse().getHeader("Accept-Ranges"));

        // 只取前 100 字节
        var first = mvc.perform(get(url).header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent()).andReturn();
        assertEquals(100, first.getResponse().getContentAsByteArray().length);
        assertEquals("bytes 0-99/" + content.length, first.getResponse().getHeader("Content-Range"));
        assertArrayEquals(java.util.Arrays.copyOfRange(content, 0, 100),
                first.getResponse().getContentAsByteArray());

        // 断点续传：从 4090 字节之后继续
        var tail = mvc.perform(get(url).header("Range", "bytes=4090-"))
                .andExpect(status().isPartialContent()).andReturn();
        assertArrayEquals(java.util.Arrays.copyOfRange(content, 4090, content.length),
                tail.getResponse().getContentAsByteArray());

        // 条件请求命中 ETag
        mvc.perform(get(url).header("If-None-Match", etag)).andExpect(status().isNotModified());

        // 越界 Range
        mvc.perform(get(url).header("Range", "bytes=" + (content.length + 10) + "-"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    @Test
    void tamperedOrMissingLinkIsRejected() throws Exception {
        byte[] content = "hello-appreseal".getBytes(StandardCharsets.UTF_8);
        String id = succeededTask(content);

        String url = postJson("/api/v1/tasks/" + id + "/download-link", userToken(), null)
                .path("url").asText();
        String tampered = url.substring(0, url.length() - 1) + (url.endsWith("A") ? "B" : "A");
        mvc.perform(get(tampered)).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/dl/not-a-token")).andExpect(status().isBadRequest());
    }

    @Test
    void expiredTaskCannotIssueNewLink() throws Exception {
        String token = userToken();
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.nolink", "appName", "未完成"));
        String id = created.path("id").asText();
        mvc.perform(post("/api/v1/tasks/" + id + "/download-link")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private String succeededTask(byte[] content) throws Exception {
        String token = userToken();
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.download", "appName", "下载测试"));
        String id = created.path("id").asText();
        Path tmp = Files.createTempFile("appreseal-dl-", ".ipa");
        Files.write(tmp, content);
        taskService.succeed(id, "1.0.0", (long) content.length, "sha-test", tmp.toString());
        Files.deleteIfExists(tmp);

        Task task = tasks.findById(id).orElseThrow();
        assertTrue(task.artifactPath != null && Files.isRegularFile(Path.of(task.artifactPath)), task.artifactPath);
        return id;
    }
}
