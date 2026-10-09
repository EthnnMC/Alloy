package dev.alloy.agent.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Alloy's settings, read from {@code alloy.properties}. Immutable: read once at agent startup.
 * A missing file is created with the default values and one comment per setting.
 */
public final class AlloyConfig {

    /** Enables loading of Forge mods. */
    public static final String FORGE_ENABLED = "forge.enabled";

    /** Path of a Forge universal jar already on disk (avoids the download). */
    public static final String FORGE_UNIVERSAL_JAR = "forge.universalJar";

    /** Reloads resources after mod initialisation, as Forge does. */
    public static final String FORGE_REFRESH_RESOURCES = "forge.refreshResourcesAfterInit";

    /** Weave chaining mode: {@code auto}, {@code true} or {@code false}. */
    public static final String WEAVE_ENABLED = "weave.enabled";

    /** Path of the Weave agent jar; empty = the newest one in {@code ~/.weave/agents}. */
    public static final String WEAVE_AGENT = "weave.agent";

    /** Also writes DEBUG messages to the log. */
    public static final String LOG_DEBUG = "log.debug";

    private static final String DEFAULT_FILE_CONTENT = """
            # Alloy settings. Lines starting with '#' are comments.

            # Load Forge 1.8.9 mods from the 'mods' folder next to this file (true / false).
            forge.enabled=true

            # Path of an already downloaded Forge universal jar. Leave empty to let Alloy
            # download forge-1.8.9-11.15.1.2318-1.8.9-universal.jar from Forge's official Maven.
            forge.universalJar=

            # Reload the game resources once the mods are initialised, as Forge does.
            # 'false' makes the game start a little faster; some mods then miss their textures.
            forge.refreshResourcesAfterInit=true

            # Chain-load Weave: auto (only when a Weave mod exists for this Minecraft version),
            # true (whenever Weave is installed) or false (never).
            weave.enabled=auto

            # Path of the Weave agent jar. Leave empty to use the newest jar of ~/.weave/agents.
            weave.agent=

            # Write debug messages to logs/latest.log (true / false).
            log.debug=false
            """;

    private final Map<String, String> values;

    private AlloyConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /**
     * Reads the settings file, creating it first if it does not exist.
     *
     * @param file the {@code alloy.properties} file
     * @throws IOException if the file can be neither read nor created
     */
    public static AlloyConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, AlloyConfig.DEFAULT_FILE_CONTENT, StandardCharsets.UTF_8);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key).trim());
        }
        return new AlloyConfig(values);
    }

    /**
     * Builds settings directly from a map, without a file (tests).
     *
     * @param values key to value; absent keys take their default
     */
    public static AlloyConfig of(Map<String, String> values) {
        return new AlloyConfig(values);
    }

    /** Returns whether Forge mods must be loaded ({@code true} by default). */
    public boolean forgeEnabled() {
        return this.booleanValue(AlloyConfig.FORGE_ENABLED, true);
    }

    /** Returns the user-supplied Forge universal jar, or empty to let Alloy handle it. */
    public Optional<Path> forgeUniversalJar() {
        return this.pathValue(AlloyConfig.FORGE_UNIVERSAL_JAR);
    }

    /** Returns the Weave chaining mode ({@link WeaveMode#AUTO} by default). */
    public WeaveMode weaveMode() {
        return WeaveMode.parse(this.values.get(AlloyConfig.WEAVE_ENABLED));
    }

    /** Returns the user-supplied Weave agent jar, or empty to search {@code ~/.weave/agents}. */
    public Optional<Path> weaveAgent() {
        return this.pathValue(AlloyConfig.WEAVE_AGENT);
    }

    /** Returns whether debug messages are logged ({@code false} by default). */
    public boolean debug() {
        return this.booleanValue(AlloyConfig.LOG_DEBUG, false);
    }

    /** Returns all settings read, as an unmodifiable map, to pass them to the runtime loaded in the game. */
    public Map<String, String> asMap() {
        return this.values;
    }

    private boolean booleanValue(String key, boolean defaultValue) {
        String value = this.values.get(key);
        return value == null || value.isEmpty() ? defaultValue : Boolean.parseBoolean(value);
    }

    private Optional<Path> pathValue(String key) {
        String value = this.values.get(key);
        return value == null || value.isEmpty() ? Optional.empty() : Optional.of(Path.of(value));
    }
}
