package dev.alloy.agent.forge;

import dev.alloy.agent.config.AlloyConfig;
import dev.alloy.agent.config.AlloyHome;
import dev.alloy.bridge.BridgeLogger;
import dev.alloy.remap.fetch.ForgeDistribution;
import dev.alloy.remap.fetch.VerifiedDownload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides the Forge 1.8.9 universal jar. Alloy does not redistribute it: it is downloaded once
 * from Forge's official Maven into {@code ~/.alloy/libraries} and checked against its SHA-256.
 *
 * @see ForgeDistribution
 */
public final class ForgeLibrary {

    private final AlloyHome home;
    private final AlloyConfig config;
    private final BridgeLogger logger;

    /**
     * Creates the accessor.
     *
     * @param home   Alloy folder
     * @param config Alloy settings
     * @param logger Alloy log
     */
    public ForgeLibrary(AlloyHome home, AlloyConfig config, BridgeLogger logger) {
        this.home = Objects.requireNonNull(home, "home");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Returns the Forge universal jar, downloading it if missing.
     *
     * @return the jar's path on disk
     * @throws IOException if the jar set in the settings is missing, or the download fails or gives an unexpected file
     */
    public Path locate() throws IOException {
        Optional<Path> configured = this.config.forgeUniversalJar();
        if (configured.isPresent()) {
            if (!Files.isRegularFile(configured.get())) {
                throw new IOException(AlloyConfig.FORGE_UNIVERSAL_JAR + " points to a missing file: " + configured.get());
            }
            return configured.get();
        }
        Path target = this.home.librariesDirectory().resolve(ForgeDistribution.FILE_NAME);
        VerifiedDownload download = ForgeDistribution.download();
        if (!download.matches(target)) {
            this.logger.info("Downloading " + ForgeDistribution.FILE_NAME + " from " + ForgeDistribution.DOWNLOAD_URI.getHost());
        }
        return download.ensure(target);
    }
}
