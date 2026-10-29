package com.appreseal;

import com.appreseal.service.IpaInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpaInspectorTest {
    @TempDir
    Path dir;

    @Test
    void readsPlistAndCountsEncryptedBinary() throws Exception {
        Path ipa = Files.write(dir.resolve("encrypted.ipa"), IpaFixtures.ipa(1));
        IpaInspector.Info info = IpaInspector.inspect(ipa, true);
        assertEquals("com.example.fixture", info.bundleId());
        assertEquals("测试应用", info.name());
        assertEquals("1.2.3", info.version());
        assertEquals("Test", info.executable());
        assertEquals(1, info.encryptedBinaries());
        assertEquals(Files.size(ipa), info.sizeBytes());
        assertEquals(64, info.sha256().length());
    }

    @Test
    void rejectsAlreadyDecryptedIpa() throws Exception {
        Path ipa = Files.write(dir.resolve("decrypted.ipa"), IpaFixtures.ipa(0));
        IpaInspector.IpaException error = assertThrows(IpaInspector.IpaException.class,
                () -> IpaInspector.inspect(ipa, true));
        assertTrue(error.getMessage().contains("没有可解密的加密段"));
    }

    @Test
    void acceptsDecryptedArtifactWhenEncryptionNotRequired() throws Exception {
        Path ipa = Files.write(dir.resolve("artifact.ipa"), IpaFixtures.ipa(0));
        IpaInspector.Info info = IpaInspector.inspect(ipa, false);
        assertEquals(0, info.encryptedBinaries());
    }

    @Test
    void rejectsEncryptedArtifactWhenEncryptionNotRequired() throws Exception {
        Path ipa = Files.write(dir.resolve("still-encrypted.ipa"), IpaFixtures.ipa(1));
        assertThrows(IpaInspector.IpaException.class, () -> IpaInspector.inspect(ipa, false));
    }

    @Test
    void rejectsIpaWithoutInfoPlist() throws Exception {
        Path ipa = Files.write(dir.resolve("no-plist.ipa"), IpaFixtures.ipa(1, false, true));
        IpaInspector.IpaException error = assertThrows(IpaInspector.IpaException.class,
                () -> IpaInspector.inspect(ipa, true));
        assertTrue(error.getMessage().contains("Info.plist"));
    }

    @Test
    void rejectsNonZipFile() throws Exception {
        Path ipa = Files.write(dir.resolve("broken.ipa"), "not a zip".getBytes());
        assertThrows(IpaInspector.IpaException.class, () -> IpaInspector.inspect(ipa, true));
    }
}
