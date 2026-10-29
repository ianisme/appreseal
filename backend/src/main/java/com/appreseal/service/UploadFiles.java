package com.appreseal.service;

import com.appreseal.common.ApiException;
import com.appreseal.config.AppProperties;
import com.appreseal.domain.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** 上传 IPA 的固定存储布局：/app/data/uploads/{taskId}/input.ipa。 */
public final class UploadFiles {
    private UploadFiles() {}

    public static Path root(AppProperties props) {
        return Path.of(props.getExecutor().getUploadDir()).toAbsolutePath().normalize();
    }

    public static Path stagingDir(AppProperties props) {
        return root(props).resolve("staging");
    }

    public static Path taskInput(AppProperties props, String taskId) {
        return root(props).resolve(taskId).resolve("input.ipa");
    }

    /** 只允许读取上传目录内的文件，避免任务记录里的路径被用来读任意文件。 */
    public static Path requireInput(AppProperties props, Task task) {
        if (task.uploadPath == null || task.uploadPath.isBlank()) {
            throw ApiException.badRequest("任务缺少上传文件");
        }
        Path root = root(props);
        Path input = Path.of(task.uploadPath).toAbsolutePath().normalize();
        if (!input.startsWith(root) || input.equals(root)) {
            throw ApiException.badRequest("上传文件路径不合法");
        }
        if (!Files.isRegularFile(input)) {
            throw ApiException.badRequest("上传文件已不存在，请重新提交");
        }
        return input;
    }

    public static void deleteTaskDir(Path input) {
        if (input == null) {
            return;
        }
        Path dir = input.getParent();
        if (dir == null) {
            return;
        }
        deleteTree(dir);
    }

    public static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响任务
                }
            });
        } catch (IOException ignored) {
            // 清理失败不影响任务
        }
    }
}
