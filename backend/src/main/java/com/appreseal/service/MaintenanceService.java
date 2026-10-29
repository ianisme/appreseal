package com.appreseal.service;

import com.appreseal.config.AppProperties;
import com.appreseal.domain.AppSettings;
import com.appreseal.domain.LibraryItem;
import com.appreseal.domain.Task;
import com.appreseal.domain.TaskStatus;
import com.appreseal.repo.LibraryRepository;
import com.appreseal.repo.TaskLogRepository;
import com.appreseal.repo.TaskRepository;
import com.appreseal.web.dto.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 存储治理：按保留期清理产物、上传包、临时工作区和任务日志，并盯着数据卷容量。
 *
 * <p>默认每小时跑一次，保留期来自管理后台的系统设置。手动清理接口给管理员用，
 * 方便在磁盘告警时立刻释放空间。清理只删数据卷内、由数据库记录解析出的路径。
 */
@Service
public class MaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);
    private static final List<TaskStatus> TERMINAL =
            List.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELED);

    private final AppProperties props;
    private final SettingsService settings;
    private final TaskRepository tasks;
    private final TaskLogRepository logs;
    private final LibraryRepository library;
    private final ArtifactService artifacts;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Dtos.CleanupReportDto lastRun = Dtos.CleanupReportDto.empty();

    public MaintenanceService(AppProperties props, SettingsService settings, TaskRepository tasks,
                              TaskLogRepository logs, LibraryRepository library, ArtifactService artifacts) {
        this.props = props;
        this.settings = settings;
        this.tasks = tasks;
        this.logs = logs;
        this.library = library;
        this.artifacts = artifacts;
    }

    @Scheduled(initialDelayString = "${appreseal.storage.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${appreseal.storage.cleanup-interval-ms:3600000}")
    @Transactional
    public void scheduled() {
        if (!props.getStorage().isEnabled()) {
            return;
        }
        AppSettings s = settings.get();
        if (!s.autoCleanup) {
            return;
        }
        try {
            Dtos.CleanupReportDto report = cleanup(s);
            log.info("定时清理完成：产物 {} 个（{}），上传包 {} 个（{}），工作区 {} 个，日志 {} 条，孤儿文件 {} 个",
                    report.artifactsDeleted(), human(report.artifactBytesFreed()),
                    report.uploadsDeleted(), human(report.uploadBytesFreed()),
                    report.workDirsDeleted(), report.logsDeleted(), report.orphanFilesDeleted());
            checkDisk(s);
        } catch (Exception e) {
            log.warn("定时清理失败：{}", e.getMessage(), e);
        }
    }

    /** 管理员手动触发；不受 autoCleanup 开关影响。 */
    @Transactional
    public Dtos.CleanupReportDto runNow() {
        return cleanup(settings.get());
    }

    /** 由 {@link #scheduled()} 或 {@link #runNow()} 在事务内调用，避免自调用绕过事务代理。 */
    private Dtos.CleanupReportDto cleanup(AppSettings s) {
        if (!running.compareAndSet(false, true)) {
            return lastRun;
        }
        Instant started = Instant.now();
        int artifactsDeleted = 0;
        long artifactBytes = 0;
        int uploadsDeleted = 0;
        long uploadBytes = 0;
        int workDirs = 0;
        int logsDeleted = 0;
        int orphans = 0;
        long orphanBytes = 0;
        int errors = 0;

        try {
            /* 1) 过期产物：删文件，标记任务已清理，同时断开下载库里的悬空引用。 */
            Instant artifactCutoff = started.minus(Duration.ofDays(Math.max(1, s.artifactRetentionDays)));
            List<Task> changed = new ArrayList<>();
            for (Task t : tasks.findByStatusInAndFinishedAtBefore(TERMINAL, artifactCutoff)) {
                if (t.artifactPath == null && t.artifactPurged) {
                    continue;
                }
                Path file = artifacts.resolveInsideOutput(t.artifactPath);
                if (file != null && Files.isRegularFile(file)) {
                    try {
                        long size = Files.size(file);
                        if (Files.deleteIfExists(file)) {
                            artifactsDeleted++;
                            artifactBytes += size;
                        }
                    } catch (IOException e) {
                        errors++;
                        log.warn("删除产物失败 {}：{}", file, e.getMessage());
                    }
                }
                for (LibraryItem item : library.findByTaskId(t.id)) {
                    item.artifactPath = null;
                    library.save(item);
                }
                t.artifactPath = null;
                t.artifactPurged = true;
                changed.add(t);
            }
            if (!changed.isEmpty()) {
                tasks.saveAll(changed);
            }

            /* 2) 过期上传包：失败/取消的任务保留一段时间供用户重试，到期后整目录删除。 */
            Instant uploadCutoff = started.minus(Duration.ofDays(Math.max(1, s.uploadRetentionDays)));
            List<Task> uploads = new ArrayList<>();
            for (Task t : tasks.findByFinishedAtBefore(uploadCutoff)) {
                if (t.status.isRunning() || t.uploadPath == null || t.uploadPath.isBlank()) {
                    continue;
                }
                Path input = safeUpload(t.uploadPath);
                if (input != null) {
                    Path dir = input.getParent();
                    long size = sizeTree(dir);
                    UploadFiles.deleteTree(dir);
                    if (!Files.exists(dir)) {
                        uploadsDeleted++;
                        uploadBytes += size;
                    }
                }
                t.uploadPath = null;
                uploads.add(t);
            }
            if (!uploads.isEmpty()) {
                tasks.saveAll(uploads);
            }

            /* 3) 临时工作区：任务结束后 24 小时清掉，避免 /app/data/work 无限增长。 */
            workDirs = cleanWorkDirs(started.minus(Duration.ofHours(24)));

            /* 4) 任务日志：任务记录保留，只清日志正文。 */
            Instant logCutoff = started.minus(Duration.ofDays(Math.max(1, s.logRetentionDays)));
            for (Task t : tasks.findByFinishedAtBefore(logCutoff)) {
                logs.deleteByTaskId(t.id);
                logsDeleted++;
            }

            /* 5) 孤儿文件：数据库已经不引用的产物/上传目录，超过 48 小时就删。 */
            Instant orphanCutoff = started.minus(Duration.ofHours(48));
            for (Path file : listChildren(artifacts.outputDir())) {
                if (!Files.isRegularFile(file) || modifiedAfter(file, orphanCutoff)) {
                    continue;
                }
                String raw = file.toString();
                if (tasks.existsByArtifactPath(raw) || library.existsByArtifactPath(raw)) {
                    continue;
                }
                try {
                    long size = Files.size(file);
                    if (Files.deleteIfExists(file)) {
                        orphans++;
                        orphanBytes += size;
                    }
                } catch (IOException e) {
                    errors++;
                }
            }
            long[] uploadOrphans = cleanOrphanUploads(orphanCutoff);
            orphans += (int) uploadOrphans[0];
            orphanBytes += uploadOrphans[1];

            /* 6) multipart 暂存目录：异常中断留下的临时文件。 */
            for (Path file : listChildren(UploadFiles.stagingDir(props))) {
                if (!Files.isRegularFile(file) || modifiedAfter(file, started.minus(Duration.ofHours(24)))) {
                    continue;
                }
                try {
                    long size = Files.size(file);
                    if (Files.deleteIfExists(file)) {
                        orphans++;
                        orphanBytes += size;
                    }
                } catch (IOException e) {
                    errors++;
                }
            }
        } finally {
            running.set(false);
        }

        Dtos.CleanupReportDto report = new Dtos.CleanupReportDto(artifactsDeleted, artifactBytes,
                uploadsDeleted, uploadBytes, workDirs, logsDeleted, orphans, orphanBytes, errors,
                started, Instant.now());
        lastRun = report;
        return report;
    }

    /** 数据卷容量；管理员页面和定时任务共用。 */
    public Dtos.DiskDto disk(AppSettings s) {
        try {
            Path root = dataDir();
            while (root != null && !Files.exists(root)) {
                root = root.getParent();
            }
            if (root == null) {
                return new Dtos.DiskDto(0, 0, 0, 0, "UNKNOWN");
            }
            var store = Files.getFileStore(root);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            long used = Math.max(0, total - usable);
            int percent = total <= 0 ? 0 : (int) Math.round(used * 100.0 / total);
            int warn = Math.max(50, Math.min(99, s.diskWarnPercent));
            String level = percent >= 90 ? "CRITICAL" : percent >= warn ? "WARN" : "OK";
            return new Dtos.DiskDto(total, usable, used, percent, level);
        } catch (IOException e) {
            log.warn("读取磁盘容量失败：{}", e.getMessage());
            return new Dtos.DiskDto(0, 0, 0, 0, "UNKNOWN");
        }
    }

    public Dtos.MaintenanceDto status() {
        AppSettings s = settings.get();
        return new Dtos.MaintenanceDto(disk(s), s.autoCleanup, s.artifactRetentionDays,
                s.uploadRetentionDays, s.logRetentionDays, s.diskWarnPercent, lastRun);
    }

    /* ------------------------------- 内部实现 ------------------------------- */

    private void checkDisk(AppSettings s) {
        Dtos.DiskDto d = disk(s);
        if ("CRITICAL".equals(d.level())) {
            log.error("数据卷使用率 {}%（{} / {}），已超过告警阈值 {}%，请立即清理或扩容",
                    d.usedPercent(), human(d.usedBytes()), human(d.totalBytes()), s.diskWarnPercent);
        } else if ("WARN".equals(d.level())) {
            log.warn("数据卷使用率 {}%（{} / {}），告警阈值 {}%",
                    d.usedPercent(), human(d.usedBytes()), human(d.totalBytes()), s.diskWarnPercent);
        }
    }

    private int cleanWorkDirs(Instant cutoff) {
        Path root = workRoot();
        int deleted = 0;
        for (Path dir : listChildren(root)) {
            if (!Files.isDirectory(dir) || modifiedAfter(dir, cutoff)) {
                continue;
            }
            UploadFiles.deleteTree(dir);
            if (!Files.exists(dir)) {
                deleted++;
            }
        }
        return deleted;
    }

    private long[] cleanOrphanUploads(Instant cutoff) {
        long count = 0;
        long bytes = 0;
        Path root = UploadFiles.root(props);
        for (Path dir : listChildren(root)) {
            String name = dir.getFileName().toString();
            if ("staging".equals(name) || !Files.isDirectory(dir) || modifiedAfter(dir, cutoff)) {
                continue;
            }
            Task task = tasks.findById(name).orElse(null);
            if (task != null && task.uploadPath != null) {
                continue;
            }
            long size = sizeTree(dir);
            UploadFiles.deleteTree(dir);
            if (!Files.exists(dir)) {
                count++;
                bytes += size;
            }
        }
        return new long[]{count, bytes};
    }

    private Path safeUpload(String raw) {
        try {
            Path root = UploadFiles.root(props);
            Path input = Path.of(raw).toAbsolutePath().normalize();
            return input.startsWith(root) && !input.equals(root) ? input : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Path dataDir() {
        String configured = props.getStorage().getDataDir();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path output = artifacts.outputDir();
        return output.getParent() == null ? output : output.getParent();
    }

    private Path workRoot() {
        return Path.of(props.getExecutor().getWorkDir()).toAbsolutePath().normalize();
    }

    private static List<Path> listChildren(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean modifiedAfter(Path path, Instant cutoff) {
        try {
            FileTime time = Files.getLastModifiedTime(path);
            return time.toInstant().isAfter(cutoff);
        } catch (IOException e) {
            return true;
        }
    }

    private static long sizeTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }
}
