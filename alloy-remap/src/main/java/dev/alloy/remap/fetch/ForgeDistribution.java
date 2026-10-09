package dev.alloy.remap.fetch;

import java.net.URI;

/**
 * Where to find the Forge 1.8.9 "universal" jar and how to recognize it. Shared by the agent
 * (which downloads it on first game launch) and the developer tools (which need it to compile).
 */
public final class ForgeDistribution {

    /** Supported Forge version: the latest published for Minecraft 1.8.9. */
    public static final String VERSION = "1.8.9-11.15.1.2318-1.8.9";

    /** Short version, used to name the workspace's renamed jar. */
    public static final String SHORT_VERSION = "1.8.9-11.15.1.2318";

    /** File name, same as in Forge's Maven repository. */
    public static final String FILE_NAME = "forge-" + ForgeDistribution.VERSION + "-universal.jar";

    /** Official URL of the jar. */
    public static final URI DOWNLOAD_URI = URI.create("https://maven.minecraftforge.net/net/minecraftforge/forge/"
            + ForgeDistribution.VERSION + "/" + ForgeDistribution.FILE_NAME);

    /** SHA-256 of the official jar: any other file is refused. */
    public static final String SHA256 = "596512ad5f12f95d8a3170321543d4455d23b8fe649c68580c5f828fe74f6668";

    private ForgeDistribution() {
        // Constants class: no instances.
    }

    /**
     * Returns the verified download of the official jar.
     */
    public static VerifiedDownload download() {
        return new VerifiedDownload(ForgeDistribution.DOWNLOAD_URI, ForgeDistribution.SHA256);
    }
}
