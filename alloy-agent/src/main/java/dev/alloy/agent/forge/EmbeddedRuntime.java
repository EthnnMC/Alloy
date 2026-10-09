package dev.alloy.agent.forge;

import dev.alloy.remap.MemberShimTable;
import dev.alloy.remap.io.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts the Forge runtime embedded in the agent jar. That runtime runs inside the game, next
 * to the mods under Lunar's loader, so it cannot be loaded by the system loader: it is shipped as
 * a plain resource and copied into the cache so a class loader can open it.
 */
public final class EmbeddedRuntime {

    /** Name of the resource in the agent jar. */
    public static final String RESOURCE_NAME = "/alloy-forge-runtime.jar";

    /** Runtime entry that lists the Forge-added members to replace. */
    public static final String SHIM_TABLE_ENTRY = "META-INF/alloy/member-shims.tsv";

    private static final String FORGE_PACKAGE = "net/minecraftforge/";
    private static final String CLASS_SUFFIX = ".class";
    private static final int HASH_LENGTH_IN_FILE_NAME = 16;

    private EmbeddedRuntime() {
    }

    /**
     * Extracts the embedded runtime into the given folder.
     *
     * @param runtimeDirectory cache folder reserved for the runtime
     * @return the extracted jar and its description
     * @throws IOException if the resource is missing (badly assembled agent jar) or writing fails
     */
    public static RuntimeJar extract(Path runtimeDirectory) throws IOException {
        byte[] content;
        try (InputStream resource = EmbeddedRuntime.class.getResourceAsStream(EmbeddedRuntime.RESOURCE_NAME)) {
            if (resource == null) {
                throw new IOException("This Alloy jar does not contain " + EmbeddedRuntime.RESOURCE_NAME
                        + ": it was built without the Forge runtime");
            }
            content = resource.readAllBytes();
        }
        return EmbeddedRuntime.install(content, runtimeDirectory);
    }

    /**
     * Writes a runtime jar's content into the cache and describes it.
     *
     * @param content          bytes of the jar
     * @param runtimeDirectory cache folder reserved for the runtime
     * @throws IOException if writing or re-reading fails
     */
    static RuntimeJar install(byte[] content, Path runtimeDirectory) throws IOException {
        Files.createDirectories(runtimeDirectory);
        String hash = Sha256.ofBytes(content);
        // The name contains the hash: a new Alloy version never overwrites a jar in use.
        Path jar = runtimeDirectory.resolve(
                "alloy-forge-runtime-" + hash.substring(0, EmbeddedRuntime.HASH_LENGTH_IN_FILE_NAME) + ".jar");
        if (!Files.isRegularFile(jar) || Files.size(jar) != content.length) {
            Path temporary = Files.createTempFile(runtimeDirectory, "runtime", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        return EmbeddedRuntime.describe(jar, hash);
    }

    private static RuntimeJar describe(Path jar, String hash) throws IOException {
        Set<String> overlaidClasses = new LinkedHashSet<>();
        MemberShimTable shims = MemberShimTable.empty();
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            for (JarEntry entry : Collections.list(entries)) {
                String name = entry.getName();
                if (name.startsWith(EmbeddedRuntime.FORGE_PACKAGE) && name.endsWith(EmbeddedRuntime.CLASS_SUFFIX)) {
                    // A runtime class under net/minecraftforge replaces Forge's own.
                    overlaidClasses.add(name.substring(0, name.length() - EmbeddedRuntime.CLASS_SUFFIX.length()));
                }
            }
            JarEntry shimTable = file.getJarEntry(EmbeddedRuntime.SHIM_TABLE_ENTRY);
            if (shimTable != null) {
                try (InputStream input = file.getInputStream(shimTable)) {
                    shims = MemberShimTable.read(input);
                }
            }
        }
        return new RuntimeJar(jar, hash, overlaidClasses, shims);
    }
}
