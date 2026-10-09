package dev.alloy.agent.launch;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the game classes as Lunar really runs them. Lunar rewrites every class when it first
 * loads it and keeps the result in a cache file ({@code cache/<hash>/<hash>/bake.zip} next to its
 * jars); its class loader, asked for a class as a resource, answers with the untouched original
 * instead. Mixin needs the real ones to know what a class inherits and declares.
 */
public final class BakedClasses implements Closeable {

    private static final String CACHE_DIRECTORY = "cache";
    private static final String CACHE_FILE = "bake.zip";
    private static final String CLASS_SUFFIX = ".class";
    private static final String MAIN_CLASS_ENTRY = "net/minecraft/client/main/Main.class";

    private final Path file;
    private final ZipFile zip;

    private BakedClasses(Path file, ZipFile zip) {
        this.file = file;
        this.zip = zip;
    }

    /**
     * Finds the cache of the running game: the one whose main class is byte for byte the class
     * the game defined.
     *
     * @param classPath      the JVM class path; the caches sit next to Lunar's jars
     * @param mainClassBytes the game main class as the game defined it
     * @return the cache, or empty if none matches (first launch after a Lunar update)
     */
    public static Optional<BakedClasses> locate(List<Path> classPath, byte[] mainClassBytes) {
        Set<Path> directories = new LinkedHashSet<>();
        for (Path entry : classPath) {
            Path parent = entry.toAbsolutePath().getParent();
            if (parent != null) {
                directories.add(parent.resolve(BakedClasses.CACHE_DIRECTORY));
            }
        }
        List<Path> candidates = new ArrayList<>();
        directories.forEach(directory -> candidates.addAll(BakedClasses.cacheFilesIn(directory)));
        // Several game configurations may share the same main class: the cache used last is the likeliest.
        candidates.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
        for (Path candidate : candidates) {
            Optional<BakedClasses> opened = BakedClasses.openIfMatching(candidate, mainClassBytes);
            if (opened.isPresent()) {
                return opened;
            }
        }
        return Optional.empty();
    }

    /**
     * Reads a class.
     *
     * @param internalName internal class name
     * @return the class file, or {@code null} if Lunar has not cached this class
     */
    public byte[] read(String internalName) {
        // ZipFile is not safe for concurrent reads of the same instance.
        synchronized (this.zip) {
            ZipEntry entry = this.zip.getEntry(internalName + BakedClasses.CLASS_SUFFIX);
            if (entry == null) {
                return null;
            }
            try (InputStream input = this.zip.getInputStream(entry)) {
                return input.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }
    }

    /** Returns the cache file, for the log. */
    public Path file() {
        return this.file;
    }

    @Override
    public void close() throws IOException {
        this.zip.close();
    }

    /** Lists {@code <directory>/<hash>/<hash>/bake.zip}. */
    private static List<Path> cacheFilesIn(Path directory) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return files;
        }
        try (DirectoryStream<Path> first = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path outer : first) {
                try (DirectoryStream<Path> second = Files.newDirectoryStream(outer, Files::isDirectory)) {
                    for (Path inner : second) {
                        Path file = inner.resolve(BakedClasses.CACHE_FILE);
                        if (Files.isRegularFile(file)) {
                            files.add(file);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // An unreadable folder simply offers no cache.
        }
        return files;
    }

    private static Optional<BakedClasses> openIfMatching(Path file, byte[] mainClassBytes) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(file.toFile());
            ZipEntry main = zip.getEntry(BakedClasses.MAIN_CLASS_ENTRY);
            if (main != null) {
                try (InputStream input = zip.getInputStream(main)) {
                    if (Arrays.equals(input.readAllBytes(), mainClassBytes)) {
                        return Optional.of(new BakedClasses(file, zip));
                    }
                }
            }
            zip.close();
        } catch (IOException e) {
            // Being written by another game, or damaged: not usable.
            BakedClasses.closeQuietly(zip);
        }
        return Optional.empty();
    }

    private static void closeQuietly(ZipFile zip) {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException ignored) {
                // Nothing more can be done with it.
            }
        }
    }
}
