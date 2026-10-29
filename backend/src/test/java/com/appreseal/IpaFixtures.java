package com.appreseal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 生成测试用的最小 IPA：Info.plist + arm64 Mach-O 主程序 + SC_Info/SUPF。 */
final class IpaFixtures {
    private IpaFixtures() {}

    static byte[] ipa(int cryptid) throws Exception {
        return ipa(cryptid, true, true);
    }

    static byte[] ipa(int cryptid, boolean withPlist, boolean withSupf) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (withPlist) {
            files.put("Payload/Test.app/Info.plist", plist().getBytes(StandardCharsets.UTF_8));
        }
        files.put("Payload/Test.app/Test", macho(cryptid));
        if (withSupf) {
            files.put("Payload/Test.app/SC_Info/Test.supf", new byte[]{1, 2, 3, 4});
        }
        files.put("Payload/Test.app/Assets.car", new byte[]{9, 9, 9});

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String plist() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>CFBundleIdentifier</key>
                  <string>com.example.fixture</string>
                  <key>CFBundleDisplayName</key>
                  <string>测试应用</string>
                  <key>CFBundleShortVersionString</key>
                  <string>1.2.3</string>
                  <key>CFBundleVersion</key>
                  <string>123</string>
                  <key>CFBundleExecutable</key>
                  <string>Test</string>
                </dict>
                </plist>
                """;
    }

    /** 最小 64 位 arm64 Mach-O，仅含一条 LC_ENCRYPTION_INFO_64。 */
    private static byte[] macho(int cryptid) {
        byte[] b = new byte[32 + 24];
        put(b, 0, 0xFEEDFACF);   // MH_MAGIC_64
        put(b, 4, 0x0100000C);   // CPU_TYPE_ARM64
        put(b, 8, 0);            // cpusubtype
        put(b, 12, 2);           // MH_EXECUTE
        put(b, 16, 1);           // ncmds
        put(b, 20, 24);          // sizeofcmds
        put(b, 24, 0);           // flags
        put(b, 28, 0);           // reserved
        put(b, 32, 0x2C);        // LC_ENCRYPTION_INFO_64
        put(b, 36, 24);          // cmdsize
        put(b, 40, 0);           // cryptoff
        put(b, 44, 0);           // cryptsize
        put(b, 48, cryptid);
        put(b, 52, 0);           // pad
        return b;
    }

    private static void put(byte[] b, int offset, int value) {
        b[offset] = (byte) value;
        b[offset + 1] = (byte) (value >>> 8);
        b[offset + 2] = (byte) (value >>> 16);
        b[offset + 3] = (byte) (value >>> 24);
    }
}
