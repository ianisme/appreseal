package com.appreseal.executor;

import com.appreseal.config.AppProperties;
import com.appreseal.domain.AppItem;
import com.appreseal.domain.Task;
import com.appreseal.domain.TaskStatus;
import com.appreseal.service.CatalogService;
import com.appreseal.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟执行器（默认）：不接触真机/设备，按固定节奏推进状态。
 * 用于本地开发与端到端演示；appName/bundleId 含 "fail" 时走失败分支，便于验证退款。
 */
@Component
@ConditionalOnProperty(name = "appreseal.executor.mode", havingValue = "mock", matchIfMissing = true)
public class MockDecryptJob implements DecryptJob {
    private static final Logger log = LoggerFactory.getLogger(MockDecryptJob.class);

    private final TaskService tasks;
    private final CatalogService catalog;
    private final AppProperties props;

    public MockDecryptJob(TaskService tasks, CatalogService catalog, AppProperties props) {
        this.tasks = tasks;
        this.catalog = catalog;
        this.props = props;
    }

    @Override
    public void run(String taskId) {
        Task task = tasks.requireTask(taskId);
        boolean willFail = (task.appName + " " + task.bundleId).toLowerCase().contains("fail");
        AppItem app = catalog.findApp(task.bundleId);

        try {
            if (!alive(taskId)) return;
            sleep(400);
            tasks.transition(taskId, TaskStatus.DOWNLOADING, 18, "INFO",
                    "分配 Apple ID 池账号 id-01（优先级 1）");
            sleep(props.getMock().getDownloadMs());
            if (!alive(taskId)) return;
            tasks.transition(taskId, TaskStatus.DOWNLOADING, 50, "OK", "下载完成，解析 SINF / SUPF");

            tasks.transition(taskId, TaskStatus.DECRYPTING, 56, "INFO",
                    "FairPlay 解密中，cryptsize=4096（1 页）");
            sleep(props.getMock().getDecryptMs());
            if (!alive(taskId)) return;
            tasks.transition(taskId, TaskStatus.DECRYPTING, 84, "OK",
                    "Mach-O 加密页已还原，cryptid 1 → 0");

            tasks.transition(taskId, TaskStatus.VERIFYING, 92, "INFO", "校验产物完整性…");
            sleep(props.getMock().getVerifyMs());
            if (!alive(taskId)) return;

            if (willFail) {
                tasks.fail(taskId, "该 App 不属于当前 Apple ID（凭证不匹配），已自动退款");
                return;
            }
            tasks.succeed(taskId, null, app != null ? app.sizeBytes : null, app != null ? app.sha256 : null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tasks.fail(taskId, "执行器被中断");
        } catch (Exception e) {
            log.warn("模拟任务失败 task={}", taskId, e);
            tasks.fail(taskId, "执行器异常：" + e.getMessage());
        }
    }

    private boolean alive(String taskId) {
        return tasks.isActive(taskId);
    }

    private void sleep(long ms) throws InterruptedException {
        Thread.sleep(Math.max(0, ms));
    }
}
