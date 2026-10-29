package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.common.Ids;
import com.appreseal.config.AppProperties;
import com.appreseal.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 接收用户上传的加密 IPA：先落盘校验，再交给 TaskService 扣费建单。
 * 校验失败不会产生任务和扣费。
 */
@Service
public class UploadService {
    private final AppProperties props;
    private final TaskService tasks;

    public UploadService(AppProperties props, TaskService tasks) {
        this.props = props;
        this.tasks = tasks;
        try {
            Files.createDirectories(UploadFiles.stagingDir(props));
        } catch (Exception e) {
            // 目录创建失败会在真正上传时给出明确错误
        }
    }

    public Dtos.TaskDto create(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要脱壳的 IPA 文件");
        }
        long max = props.getExecutor().getMaxUploadBytes();
        if (max > 0 && file.getSize() > max) {
            throw ApiException.badRequest("IPA 不能超过 " + humanSize(max));
        }
        String original = file.getOriginalFilename();
        if (!IpaInspector.looksLikeIpa(original)) {
            throw ApiException.badRequest("只支持 .ipa 文件");
        }

        Path stagingDir = UploadFiles.stagingDir(props);
        Path staged = stagingDir.resolve(Ids.of("up") + ".ipa");
        try {
            Files.createDirectories(stagingDir);
            file.transferTo(staged);
            return finish(userId, staged, original);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("保存上传文件失败：" + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(staged);
            } catch (Exception ignored) {
                // 已移入任务目录时这里是空操作
            }
        }
    }

    /** 分片上传收齐后走同一套校验和建单。调用方负责在失败时清理临时文件。 */
    public Dtos.TaskDto finish(String userId, Path staged, String originalName) {
        IpaInspector.Info info;
        try {
            info = IpaInspector.inspect(staged, true);
        } catch (IpaInspector.IpaException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        validate(info);
        return tasks.createUpload(userId, staged, info, originalName);
    }

    private static void validate(IpaInspector.Info info) {
        String bundleId = info.bundleId();
        if (bundleId == null || !bundleId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")) {
            throw ApiException.badRequest("IPA 的 bundle id 不合法");
        }
        if (info.name() == null || info.name().isBlank()) {
            throw ApiException.badRequest("IPA 缺少应用名称");
        }
        if (info.encryptedBinaries() <= 0) {
            throw ApiException.badRequest("这个 IPA 没有可解密的加密段");
        }
    }

    private static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f GB", bytes / 1024.0 / 1024 / 1024);
        }
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1024.0 / 1024);
    }
}
