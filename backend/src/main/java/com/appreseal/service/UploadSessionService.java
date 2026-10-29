package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.config.AppProperties;
import com.appreseal.web.dto.Dtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大 IPA 分片上传。文件先按偏移追加到 staging，收齐后再走原来的校验和建单。
 * 中断后用同一个 id 从 {@code received} 继续，不必整包重传。
 */
@Service
public class UploadSessionService {
    public static final int CHUNK_BYTES = 4 * 1024 * 1024;
    public static final int MAX_CHUNK_BYTES = 8 * 1024 * 1024;
    private static final Duration TTL = Duration.ofHours(24);

    private final AppProperties props;
    private final UploadService uploads;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public UploadSessionService(AppProperties props, UploadService uploads, ObjectMapper json) {
        this.props = props;
        this.uploads = uploads;
        this.json = json;
    }

    public Dtos.UploadStatusDto create(String userId, String filename, long size) {
        String name = cleanName(filename);
        long max = props.getExecutor().getMaxUploadBytes();
        if (max > 0 && size > max) {
            throw ApiException.badRequest("IPA 不能超过 " + human(max));
        }
        String id = Ids.of("up");
        Session session = new Session();
        session.id = id;
        session.userId = userId;
        session.filename = name;
        session.size = size;
        session.received = 0;
        session.createdAt = Instant.now();
        session.updatedAt = session.createdAt;
        synchronized (lock(id)) {
            try {
                Files.createDirectories(UploadFiles.stagingDir(props));
                Files.write(part(id), new byte[0]);
                writeMeta(session);
            } catch (IOException e) {
                deleteQuiet(part(id));
                deleteQuiet(meta(id));
                throw ApiException.badRequest("无法创建上传任务");
            }
        }
        return toDto(session);
    }

    public Dtos.UploadStatusDto status(String userId, String id) {
        synchronized (lock(requireId(id))) {
            return toDto(load(userId, id));
        }
    }

    /** 偏移对不上时返回当前进度，调用方用 409 告诉浏览器从哪继续。 */
    public AppendResult append(String userId, String id, long offset, InputStream body) {
        synchronized (lock(requireId(id))) {
            Session session = load(userId, id);
            if (offset != session.received) {
                return new AppendResult(toDto(session), true);
            }
            if (offset >= session.size) {
                throw ApiException.badRequest("文件已经传完，请直接完成上传");
            }
            Path file = part(id);
            long written = 0;
            try (InputStream in = body) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n == 0) {
                        continue;
                    }
                    written += n;
                    if (written > MAX_CHUNK_BYTES || offset + written > session.size) {
                        truncate(file, offset);
                        throw ApiException.badRequest(written > MAX_CHUNK_BYTES
                                ? "单个分片不能超过 8 MB"
                                : "上传内容超过了声明的文件大小");
                    }
                    Files.write(file, java.util.Arrays.copyOf(buf, n), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            } catch (ApiException e) {
                throw e;
            } catch (IOException e) {
                truncate(file, offset);
                throw ApiException.badRequest("写入上传分片失败");
            }
            if (written == 0) {
                throw ApiException.badRequest("分片是空的");
            }
            session.received = offset + written;
            session.updatedAt = Instant.now();
            try {
                writeMeta(session);
            } catch (IOException e) {
                truncate(file, offset);
                throw ApiException.badRequest("保存上传进度失败");
            }
            return new AppendResult(toDto(session), false);
        }
    }

    public Dtos.TaskDto complete(String userId, String id) {
        synchronized (lock(requireId(id))) {
            Session session = load(userId, id);
            if (session.received != session.size) {
                throw ApiException.badRequest("文件还没传完，已收到 " + session.received + " / " + session.size + " 字节");
            }
            try {
                Dtos.TaskDto task = uploads.finish(userId, part(id), session.filename);
                deleteQuiet(meta(id));
                return task;
            } catch (ApiException e) {
                deleteQuiet(part(id));
                deleteQuiet(meta(id));
                throw e;
            }
        }
    }

    /* ------------------------------- 内部实现 ------------------------------- */

    private Session load(String userId, String id) {
        Path meta = meta(id);
        if (!Files.isRegularFile(meta) || !Files.isRegularFile(part(id))) {
            throw ApiException.notFound("上传任务不存在或已过期");
        }
        Session session;
        try {
            session = json.readValue(meta.toFile(), Session.class);
        } catch (IOException e) {
            throw ApiException.notFound("上传任务不存在或已过期");
        }
        if (session.userId == null || !session.userId.equals(userId) || session.createdAt == null
                || session.createdAt.plus(TTL).isBefore(Instant.now())) {
            throw ApiException.notFound("上传任务不存在或已过期");
        }
        try {
            long size = Files.size(part(id));
            if (size != session.received) {
                // 上次写入成功但进度没落盘时，以进度文件为准，避免续传接到脏尾巴上。
                truncate(part(id), Math.min(size, session.received));
                session.received = Math.min(size, session.received);
            }
        } catch (IOException e) {
            throw ApiException.notFound("上传任务不存在或已过期");
        }
        return session;
    }

    private void writeMeta(Session session) throws IOException {
        Path target = meta(session.id);
        Path tmp = target.resolveSibling(session.id + ".json.tmp");
        json.writeValue(tmp.toFile(), session);
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private Path part(String id) {
        return UploadFiles.stagingDir(props).resolve(id + ".part");
    }

    private Path meta(String id) {
        return UploadFiles.stagingDir(props).resolve(id + ".json");
    }

    private Object lock(String id) {
        return locks.computeIfAbsent(id, ignored -> new Object());
    }

    private static String requireId(String id) {
        if (id == null || !id.matches("up_[a-z0-9]{10}")) {
            throw ApiException.notFound("上传任务不存在或已过期");
        }
        return id;
    }

    private static String cleanName(String filename) {
        if (filename == null || filename.isBlank()) {
            throw ApiException.badRequest("缺少文件名");
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".ipa") || name.length() > 180) {
            throw ApiException.badRequest("只支持 .ipa 文件");
        }
        return name;
    }

    private static void truncate(Path file, long size) {
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(Math.max(0, size));
        } catch (IOException ignored) {
            // 回滚失败时下次续传会按文件实际长度对齐
        }
    }

    private static void deleteQuiet(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件留给定时清理
        }
    }

    private static Dtos.UploadStatusDto toDto(Session session) {
        return new Dtos.UploadStatusDto(session.id, session.filename, session.size, session.received, CHUNK_BYTES,
                session.createdAt.plus(TTL));
    }

    private static String human(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f GB", bytes / 1024.0 / 1024 / 1024);
        }
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1024.0 / 1024);
    }

    public record AppendResult(Dtos.UploadStatusDto status, boolean conflict) {}

    /** 只落在 staging 目录，不进数据库。 */
    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    public static class Session {
        public String id;
        public String userId;
        public String filename;
        public long size;
        public long received;
        public Instant createdAt;
        public Instant updatedAt;
    }
}
