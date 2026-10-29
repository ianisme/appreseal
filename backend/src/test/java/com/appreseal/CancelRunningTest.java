package com.appreseal;

import com.appreseal.domain.Task;
import com.appreseal.domain.TaskStatus;
import com.appreseal.executor.JobControl;
import com.appreseal.repo.TaskRepository;
import com.appreseal.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 正在处理的任务可以取消退款，并且不会被随后的成功回调改回去。 */
class CancelRunningTest extends ApiTestSupport {

    @Autowired
    TaskRepository taskRepo;

    @Autowired
    TaskService taskService;

    @Autowired
    JobControl jobs;

    @Test
    void cancelDownloadingRefundsAndBlocksLaterSuccess() throws Exception {
        String token = userToken();
        var created = postJson("/api/v1/tasks", token, Map.of(
                "source", "STORE", "bundleId", "com.example.cancel", "appName", "取消测试"));
        String id = created.path("id").asText();
        assertEquals(0, created.path("priceCents").asLong());

        Task task = taskRepo.findById(id).orElseThrow();
        task.status = TaskStatus.DECRYPTING;
        task.stage = "解密中";
        task.progress = 60;
        taskRepo.save(task);

        var canceled = postJson("/api/v1/tasks/" + id + "/cancel", token, null);
        assertEquals("CANCELED", canceled.path("status").asText());
        assertEquals(0, canceled.path("refundedCents").asLong());

        taskService.succeed(id, "9.9", 12L, "sha-cancel", null);
        taskService.fail(id, "不应覆盖取消");
        Task after = taskRepo.findById(id).orElseThrow();
        assertEquals(TaskStatus.CANCELED, after.status);
        assertEquals(0, after.refundedCents);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/tasks/" + id + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stopKillsTheProcessTree() throws Exception {
        Process process = new ProcessBuilder("sleep", "30").start();
        Process child = new ProcessBuilder("sleep", "30").start();
        assertTrue(process.isAlive());
        jobs.begin("t_cancel_proc");
        jobs.watch("t_cancel_proc", process);
        jobs.watch("t_cancel_proc", child);
        jobs.stop("t_cancel_proc");
        assertFalse(process.isAlive(), "主进程应已停止");
        assertFalse(child.isAlive(), "一并登记的子进程应已停止");
        jobs.end("t_cancel_proc");
    }
}
