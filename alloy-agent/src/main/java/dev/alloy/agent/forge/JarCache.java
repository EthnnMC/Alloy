package dev.alloy.agent.forge;

import dev.alloy.remap.io.Sha256;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache of rewritten jars ({@code ~/.alloy/cache}). Each jar name contains a key computed from
 * everything its content depends on, so a change simply produces a new jar. Next to a mod jar, two
 * small text files remember the {@code @Mod} classes and the warnings found while rewriting.
 */
public final class JarCache {

    private static final int KEY_LENGTH = 16;
    private static final String JAR_SUFFIX = ".jar";
    private static final String MOD_CLASSES_SUFFIX = ".modclasses";
    private static final String WARNINGS_SUFFIX = ".warnings";

    private final Path directory;
    private final String environmentKey;

    /**
     * Opens the cache.
     *
     * @param directory      cache folder
     * @param environmentKey hash of what is common to all jars: Lunar tables, rewrite version, Alloy runtime
     */
    public JarCache(Path directory, String environmentKey) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.environmentKey = Objects.requireNonNull(environmentKey, "environmentKey");
    }

    /**
     * Returns where the rewritten version of a jar lives in the cache, whether or not it exists yet.
     *
     * @param group  subfolder ({@code "forge"} or {@code "mods"})
     * @param source the original jar
     * @throws IOException if the original jar cannot be read
     */
    public Path locationFor(String group, Path source) throws IOException {
        String key = Sha256.ofText(this.environmentKey + "\n" + Sha256.ofFile(source)).substring(0, JarCache.KEY_LENGTH);
        String sourceName = source.getFileName().toString();
        String baseName = sourceName.endsWith(JarCache.JAR_SUFFIX)
                ? sourceName.substring(0, sourceName.length() - JarCache.JAR_SUFFIX.length())
                : sourceName;
        return this.directory.resolve(group).resolve(baseName + "-" + key + JarCache.JAR_SUFFIX);
    }

    /**
     * Reads back the {@code @Mod} classes noted next to a cached mod jar.
     *
     * @param cachedJar the cached jar
     * @return the class names, or empty if the jar or its note is missing (it must then be rebuilt)
     * @throws IOException if the note exists but cannot be read
     */
    public Optional<List<String>> readModClasses(Path cachedJar) throws IOException {
        Path note = JarCache.noteOf(cachedJar, JarCache.MOD_CLASSES_SUFFIX);
        if (!Files.isRegularFile(cachedJar) || !Files.isRegularFile(note)) {
            return Optional.empty();
        }
        return Optional.of(JarCache.readLines(note));
    }

    /**
     * Reads back the warnings noted next to a cached mod jar, so they are logged at every launch.
     *
     * @return the warnings; empty if there were none
     * @throws IOException if the note exists but cannot be read
     */
    public List<String> readWarnings(Path cachedJar) throws IOException {
        Path note = JarCache.noteOf(cachedJar, JarCache.WARNINGS_SUFFIX);
        return Files.isRegularFile(note) ? JarCache.readLines(note) : List.of();
    }

    /**
     * Notes the {@code @Mod} classes of a mod jar just written to the cache.
     *
     * @param cachedJar     the cached jar
     * @param modClassNames dotted names of its {@code @Mod} classes
     * @throws IOException if writing fails
     */
    public void writeModClasses(Path cachedJar, List<String> modClassNames) throws IOException {
        JarCache.writeNote(JarCache.noteOf(cachedJar, JarCache.MOD_CLASSES_SUFFIX), modClassNames);
    }

    /**
     * Notes the warnings of a mod jar; call it before {@link #writeModClasses}, whose note marks the
     * cache entry as complete.
     *
     * @throws IOException if writing fails
     */
    public void writeWarnings(Path cachedJar, List<String> warnings) throws IOException {
        JarCache.writeNote(JarCache.noteOf(cachedJar, JarCache.WARNINGS_SUFFIX), warnings);
    }

    private static List<String> readLines(Path note) throws IOException {
        return Files.readAllLines(note, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static void writeNote(Path note, List<String> lines) throws IOException {
        Path temporary = Files.createTempFile(note.getParent(), "note", ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        // The note appears only once complete: an interrupted launch leaves no half-written cache.
        Files.move(temporary, note, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path noteOf(Path cachedJar, String suffix) {
        return cachedJar.resolveSibling(cachedJar.getFileName() + suffix);
    }
}
