package com.appreseal.service;

import com.appreseal.config.AppProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** 容器内 ipatool 数据卷的固定布局。后台登录、凭证导入、真实执行器共用同一套路径。 */
public final class IpatoolFiles {
    private IpatoolFiles() {}

    public static Path home(AppProperties props) {
        return Path.of(props.getIpatool().getHome()).toAbsolutePath().normalize();
    }

    public static String slug(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._@+-]", "_");
    }

    public static Path session(Path home, String email) {
        return home.resolve("accounts").resolve(slug(email) + ".session");
    }

    public static Path credential(Path home, String email) {
        return home.resolve("creds").resolve(slug(email)).resolve("credential.bin");
    }

    public static Path passphrase(Path home) {
        return home.resolve(".ipatool-passphrase");
    }

    public static String readPassphrase(Path home) throws java.io.IOException {
        Path file = passphrase(home);
        return Files.isRegularFile(file) ? Files.readString(file).trim() : "";
    }

    public static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
            // 非 POSIX 文件系统不阻断流程
        }
    }
}
