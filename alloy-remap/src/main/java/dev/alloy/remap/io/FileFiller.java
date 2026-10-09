package dev.alloy.remap.io;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the content of a temporary file, for {@link AtomicFiles#replace}.
 * Like {@code Consumer<Path>}, but may throw {@link IOException}.
 */
@FunctionalInterface
public interface FileFiller {

    /**
     * Writes the complete file content.
     *
     * @param temporaryFile empty, already created file to fill
     * @throws IOException if writing fails or the resulting content is rejected
     */
    void fill(Path temporaryFile) throws IOException;
}
