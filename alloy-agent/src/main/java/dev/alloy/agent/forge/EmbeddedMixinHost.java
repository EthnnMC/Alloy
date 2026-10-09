package dev.alloy.agent.forge;

import dev.alloy.remap.io.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts the Mixin host embedded in the agent jar (module {@code alloy-mixin} with the Mixin
 * library). Like the Forge runtime it is shipped as a plain resource, because it must be loaded by
 * a class loader of its own.
 */
public final class EmbeddedMixinHost {

    /** Name of the resource in the agent jar. */
    public static final String RESOURCE_NAME = "/alloy-mixin-host.jar";

    private static final int HASH_LENGTH_IN_FILE_NAME = 16;

    private EmbeddedMixinHost() {
    }

    /**
     * Extracts the host jar into the given folder, unless it is already there.
     *
     * @param directory cache folder reserved for the Mixin host
     * @return the extracted jar
     * @throws IOException if the resource is missing (badly assembled agent jar) or writing fails
     */
    public static Path extract(Path directory) throws IOException {
        byte[] content;
        try (InputStream resource = EmbeddedMixinHost.class.getResourceAsStream(EmbeddedMixinHost.RESOURCE_NAME)) {
            if (resource == null) {
                throw new IOException("This Alloy jar does not contain " + EmbeddedMixinHost.RESOURCE_NAME
                        + ": it was built without the Mixin host");
            }
            content = resource.readAllBytes();
        }
        Files.createDirectories(directory);
        // The name contains the hash: a new Alloy version never overwrites a jar in use.
        String hash = Sha256.ofBytes(content).substring(0, EmbeddedMixinHost.HASH_LENGTH_IN_FILE_NAME);
        Path jar = directory.resolve("alloy-mixin-host-" + hash + ".jar");
        if (!Files.isRegularFile(jar) || Files.size(jar) != content.length) {
            Path temporary = Files.createTempFile(directory, "mixin-host", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        return jar;
    }
}
