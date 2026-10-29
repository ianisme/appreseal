package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import com.appreseal.domain.LibraryItem;
import com.appreseal.domain.Task;
import com.appreseal.domain.TaskStatus;
import com.appreseal.repo.LibraryRepository;
import com.appreseal.repo.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 产物只从配置的输出目录读出，避免任务记录里的路径被用来读任意文件。
 * mock 模式写一份清单；shell 模式把 appget 的产物复制进该目录后再提供下载。
 */
@Service
public class ArtifactService {
    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

    private final AppProperties props;
    private final TaskRepository tasks;
    private final LibraryRepository library;

    public ArtifactService(AppProperties props, TaskRepository tasks, LibraryRepository library) {
        this.props = props;
        this.tasks = tasks;
        this.library = library;
    }

    /** 把外部产物收进输出目录；没有文件时写一份清单。返回可保存的路径。 */
    public String adopt(Task task, String externalPath) {
        try {
            Path dir = outputDir();
            Files.createDirectories(dir);
            Path copied = copyInside(dir, task.id, externalPath);
            if (copied != null) {
                return copied.toString();
            }
            Path manifest = dir.resolve(task.id + ".txt");
            Files.writeString(manifest, taskManifest(task));
            return manifest.toString();
        } catch (Exception e) {
            log.warn("写入产物失败 task={}: {}", task.id, e.getMessage());
            return null;
        }
    }

    public record FileRef(Path path, String title, String version) {}

    @Transactional
    public FileRef taskFile(String taskId, String userId, boolean admin) {
        Task task = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("任务不存在"));
        if (!admin && !task.userId.equals(userId)) {
            throw ApiException.forbidden("无权下载该产物");
        }
        if (task.status != TaskStatus.SUCCEEDED) {
            throw ApiException.badRequest("任务尚未完成，没有可下载的产物");
        }
        if (task.artifactPurged) {
            throw ApiException.badRequest("产物已超过保留期并自动清理，如需再次下载请重新脱壳");
        }
        Path existing = safeFile(task.artifactPath);
        if (existing != null) {
            return new FileRef(existing, task.appName, task.version);
        }
        // 记录里从来没有产物路径：历史/演示数据按清单处理。
        if (task.artifactPath != null && !task.simulated) {
            // 真实产物丢失（磁盘故障/手工删除）时不伪造清单，避免用户下载到假文件。
            throw ApiException.badRequest("产物暂时不可用，请联系客服处理");
        }
        String stored = adopt(task, null);
        if (stored == null) {
            throw ApiException.badRequest("产物生成失败");
        }
        task.artifactPath = stored;
        tasks.save(task);
        return new FileRef(Path.of(stored), task.appName, task.version);
    }

    @Transactional
    public FileRef libraryFile(String libraryId, String userId, boolean admin) {
        LibraryItem item = library.findById(libraryId).orElseThrow(() -> ApiException.notFound("应用不在下载库中"));
        if (!admin && !item.userId.equals(userId)) {
            throw ApiException.forbidden("无权下载该应用");
        }
        if (item.taskId != null) {
            Task task = tasks.findById(item.taskId).orElse(null);
            if (task != null) {
                if (task.artifactPurged) {
                    throw ApiException.badRequest("产物已超过保留期并自动清理，如需再次下载请重新脱壳");
                }
                Path fromTask = safeFile(task.artifactPath);
                if (fromTask != null) {
                    return new FileRef(fromTask, item.name, item.version);
                }
                if (task.artifactPath != null && !task.simulated) {
                    throw ApiException.badRequest("产物暂时不可用，请联系客服处理");
                }
            }
        }
        Path own = safeFile(item.artifactPath);
        if (own != null) {
            return new FileRef(own, item.name, item.version);
        }
        try {
            Path dir = outputDir();
            Files.createDirectories(dir);
            Path manifest = dir.resolve("lib-" + item.id + ".txt");
            if (!Files.isRegularFile(manifest)) {
                Files.writeString(manifest, libraryManifest(item));
            }
            return new FileRef(manifest, item.name, item.version);
        } catch (IOException e) {
            throw ApiException.badRequest("产物生成失败");
        }
    }

    public static String downloadName(Path path, String title, String version) {
        String ext = extension(path.getFileName().toString());
        String base = (title == null || title.isBlank() ? "appreseal" : title) + "-" + (version == null ? "latest" : version);
        base = base.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return base + ext;
    }

    private Path copyInside(Path dir, String id, String externalPath) throws IOException {
        if (externalPath == null || externalPath.isBlank()) {
            return null;
        }
        Path src = Path.of(externalPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(src)) {
            return null;
        }
        if (src.startsWith(dir)) {
            return src;
        }
        Path dest = dir.resolve(id + extension(src.getFileName().toString()));
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private Path safeFile(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path dir = outputDir();
            Path file = Path.of(raw).toAbsolutePath().normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                return null;
            }
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    /** 产物目录：清理任务和下载接口共用，避免各写一套路径解析。 */
    public Path outputDir() {
        return Path.of(props.getExecutor().getOutputDir()).toAbsolutePath().normalize();
    }

    /** 只接受产物目录内的路径，防止清理时被数据库里的异常路径带到别处。 */
    public Path resolveInsideOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path dir = outputDir();
            Path file = Path.of(raw).toAbsolutePath().normalize();
            return file.startsWith(dir) && !file.equals(dir) ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".txt";
        }
        String ext = name.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : ".bin";
    }

    private static String taskManifest(Task t) {
        return """
                AppReseal · 产物清单
                ==============================
                应用: %s
                bundle id: %s
                版本: %s
                大小: %s
                SHA256: %s
                cryptid: 0（已脱壳）
                任务: %s

                当前是模拟执行器，没有真实 IPA。接入 shell 模式后，这里会是 appget.sh 产出的文件。
                """.formatted(t.appName, t.bundleId, t.version, t.sizeBytes == null ? "—" : t.sizeBytes + " bytes",
                t.sha256 == null ? "—" : t.sha256, t.id);
    }

    private static String libraryManifest(LibraryItem item) {
        return """
                AppReseal · 成品清单
                ==============================
                应用: %s
                bundle id: %s
                版本: %s
                来源: %s

                模拟环境不附带真实 IPA。购买或脱壳成功后，可重复下载本清单。
                """.formatted(item.name, item.bundleId, item.version, item.source);
    }
}
