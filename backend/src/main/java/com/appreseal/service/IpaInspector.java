package com.appreseal.service;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 只读解析已上传的 IPA：读取顶层 Info.plist，并检查 SC_Info 引用的主程序是否仍带
 * FairPlay 加密段。不调用 Python、不需要 Apple ID，用于上传时的快速校验。
 */
public final class IpaInspector {
    private static final int MAX_ENTRIES = 300_000;
    private static final long MAX_UNCOMPRESSED = 12L * 1024 * 1024 * 1024;
    private static final long MAX_LOAD_COMMANDS = 4L * 1024 * 1024;
    private static final int ARM64 = 0x0100000C;
    private static final Pattern TOP_APP_PLIST =
            Pattern.compile("^Payload/[^/]+\\.app/Info\\.plist$");

    private IpaInspector() {}

    public record Info(String bundleId, String name, String version, String executable,
                       int encryptedBinaries, long sizeBytes, String sha256) {}

    public static final class IpaException extends Exception {
        public IpaException(String message) {
            super(message);
        }
    }

    /**
     * @param requireEncrypted true 用于上传校验：至少要有 1 个可解密的加密主程序；
     *                         false 用于产物校验：要求所有 SUPF 引用的主程序都已 cryptid=0
     */
    public static Info inspect(Path ipa, boolean requireEncrypted) throws IpaException {
        if (!Files.isRegularFile(ipa)) {
            throw new IpaException("IPA 文件不存在");
        }
        try (ZipFile zip = new ZipFile(ipa.toFile())) {
            List<ZipEntry> entries = entries(zip);
            ZipEntry plistEntry = findTopAppPlist(entries);
            if (plistEntry == null) {
                throw new IpaException("不是有效的 IPA：缺少 Payload/*.app/Info.plist");
            }
            NSDictionary plist = parsePlist(zip, plistEntry);
            String appDir = plistEntry.getName().substring(0, plistEntry.getName().length() - "Info.plist".length());
            String bundleId = text(plist, "CFBundleIdentifier");
            String name = firstNonBlank(
                    text(plist, "CFBundleDisplayName"),
                    text(plist, "CFBundleName"),
                    appDir.substring("Payload/".length(), appDir.length() - 1));
            String version = firstNonBlank(
                    text(plist, "CFBundleShortVersionString"),
                    text(plist, "CFBundleVersion"),
                    "latest");
            String executable = text(plist, "CFBundleExecutable");
            if (bundleId == null || bundleId.isBlank()) {
                throw new IpaException("IPA 的 Info.plist 缺少 CFBundleIdentifier");
            }
            if (executable == null || executable.isBlank()) {
                executable = appDir.substring("Payload/".length(), appDir.length() - 1);
            }
            bundleId = clean(bundleId);
            name = clean(name);
            version = clean(version);
            executable = clean(executable);

            int encrypted = countEncryptedBinaries(zip, entries);
            if (requireEncrypted && encrypted == 0) {
                throw new IpaException("这个 IPA 没有可解密的加密段（cryptid=0），可能已经是脱壳后的包");
            }
            if (!requireEncrypted && encrypted > 0) {
                throw new IpaException("解密产物仍包含 " + encrypted + " 个加密主程序");
            }
            return new Info(bundleId, name, version, executable, encrypted,
                    Files.size(ipa), sha256(ipa));
        } catch (ZipException e) {
            throw new IpaException("不是有效的 ZIP/IPA 文件");
        } catch (IpaException e) {
            throw e;
        } catch (Exception e) {
            throw new IpaException("IPA 解析失败：" + e.getMessage());
        }
    }

    private static List<ZipEntry> entries(ZipFile zip) throws IpaException {
        List<ZipEntry> out = new ArrayList<>();
        long total = 0;
        Enumeration<? extends ZipEntry> it = zip.entries();
        while (it.hasMoreElements()) {
            ZipEntry entry = it.nextElement();
            out.add(entry);
            if (out.size() > MAX_ENTRIES) {
                throw new IpaException("IPA 文件条目过多");
            }
            if (entry.getSize() > 0) {
                total += entry.getSize();
                if (total > MAX_UNCOMPRESSED) {
                    throw new IpaException("IPA 解压后体积超过限制");
                }
            }
        }
        return out;
    }

