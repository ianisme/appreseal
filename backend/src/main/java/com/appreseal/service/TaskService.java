package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.common.Pages;
import com.appreseal.config.AppProperties;
import com.appreseal.executor.JobControl;
import com.appreseal.domain.*;
import com.appreseal.repo.*;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class TaskService {
    private static final List<TaskStatus> RUNNING =
            List.of(TaskStatus.QUEUED, TaskStatus.DOWNLOADING, TaskStatus.DECRYPTING, TaskStatus.VERIFYING);

    private final TaskRepository tasks;
    private final TaskLogRepository logs;
    private final UserRepository users;
    private final LibraryRepository library;
    private final SettingsService settings;
    private final CatalogService catalog;
    private final ArtifactService artifacts;
    private final AppProperties props;
    private final JobControl jobs;

    public TaskService(TaskRepository tasks, TaskLogRepository logs, UserRepository users, LibraryRepository library,
                       SettingsService settings, CatalogService catalog, ArtifactService artifacts,
                       AppProperties props, JobControl jobs) {
        this.tasks = tasks;
        this.logs = logs;
        this.users = users;
        this.library = library;
        this.settings = settings;
        this.catalog = catalog;
        this.artifacts = artifacts;
        this.props = props;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Dtos.TaskPageDto<Dtos.TaskDto> list(String userId, String status, Integer page, Integer size) {
        List<Task> all = tasks.findByUserIdOrderByCreatedAtDesc(userId);
        return page(all, status, null, page, size, false);
    }

    @Transactional(readOnly = true)
    public Dtos.TaskPageDto<Dtos.AdminTaskDto> adminPage(String status, String q, Integer page, Integer size) {
        List<Task> all = tasks.findAllByOrderByCreatedAtDesc();
        return page(all, status, q, page, size, true);
    }

    private <T> Dtos.TaskPageDto<T> page(List<Task> all, String status, String q, Integer page, Integer size,
                                         boolean admin) {
        int p = Pages.page(page);
        int s = Pages.size(size);
        long running = all.stream().filter(t -> t.status.isRunning()).count();
        long succeeded = all.stream().filter(t -> t.status == TaskStatus.SUCCEEDED).count();
        long failed = all.stream().filter(t -> t.status == TaskStatus.FAILED).count();
        long canceled = all.stream().filter(t -> t.status == TaskStatus.CANCELED).count();
        long revenue = 0;
        String query = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<Task> filtered = all.stream()
                .filter(t -> matches(t, status) && matchesQuery(t, query))
                .toList();
        List<T> items = Pages.slice(filtered, p, s).stream().map(t -> this.<T>toPageItem(t, admin)).toList();
        return new Dtos.TaskPageDto<>(items, p, s, filtered.size(), Pages.pageCount(filtered.size(), s),
                running, succeeded, failed, canceled, revenue);
    }

    @SuppressWarnings("unchecked")
    private <T> T toPageItem(Task task, boolean admin) {
        if (admin) {
            return (T) Dtos.AdminTaskDto.of(task, logs.findByTaskIdOrderBySeqAsc(task.id),
                    users.findById(task.userId).map(u -> u.email).orElse("—"));
        }
        return (T) toDto(task, false);
    }

    private boolean matchesQuery(Task task, String q) {
        if (q.isEmpty()) {
            return true;
        }
        String email = users.findById(task.userId).map(u -> u.email).orElse("");
        return contains(task.appName, q) || contains(task.bundleId, q) || contains(task.id, q)
                || contains(email, q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(q);
    }

    @Transactional(readOnly = true)
    public Dtos.TaskDto get(String id, String userId, boolean admin) {
        Task t = tasks.findById(id).orElseThrow(() -> ApiException.notFound("任务不存在"));
        if (!admin && !t.userId.equals(userId)) {
            throw ApiException.forbidden("无权查看该任务");
        }
        return toDto(t, admin);
    }

    @Transactional(readOnly = true)
    public Task requireTask(String id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("任务不存在"));
    }

    @Transactional
    public Dtos.TaskDto create(String userId, Dtos.CreateTaskRequest req) {
        AppSettings s = settings.get();
        if (s.maintenance) {
            throw ApiException.badRequest("系统维护中，暂时无法提交任务");
        }
        String bundleId = req.bundleId().trim();
        AppItem app = catalog.findApp(bundleId);
        String appName = req.appName() == null || req.appName().isBlank()
                ? (app != null ? app.name : bundleId)
                : req.appName().trim();
        String version = req.version() == null || req.version().isBlank()
                ? (app != null ? app.version : "latest")
                : req.version().trim();
        Task t = new Task();
        t.id = Ids.of("t");
        t.userId = userId;
        t.appName = appName;
        t.bundleId = bundleId;
        t.version = version;
        t.storefront = req.source() == TaskSource.STORE ? Storefronts.normalize(req.storefront()) : null;
        t.source = req.source();
        t.status = TaskStatus.QUEUED;
        t.progress = 6;
        t.stage = TaskStatus.QUEUED.stage();
        t.priceCents = 0;
        t.attempt = 1;
        t.simulated = true;

        tasks.save(t);
        appendLogInternal(t.id, "INFO", "任务已创建，进入队列");
        return toDto(t, false);
    }

    /**
     * 上传 IPA 建单：文件已经落到 uploads/staging，由这里移动到任务目录。
     * 保存失败会删掉刚移动的文件，避免留下无主包。
     */
    @Transactional
    public Dtos.TaskDto createUpload(String userId, Path staged, IpaInspector.Info info, String originalName) {
        AppSettings s = settings.get();
        if (s.maintenance) {
            throw ApiException.badRequest("系统维护中，暂时无法提交任务");
        }
        String bundleId = info.bundleId();
        String appName = truncate(info.name(), 120);
        if (appName.isBlank()) {
            appName = bundleId;
        }
        String version = truncate(info.version(), 32);
        if (version.isBlank()) {
            version = "latest";
        }
        Task t = new Task();
        t.id = Ids.of("t");
        t.userId = userId;
        t.appName = appName;
        t.bundleId = bundleId;
        t.version = version;
        t.storefront = null;
        t.source = TaskSource.UPLOAD;
        t.status = TaskStatus.QUEUED;
        t.progress = 6;
        t.stage = TaskStatus.QUEUED.stage();
        t.priceCents = 0;
        t.attempt = 1;
        t.simulated = true;

        Path target = UploadFiles.taskInput(props, t.id);
        try {
            Files.createDirectories(target.getParent());
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw ApiException.badRequest("保存上传文件失败");
        }
        t.uploadPath = target.toString();
        try {
            tasks.save(t);
        } catch (RuntimeException e) {
            UploadFiles.deleteTaskDir(target);
            throw e;
        }
        appendLogInternal(t.id, "INFO", "任务已创建，进入队列");
        appendLogInternal(t.id, "INFO", "已收到上传包：" + appName + " · " + bundleId + " " + version
                + " · " + humanSize(info.sizeBytes()) + " · SHA256 " + shortHash(info.sha256()));
        if (originalName != null && !originalName.isBlank()) {
            appendLogInternal(t.id, "INFO", "原始文件名：" + truncate(originalName, 120));
        }
        return toDto(t, false);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String shortHash(String sha) {
        return sha == null || sha.length() < 16 ? "—" : sha.substring(0, 16) + "…";
    }

    private static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024 / 1024);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024);
    }

    /** 排队中或正在处理的任务都可以取消。状态提交后，再停掉这次执行拉起的进程。 */
    @Transactional
    public Dtos.TaskDto cancel(String id, String userId, boolean admin) {
        Task t = tasks.lockById(id).orElseThrow(() -> ApiException.notFound("任务不存在"));
        if (!admin && !t.userId.equals(userId)) {
            throw ApiException.forbidden("无权操作");
        }
        if (!t.status.isRunning()) {
            throw ApiException.badRequest("任务已结束，不能取消");
        }
        boolean live = t.status != TaskStatus.QUEUED;
        t.status = TaskStatus.CANCELED;
        t.stage = TaskStatus.CANCELED.stage();
        t.progress = 100;
        t.finishedAt = Instant.now();
        tasks.save(t);
        appendLogInternal(t.id, "WARN", live ? "任务已取消，正在停止处理" : "任务已取消");
        stopAfterCommit(id);
        return toDto(t, admin);
    }

    private void stopAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            jobs.stop(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobs.stop(taskId);
            }
        });
    }

    /** 失败或已取消的任务可以重新入队，不再计费。 */
    @Transactional
    public Dtos.TaskDto retry(String id, String userId, boolean admin) {
        Task t = tasks.findById(id).orElseThrow(() -> ApiException.notFound("任务不存在"));
        if (!admin && !t.userId.equals(userId)) {
            throw ApiException.forbidden("无权操作");
        }
        if (t.status != TaskStatus.FAILED && t.status != TaskStatus.CANCELED) {
            throw ApiException.badRequest("只有失败或已取消的任务可以重试");
        }
        int nextAttempt = t.attempt + 1;
        t.attempt = nextAttempt;
        t.status = TaskStatus.QUEUED;
        t.progress = 6;
        t.stage = TaskStatus.QUEUED.stage();
        t.error = null;
        t.finishedAt = null;
        t.refundedCents = 0;
        t.createdAt = Instant.now();
        t.simulated = true;
        t.sizeBytes = null;
        t.sha256 = null;
        t.artifactPath = null;
        tasks.save(t);
        logs.deleteByTaskId(t.id);
        appendLogInternal(t.id, "INFO", admin ? "管理员重新入队" : "任务重新入队");
        return toDto(t, admin);
    }

    /* ------------------------- 执行器调用（内部） ------------------------- */

    @Transactional
    public boolean isActive(String taskId) {
        return tasks.findById(taskId)
                .map(t -> !t.status.isFinished())
                .orElse(false);
    }

    /** 真实执行器接单后调用，产物不再是模拟清单。 */
    @Transactional
    public void markLive(String taskId) {
        tasks.lockById(taskId).ifPresent(t -> {
            if (t.status.isFinished()) {
                return;
            }
            t.simulated = false;
            tasks.save(t);
        });
    }

    @Transactional
    public void transition(String taskId, TaskStatus status, int progress, String level, String message) {
        Task t = tasks.lockById(taskId).orElse(null);
        if (t == null || t.status.isFinished()) {
            return;
        }
        t.status = status;
        t.progress = Math.max(0, Math.min(100, progress));
        t.stage = status.stage();
        tasks.save(t);
        if (message != null && !message.isBlank()) {
            appendLogInternal(taskId, level == null ? "INFO" : level, message);
        }
    }

    @Transactional
    public void log(String taskId, String level, String message) {
        appendLogInternal(taskId, level, message, false);
    }

    /** 仅管理员可见的详细日志：路径、账号、脚本名等。 */
    @Transactional
    public void logInternal(String taskId, String level, String message) {
        appendLogInternal(taskId, level, message, true);
    }

    @Transactional
    public void succeed(String taskId, String version, Long sizeBytes, String sha256, String artifactPath) {
        Task t = tasks.lockById(taskId).orElse(null);
        if (t == null || t.status.isFinished()) {
            return;
        }
        AppItem app = catalog.findApp(t.bundleId);
        if (version != null && !version.isBlank() && !"latest".equalsIgnoreCase(version)) {
            t.version = version;
        }
        t.status = TaskStatus.SUCCEEDED;
        t.progress = 100;
        t.stage = TaskStatus.SUCCEEDED.stage();
        t.finishedAt = Instant.now();
        t.sizeBytes = sizeBytes != null ? sizeBytes : (app != null ? app.sizeBytes : 140L * 1024 * 1024);
        t.sha256 = sha256 != null ? sha256 : (app != null ? app.sha256 : Ids.of("sha"));
        t.artifactPath = artifacts.adopt(t, artifactPath);
        tasks.save(t);
        appendLogInternal(taskId, "OK", "脱壳完成，产物已入库");

        upsertLibrary(t, app, Instant.now());
    }

    private void upsertLibrary(Task t, AppItem app, Instant now) {
        LibraryItem item = library.findFirstByUserIdAndBundleIdOrderByAcquiredAtDesc(t.userId, t.bundleId)
                .orElseGet(() -> {
                    LibraryItem l = new LibraryItem();
                    l.id = Ids.of("lib");
                    l.userId = t.userId;
                    l.bundleId = t.bundleId;
                    return l;
                });
        if (app != null) {
            item.appId = app.id;
        }
        item.name = t.appName;
        item.version = t.version;
        item.source = LibrarySource.DECRYPT;
        item.acquiredAt = now;
        item.freeUntil = now.plus(Duration.ofDays(3650));
        item.taskId = t.id;
        item.artifactPath = t.artifactPath;
        library.save(item);
    }

    @Transactional
    public void fail(String taskId, String error) {
        Task t = tasks.lockById(taskId).orElse(null);
        if (t == null || t.status.isFinished()) {
            return;
        }
        t.status = TaskStatus.FAILED;
        t.progress = 100;
        t.stage = TaskStatus.FAILED.stage();
        t.error = error;
        t.finishedAt = Instant.now();
        tasks.save(t);
        appendLogInternal(t.id, "ERROR", "✘ " + error);
    }

    @Transactional(readOnly = true)
    public List<Task> queued() {
        return tasks.findByStatusOrderByCreatedAtAsc(TaskStatus.QUEUED);
    }

    @Transactional(readOnly = true)
    public long runningCount() {
        return tasks.countByStatusIn(List.of(TaskStatus.DOWNLOADING, TaskStatus.DECRYPTING, TaskStatus.VERIFYING));
    }

    @Transactional(readOnly = true)
    public long queuedCount() {
        return tasks.countByStatus(TaskStatus.QUEUED);
    }

    /** 服务重启后把「执行中」的任务重新排队（执行器日志会保留）。 */
    @Transactional
    public int requeueInterrupted() {
        List<Task> stale = tasks.findByStatusInOrderByCreatedAtAsc(
                List.of(TaskStatus.DOWNLOADING, TaskStatus.DECRYPTING, TaskStatus.VERIFYING));
        for (Task t : stale) {
            t.status = TaskStatus.QUEUED;
            t.progress = 6;
            t.stage = TaskStatus.QUEUED.stage();
            t.simulated = true;
            tasks.save(t);
            appendLogInternal(t.id, "WARN", "服务重启，任务重新排队");
        }
        return stale.size();
    }

    /* ------------------------------- helpers ------------------------------- */

    private Dtos.TaskDto toDto(Task t, boolean admin) {
        return Dtos.TaskDto.from(t, logs.findByTaskIdOrderBySeqAsc(t.id), admin);
    }

    private void appendLogInternal(String taskId, String level, String message) {
        appendLogInternal(taskId, level, message, false);
    }

    private void appendLogInternal(String taskId, String level, String message, boolean internal) {
        int seq = logs.maxSeq(taskId) + 1;
        logs.save(new TaskLog(taskId, seq, level, message, internal));
    }

    private boolean matches(Task t, String status) {
        if (status == null || status.isBlank() || "ALL".equals(status)) {
            return true;
        }
        if ("RUNNING".equals(status)) {
            return RUNNING.contains(t.status);
        }
        try {
            return t.status == TaskStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
