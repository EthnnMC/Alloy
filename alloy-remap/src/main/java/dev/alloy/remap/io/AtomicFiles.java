package dev.alloy.remap.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file atomically: content goes to a temporary file in the same directory, which is then
 * renamed to the target, so a half-written jar is never left in the cache.
 */
public final class AtomicFiles {

    private static final String TEMPORARY_SUFFIX = ".tmp";

    private AtomicFiles() {
        // Utility class: no instances.
    }

    /**
     * Creates or replaces {@code target} with the content written by {@code filler}.
     *
     * @param target the final file; parent directories are created as needed
     * @param filler fills the temporary file; if it throws, {@code target} is untouched
     * @throws IOException if writing or renaming fails
     */
    public static void replace(Path target, FileFiller filler) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Files.createDirectories(absoluteTarget.getParent());
        Path temporary = Files.createTempFile(
                absoluteTarget.getParent(), absoluteTarget.getFileName() + ".", AtomicFiles.TEMPORARY_SUFFIX);
        try {
            filler.fill(temporary);
            AtomicFiles.moveIntoPlace(temporary, absoluteTarget);
        } finally {
            // After a successful rename the temporary file is gone: nothing to delete.
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Some file systems (network drives) cannot rename atomically: fall back to a plain move.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
