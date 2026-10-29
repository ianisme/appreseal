package com.appreseal.executor;

import com.appreseal.domain.Task;
import com.appreseal.service.SettingsService;
import com.appreseal.service.TaskService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务调度器：定时扫 QUEUED 队列，按「设置里的并发数」把任务丢进线程池。
 *
 * <p>并发上限由 {@code app_settings.concurrency} 控制；每个任务执行期间在
 * {@link #inflight} 中占位，避免下一轮轮询重复提交。服务启动时会把上次
 * 中断的 DOWNLOADING/DECRYPTING/VERIFYING 任务重新排队。
 */
@Component
@ConditionalOnProperty(name = "appreseal.executor.enabled", havingValue = "true", matchIfMissing = true)
public class TaskDispatcher {
    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);
    private static final int MAX_WORKERS = 8;

    private final TaskService tasks;
    private final SettingsService settings;
    private final DecryptJob job;
    private final JobControl jobs;
    private final ExecutorService pool;
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();
    /** 种子数据写完之前不扫队列，避免和 DataSeeder 抢着插入 settings。 */
    private volatile boolean open;

    public TaskDispatcher(TaskService tasks, SettingsService settings, DecryptJob job, JobControl jobs) {
        this.tasks = tasks;
        this.settings = settings;
        this.job = job;
        this.jobs = jobs;
        AtomicInteger seq = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(MAX_WORKERS, r -> {
            Thread t = new Thread(r, "appreseal-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Scheduled(fixedDelayString = "${appreseal.executor.poll-interval-ms:1000}")
    public synchronized void poll() {
        if (!open) {
            return;
        }
        try {
            dispatch();
        } catch (Exception e) {
            log.warn("任务调度轮询失败：{}", e.getMessage(), e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onReady() {
        int requeued = tasks.requeueInterrupted();
        if (requeued > 0) {
            log.warn("服务重启：{} 个执行中的任务已重新排队", requeued);
        }
        open = true;
        dispatch();
    }

    private void dispatch() {
        int concurrency = Math.max(1, Math.min(MAX_WORKERS, settings.get().concurrency));
        long running = tasks.runningCount();
        if (running >= concurrency) {
            return;
        }
        for (Task t : tasks.queued()) {
            if (running >= concurrency) {
                break;
            }
            if (!inflight.add(t.id)) {
                continue;
            }
            running++;
            pool.submit(() -> runOne(t.id));
        }
    }

    private void runOne(String taskId) {
        jobs.begin(taskId);
        try {
            job.run(taskId);
        } catch (Throwable e) {
            log.error("任务执行异常 task={}", taskId, e);
        } finally {
            jobs.end(taskId);
            inflight.remove(taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
