package dev.alloy.remap.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

class JarSanitizerTest {

    private static byte[] manifest(String text) {
        return text.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void recognisesSignatureFilesWhateverTheirCase() {
        assertTrue(JarSanitizer.isSignatureFile("META-INF/FORGE.SF"));
        assertTrue(JarSanitizer.isSignatureFile("META-INF/FORGE.DSA"));
        assertTrue(JarSanitizer.isSignatureFile("META-INF/MOJANGCS.RSA"));
        assertTrue(JarSanitizer.isSignatureFile("meta-inf/key.ec"));
        assertTrue(JarSanitizer.isSignatureFile("META-INF/SIG-ANYTHING"));
    }

    @Test
    void leavesOrdinaryFilesAlone() {
        assertFalse(JarSanitizer.isSignatureFile("META-INF/MANIFEST.MF"));
        assertFalse(JarSanitizer.isSignatureFile("META-INF/"));
        assertFalse(JarSanitizer.isSignatureFile("META-INF/services/some.Service"));
        assertFalse(JarSanitizer.isSignatureFile("META-INF/maven/group/NOTES.SF"));
        assertFalse(JarSanitizer.isSignatureFile("assets/mod/sounds/hit.sf"));
        assertFalse(JarSanitizer.isSignatureFile("example/Mod.class"));
    }

    @Test
    void removesDigestsAndClassPathButKeepsEverythingElse() throws IOException {
        byte[] signed = JarSanitizerTest.manifest("""
                Manifest-Version: 1.0
                TweakClass: net.minecraftforge.fml.common.launcher.FMLTweaker
                Class-Path: libraries/a.jar libraries/b.jar
                FMLAT: example_at.cfg

                Name: example/Mod.class
                SHA-256-Digest: MdWIiX4OOfE2aefloA+1GaXaZKW52cSH/VI5Y1e2CuE=

                Name: example/Sealed.class
                SHA1-Digest: AAAAAAAAAAAAAAAAAAAAAAAAAAA=
                Sealed: true

                """);

        Manifest cleaned = new Manifest(new ByteArrayInputStream(JarSanitizer.cleanManifest(signed)));

        assertEquals("1.0", cleaned.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("net.minecraftforge.fml.common.launcher.FMLTweaker", cleaned.getMainAttributes().getValue("TweakClass"));
        assertEquals("example_at.cfg", cleaned.getMainAttributes().getValue("FMLAT"));
        assertNull(cleaned.getMainAttributes().getValue("Class-Path"));
        assertEquals(Set.of("example/Sealed.class"), cleaned.getEntries().keySet());
        assertEquals("true", cleaned.getAttributes("example/Sealed.class").getValue("Sealed"));
        assertNull(cleaned.getAttributes("example/Sealed.class").getValue("SHA1-Digest"));
    }

    @Test
    void returnsTheVerySameBytesWhenThereIsNothingToRemove() throws IOException {
        byte[] plain = JarSanitizerTest.manifest("""
                Manifest-Version: 1.0
                Custom-Attribute:   odd   spacing kept

                """);

        assertSame(plain, JarSanitizer.cleanManifest(plain));
    }
}
