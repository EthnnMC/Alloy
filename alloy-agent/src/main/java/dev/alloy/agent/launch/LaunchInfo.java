package dev.alloy.agent.launch;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the agent knows about the current launch before the game starts. Everything is derived
 * from JVM properties (command line, class path) and from whether Lunar's bootstrap class is
 * present; no Lunar class is loaded.
 *
 * @param minecraftVersion game version requested from the launcher, for example {@code "1.8.9"}; empty if unknown
 * @param lunar            {@code true} if the game is launched by Lunar Client
 * @param classPath        entries of the JVM class path
 */
public record LaunchInfo(Optional<String> minecraftVersion, boolean lunar, List<Path> classPath) {

    /** System property that forces the game version when the command line does not give it. */
    public static final String VERSION_PROPERTY = "alloy.minecraft.version";

    /** The only game version for which Alloy can load Forge mods. */
    public static final String FORGE_MINECRAFT_VERSION = "1.8.9";

    private static final String GENESIS_RESOURCE = "com/moonsworth/lunar/genesis/Genesis.class";
    private static final Pattern VERSION_ARGUMENT = Pattern.compile("--version\\s+(\\S+)");
    private static final String MAPPINGS_JAR_PREFIX = "lunar-platform-mappings-";

    /** Copies the class path list to keep the record immutable. */
    public LaunchInfo {
        classPath = List.copyOf(classPath);
    }

    /** Observes the current JVM. */
    public static LaunchInfo detect() {
        String forced = System.getProperty(LaunchInfo.VERSION_PROPERTY);
        Optional<String> version = forced != null && !forced.isBlank()
                ? Optional.of(forced.trim())
                : LaunchInfo.versionIn(System.getProperty("sun.java.command", ""));
        boolean lunar = ClassLoader.getSystemResource(LaunchInfo.GENESIS_RESOURCE) != null;
        return new LaunchInfo(version, lunar, LaunchInfo.parseClassPath(System.getProperty("java.class.path", "")));
    }

    /**
     * Finds the {@code --version <value>} argument in a command line.
     *
     * @return the version, or empty if the argument is absent
     */
    public static Optional<String> versionIn(String commandLine) {
        Matcher matcher = LaunchInfo.VERSION_ARGUMENT.matcher(commandLine);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * Splits a class path into its non-empty entries, in order.
     *
     * @param classPath value of {@code java.class.path}
     */
    public static List<Path> parseClassPath(String classPath) {
        List<Path> entries = new ArrayList<>();
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry));
            }
        }
        return entries;
    }

    /** Returns whether Alloy loads Forge mods for this launch: Lunar Client on 1.8.9 (Lunar sometimes writes {@code 1.8}). */
    public boolean supportsForge() {
        return this.lunar && this.minecraftVersion.map(LaunchInfo::isForgeVersion).orElse(false);
    }

    /**
     * Finds Lunar's mappings jar for a game version, named like
     * {@code lunar-platform-mappings-v1_8.jar} on the class path.
     *
     * @param version game version, for example {@code "1.8.9"}
     * @return the jar, or empty if it is not on the class path
     */
    public Optional<Path> lunarMappingsJar(String version) {
        String expectedPrefix = LaunchInfo.MAPPINGS_JAR_PREFIX + LaunchInfo.versionToken(version);
        for (Path entry : this.classPath) {
            Path fileName = entry.getFileName();
            if (fileName != null && fileName.toString().startsWith(expectedPrefix)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Turns {@code "1.8.9"} into {@code "v1_8"}, the way Lunar names its per-version files. */
    static String versionToken(String version) {
        String[] parts = version.split("\\.");
        String minor = parts.length > 1 ? parts[1] : "0";
        return "v" + parts[0] + "_" + minor;
    }

    private static boolean isForgeVersion(String version) {
        return version.equals(LaunchInfo.FORGE_MINECRAFT_VERSION) || version.equals("1.8");
    }
}
