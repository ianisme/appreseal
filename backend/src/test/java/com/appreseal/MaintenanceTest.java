package com.appreseal;

import com.appreseal.domain.AppSettings;
import com.appreseal.domain.Task;
import com.appreseal.domain.TaskStatus;
import com.appreseal.repo.TaskLogRepository;
import com.appreseal.repo.TaskRepository;
import com.appreseal.service.MaintenanceService;
import com.appreseal.service.SettingsService;
import com.appreseal.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 保留期清理：产物、上传包、日志与孤儿文件。 */
class MaintenanceTest extends ApiTestSupport {

    @Autowired
    TaskService taskService;

    @Autowired
    MaintenanceService maintenance;

    @Autowired
    TaskRepository tasks;

    @Autowired
    TaskLogRepository logs;

    @Autowired
    SettingsService settingsService;

    @Test
    void cleanupPurgesExpiredArtifactsUploadsLogsAndOrphans() throws Exception {
        settingsService.update(new com.appreseal.web.dto.Dtos.UpdateSettingsRequest(
                null, null, null, null, null, null, null, 30, 14, 10, null, null));

        String token = userToken();

        /* 1) 成功任务 + 真实产物文件（40 天前完成） */
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.retention", "appName", "保留期测试"));
        String id = created.path("id").asText();
        Path source = Files.createTempFile("appreseal-retention-", ".ipa");
        Files.writeString(source, "retention-artifact");
        taskService.succeed(id, "1.0", 17L, "sha-retention", source.toString());
        Files.deleteIfExists(source);

        Task task = tasks.findById(id).orElseThrow();
        Path artifact = Path.of(task.artifactPath);
        assertTrue(Files.isRegularFile(artifact));
        task.finishedAt = Instant.now().minus(Duration.ofDays(40));
        tasks.save(task);

        /* 2) 失败任务 + 上传包（30 天前结束） */
        var failed = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.retention2", "appName", "上传保留期"));
        String uploadId = failed.path("id").asText();
        Path uploadDir = Path.of("./target/test-uploads").toAbsolutePath().normalize().resolve(uploadId);
        Files.createDirectories(uploadDir);
        Path input = Files.writeString(uploadDir.resolve("input.ipa"), "upload-bytes");
        Task uploadTask = tasks.findById(uploadId).orElseThrow();
        uploadTask.status = TaskStatus.FAILED;
        uploadTask.finishedAt = Instant.now().minus(Duration.ofDays(30));
        uploadTask.uploadPath = input.toString();
        tasks.save(uploadTask);
        taskService.log(uploadId, "INFO", "上传包保留期测试");

        /* 3) 孤儿文件：数据库不引用，且已超过 48 小时 */
        Path orphan = Path.of("./target/test-artifacts").toAbsolutePath().normalize().resolve("orphan-abc.ipa");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "orphan");
        Files.setLastModifiedTime(orphan, FileTime.from(Instant.now().minus(Duration.ofDays(5))));

        var report = maintenance.runNow();

        assertTrue(report.artifactsDeleted() >= 1, report.toString());
        assertTrue(report.uploadsDeleted() >= 1, report.toString());
        assertTrue(report.logsDeleted() >= 1, report.toString());
        assertTrue(report.orphanFilesDeleted() >= 1, report.toString());
        assertEquals(0, report.errors(), report.toString());
        assertTrue(report.artifactBytesFreed() > 0);

        assertFalse(Files.exists(artifact), "过期产物应已删除");
        assertFalse(Files.exists(uploadDir), "过期上传包目录应已删除");
        assertFalse(Files.exists(orphan), "孤儿文件应已删除");

        Task purged = tasks.findById(id).orElseThrow();
        assertTrue(purged.artifactPurged);
        assertNull(purged.artifactPath);
        Task uploadPurged = tasks.findById(uploadId).orElseThrow();
        assertNull(uploadPurged.uploadPath);

        assertTrue(logs.findByTaskIdOrderBySeqAsc(uploadId).isEmpty());
        assertTrue(logs.findByTaskIdOrderBySeqAsc(id).isEmpty());

        /* 清理后的产物不能再用旧链接或重新生成清单 */
        mvc.perform(post("/api/v1/tasks/" + id + "/download-link")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/tasks/" + id + "/artifact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        /* 存储状态可读，且不会把已删除的文件算成可用产物 */
        JsonNode status = getJson("/api/v1/admin/maintenance", adminToken());
        assertTrue(status.path("disk").path("totalBytes").asLong() > 0);
        assertTrue(status.path("disk").path("usedPercent").asInt() >= 0);
        assertEquals(30, status.path("artifactRetentionDays").asInt());
        assertTrue(status.path("lastRun").path("artifactsDeleted").asInt() >= 1);
    }
}
