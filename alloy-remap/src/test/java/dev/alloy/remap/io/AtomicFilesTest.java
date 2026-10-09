package dev.alloy.remap.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void createsTheTargetAndItsParentDirectories(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("cache/forge/out.bin");

        AtomicFiles.replace(target, temporary -> Files.writeString(temporary, "content"));

        assertEquals("content", Files.readString(target));
        assertEquals(List.of("out.bin"), AtomicFilesTest.namesIn(target.getParent()));
    }

    @Test
    void replacesAnExistingTarget(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("out.bin");
        Files.writeString(target, "old");

        AtomicFiles.replace(target, temporary -> Files.writeString(temporary, "new"));

        assertEquals("new", Files.readString(target));
    }

    @Test
    void failedWriteLeavesTheOldTargetAndNoTemporaryFile(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("out.bin");
        Files.writeString(target, "old");

        assertThrows(IOException.class, () -> AtomicFiles.replace(target, temporary -> {
            Files.writeString(temporary, "half writ");
            throw new IOException("disk full");
        }));

        assertEquals("old", Files.readString(target));
        assertEquals(List.of("out.bin"), AtomicFilesTest.namesIn(directory));
    }

    @Test
    void failedFirstWriteCreatesNothing(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("out.bin");

        assertThrows(IOException.class, () -> AtomicFiles.replace(target, temporary -> {
            throw new IOException("no network");
        }));

        assertFalse(Files.exists(target));
        assertEquals(List.of(), AtomicFilesTest.namesIn(directory));
    }
}