    private static ZipEntry findTopAppPlist(List<ZipEntry> entries) {
        return entries.stream()
                .filter(e -> !e.isDirectory() && TOP_APP_PLIST.matcher(e.getName()).matches())
                .min((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .orElse(null);
    }

    private static NSDictionary parsePlist(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream in = zip.getInputStream(entry)) {
            NSObject parsed = PropertyListParser.parse(in);
            if (!(parsed instanceof NSDictionary dict)) {
                throw new IpaException("IPA 的 Info.plist 格式不正确");
            }
            return dict;
        }
    }

    /**
     * 统计仍处于 cryptid=1 的主程序数量。扫描范围与 fairplay-decrypt/decrypt.py 一致：
     * SC_Info/&lt;name&gt;.supf 对应同目录上层的 &lt;name&gt; 主程序。
     */
    private static int countEncryptedBinaries(ZipFile zip, List<ZipEntry> entries) throws Exception {
        int encrypted = 0;
        for (ZipEntry entry : entries) {
            String name = entry.getName();
            if (!name.endsWith(".supf")) {
                continue;
            }
            int marker = name.lastIndexOf("/SC_Info/");
            if (marker <= 0) {
                continue;
            }
            String bundle = name.substring(0, marker);
            String filename = name.substring(marker + "/SC_Info/".length());
            String stem = filename.endsWith(".v5.supf")
                    ? filename.substring(0, filename.length() - ".v5.supf".length())
                    : filename.substring(0, filename.length() - ".supf".length());
            if (stem.isBlank() || stem.contains("/")) {
                throw new IpaException("IPA 结构不合法：SUPF 文件名异常 " + name);
            }
            String binary = bundle + "/" + stem;
            ZipEntry binaryEntry = zip.getEntry(binary);
            if (binaryEntry == null || binaryEntry.isDirectory()) {
                throw new IpaException("IPA 结构不完整：SUPF 缺少对应主程序 " + binary);
            }
            int cryptid = readCryptid(zip, binaryEntry);
            if (cryptid == 1) {
                encrypted++;
            } else if (cryptid != 0 && cryptid != -1) {
                throw new IpaException("主程序使用了不支持的 cryptid=" + cryptid + "：" + binary);
            }
        }
        return encrypted;
    }

    /** 读取 Mach-O（含 FAT）的 LC_ENCRYPTION_INFO_64.cryptid；没有该命令返回 -1。 */
    private static int readCryptid(ZipFile zip, ZipEntry entry) throws Exception {
        long size = entry.getSize();
        if (size <= 0) {
            throw new IpaException("主程序为空：" + entry.getName());
        }
        byte[] head = readRange(zip, entry, 0, 8);
        if (head.length < 8) {
            throw new IpaException("主程序不是有效的 Mach-O：" + entry.getName());
        }
        long sliceOffset = 0;
        long sliceSize = size;
        if (head[0] == (byte) 0xCA && head[1] == (byte) 0xFE
                && head[2] == (byte) 0xBA && (head[3] == (byte) 0xBE || head[3] == (byte) 0xBF)) {
            boolean fat64 = head[3] == (byte) 0xBF;
            int entrySize = fat64 ? 32 : 20;
            int count = readIntBE(head, 4);
            if (count <= 0 || count > 16) {
                throw new IpaException("主程序架构表不合法：" + entry.getName());
            }
            byte[] table = readRange(zip, entry, 8, count * entrySize);
            long foundOffset = -1;
            long foundSize = 0;
            for (int i = 0; i < count; i++) {
                int pos = i * entrySize;
                if (readIntBE(table, pos) != ARM64) {
                    continue;
                }
                long off = fat64 ? readLongBE(table, pos + 8) : readIntBE(table, pos + 8);
                long len = fat64 ? readLongBE(table, pos + 16) : readIntBE(table, pos + 12);
                if (foundOffset >= 0) {
                    throw new IpaException("主程序包含多个 arm64 切片，暂不支持");
                }
                foundOffset = off;
                foundSize = len;
            }
            if (foundOffset < 0) {
                throw new IpaException("主程序不含 arm64 切片：" + entry.getName());
            }
            sliceOffset = foundOffset;
            sliceSize = foundSize;
        } else if (!(head[0] == (byte) 0xCF && head[1] == (byte) 0xFA
                && head[2] == (byte) 0xED && head[3] == (byte) 0xFE)) {
            throw new IpaException("主程序不是 arm64 Mach-O：" + entry.getName());
        }
        if (sliceOffset < 0 || sliceOffset + 32 > size || sliceSize <= 0) {
            throw new IpaException("主程序切片越界：" + entry.getName());
        }

        byte[] header = readRange(zip, entry, sliceOffset, 32);
        if (header.length < 32 || readIntLE(header, 0) != 0xFEEDFACF) {
            throw new IpaException("主程序不是 64 位 Mach-O：" + entry.getName());
        }
        int ncmds = readIntLE(header, 16);
        long sizeofcmds = readIntLE(header, 20);
        if (ncmds <= 0 || ncmds > 1000 || sizeofcmds <= 0 || sizeofcmds > MAX_LOAD_COMMANDS) {
            throw new IpaException("主程序加载命令不合法：" + entry.getName());
        }
        byte[] commands = readRange(zip, entry, sliceOffset + 32, (int) sizeofcmds);
        int pos = 0;
        Integer cryptid = null;
        for (int i = 0; i < ncmds; i++) {
            if (pos + 8 > commands.length) {
                throw new IpaException("主程序加载命令被截断：" + entry.getName());
            }
            int cmd = readIntLE(commands, pos);
            int len = readIntLE(commands, pos + 4);
            if (len < 8 || pos + len > commands.length) {
                throw new IpaException("主程序加载命令长度不合法：" + entry.getName());
            }
            if (cmd == 0x2C) {
                if (len < 24) {
                    throw new IpaException("主程序加密信息不完整：" + entry.getName());
                }
                if (cryptid != null) {
                    throw new IpaException("主程序存在多个加密信息命令：" + entry.getName());
                }
                cryptid = readIntLE(commands, pos + 16);
            }
            pos += len;
        }
        return cryptid == null ? -1 : cryptid;
    }

    private static byte[] readRange(ZipFile zip, ZipEntry entry, long offset, int length) throws IOException {
        if (length <= 0) {
            return new byte[0];
        }
        try (InputStream in = zip.getInputStream(entry)) {
            long skipped = 0;
            while (skipped < offset) {
                long n = in.skip(offset - skipped);
                if (n <= 0) {
                    if (in.read() < 0) {
                        return new byte[0];
                    }
                    n = 1;
                }
                skipped += n;
            }
            return in.readNBytes(length);
        }
    }

    private static String text(NSDictionary plist, String key) {
        NSObject value = plist.objectForKey(key);
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static int readIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static long readLongBE(byte[] data, int offset) {
        long hi = readIntBE(data, offset) & 0xFFFFFFFFL;
        long lo = readIntBE(data, offset + 4) & 0xFFFFFFFFL;
        return (hi << 32) | lo;
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    /** 用于测试与日志的统一小写后缀判断。 */
    public static boolean looksLikeIpa(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".ipa");
    }
}
