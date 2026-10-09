package dev.alloy.agent.launch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the lookup of Lunar's class cache on a folder laid out like Lunar's. */
class BakedClassesTest {

    private static final byte[] MAIN = {1, 2, 3};
    private static final byte[] OTHER_MAIN = {4, 5, 6};
    private static final byte[] MINECRAFT = {7, 8, 9};

    @TempDir
    Path lunarDirectory;

    @Test
    void picksTheCacheWhoseMainClassIsTheOneTheGameDefined() throws IOException {
        this.writeCache("aaaa/1111", Map.of("net.minecraft.client.main.Main", BakedClassesTest.OTHER_MAIN));
        Path right = this.writeCache("bbbb/2222", Map.of(
                "net.minecraft.client.main.Main", BakedClassesTest.MAIN,
                "net.minecraft.client.Minecraft", BakedClassesTest.MINECRAFT));

        Optional<BakedClasses> located = BakedClasses.locate(
                List.of(this.lunarDirectory.resolve("lunar.jar")), BakedClassesTest.MAIN);

        try (BakedClasses baked = located.orElseThrow()) {
            assertEquals(right, baked.file());
            // Entries are named by dotted class name; callers ask with internal names.
            assertArrayEquals(BakedClassesTest.MINECRAFT, baked.read("net/minecraft/client/Minecraft"));
            assertNull(baked.read("net/minecraft/client/Missing"));
        }
    }

    @Test
    void noCacheIsFoundWhenNoneMatches() throws IOException {
        this.writeCache("aaaa/1111", Map.of("net.minecraft.client.main.Main", BakedClassesTest.OTHER_MAIN));

        assertTrue(BakedClasses.locate(List.of(this.lunarDirectory.resolve("lunar.jar")), BakedClassesTest.MAIN).isEmpty());
        assertTrue(BakedClasses.locate(List.of(), BakedClassesTest.MAIN).isEmpty());
    }

    private Path writeCache(String hashes, Map<String, byte[]> entries) throws IOException {
        Path file = this.lunarDirectory.resolve("cache").resolve(hashes).resolve("bake.zip");
        Files.createDirectories(file.getParent());
        try (OutputStream output = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }
}
