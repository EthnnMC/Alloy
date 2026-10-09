package dev.alloy.agent.weave;

import dev.alloy.agent.config.AlloyConfig;
import dev.alloy.agent.config.WeaveMode;
import dev.alloy.bridge.BridgeLogger;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Starts the user's installed Weave agent from Alloy, so one {@code -javaagent} serves both.
 * Weave stops the JVM when it dislikes something, so everything checkable is verified first
 * and Alloy gives up at the slightest doubt: a game without Weave beats one that does not start.
 */
public final class WeaveChainLoader {

    /** Property set by Weave once attached. */
    static final String ATTACHED_PROPERTY = "weave.game.info";

    /** Property that tells Weave the game version. */
    static final String VERSION_PROPERTY = "weave.environment.version";

    private static final String WEAVE_PACKAGE_PREFIX = "net.weavemc.";
    private static final String AGENT_ARGUMENT_PREFIX = "-javaagent:";
    private static final String MOD_DESCRIPTOR = "weave.mod.json";
    private static final String JAR_SUFFIX = ".jar";

    private final AlloyConfig config;
    private final BridgeLogger logger;
    private final Path weaveHome;

    /** Other folders that may hold the Weave agent, tried after {@link #weaveHome}. */
    private final List<Path> otherAgentHomes;

    /**
     * Creates the chain loader.
     *
     * @param config    Alloy settings
     * @param logger    Alloy log
     * @param weaveHome Weave folder ({@code ~/.weave}), where Weave reads its mods
     */
    public WeaveChainLoader(AlloyConfig config, BridgeLogger logger, Path weaveHome) {
        this(config, logger, weaveHome, List.of());
    }

    /**
     * Creates the chain loader for an installation where the Weave agent may sit outside the user
     * folder. Weave's guide puts a second copy of {@code .weave} at the root of the drive when the
     * user folder has a space in its path, since the agent path is typed as a JVM argument.
     *
     * @param config          Alloy settings
     * @param logger          Alloy log
     * @param weaveHome       Weave folder ({@code ~/.weave}), where Weave reads its mods
     * @param otherAgentHomes other Weave folders whose {@code agents} folder is searched too
     */
    public WeaveChainLoader(AlloyConfig config, BridgeLogger logger, Path weaveHome, List<Path> otherAgentHomes) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.weaveHome = Objects.requireNonNull(weaveHome, "weaveHome");
        this.otherAgentHomes = List.copyOf(otherAgentHomes);
    }

    /**
     * Chains Weave if the settings ask for it and all checks pass. Never throws: the reason
     * for giving up is written to the log.
     *
     * @param instrumentation  the JVM access received by the agent
     * @param minecraftVersion game version, if known
     * @param jvmArguments     JVM arguments (to spot a Weave launched separately)
     * @return {@code true} if Weave was started
     */
    public boolean chain(Instrumentation instrumentation, Optional<String> minecraftVersion, List<String> jvmArguments) {
        try {
            Optional<WeavePlan> plan = this.plan(minecraftVersion, jvmArguments);
            if (plan.isEmpty()) {
                return false;
            }
            this.start(instrumentation, plan.get(), minecraftVersion.orElseThrow());
            return true;
        } catch (InvocationTargetException e) {
            this.logger.error("Weave failed while starting: the game continues without Weave", e.getCause());
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            this.logger.error("Weave could not be chained: the game continues without Weave", e);
        }
        return false;
    }

    /**
     * Decides whether to chain Weave, without changing anything.
     *
     * @return the jar and class to start, or empty if Weave must not be chained
     * @throws IOException if a jar to inspect is unreadable
     */
    Optional<WeavePlan> plan(Optional<String> minecraftVersion, List<String> jvmArguments) throws IOException {
        WeaveMode mode = this.config.weaveMode();
        if (mode == WeaveMode.NEVER) {
            return Optional.empty();
        }
        Optional<Path> agentJar = this.findAgentJar();
        if (agentJar.isEmpty()) {
            if (mode == WeaveMode.ALWAYS) {
                this.logger.warn("weave.enabled=true but no Weave agent jar was found in "
                        + this.weaveHome.resolve("agents") + " nor in " + this.otherAgentHomes);
            }
            return Optional.empty();
        }
        if (minecraftVersion.isEmpty()) {
            this.logger.warn("The Minecraft version is unknown: Weave is not chained");
            return Optional.empty();
        }
        Optional<String> premainClass = WeaveChainLoader.premainClassOf(agentJar.get());
        if (premainClass.isEmpty() || !premainClass.get().startsWith(WeaveChainLoader.WEAVE_PACKAGE_PREFIX)) {
            this.logger.warn(agentJar.get() + " does not look like a Weave agent: Weave is not chained");
            return Optional.empty();
        }
        if (System.getProperties().get(WeaveChainLoader.ATTACHED_PROPERTY) != null
                || WeaveChainLoader.hasOwnWeaveAgent(jvmArguments)) {
            this.logger.info("Weave is already attached through its own -javaagent: nothing to chain");
            return Optional.empty();
        }
        List<Path> mods = this.weaveModJars(minecraftVersion.get());
        for (Path mod : mods) {
            if (!WeaveChainLoader.isWeaveMod(mod)) {
                this.logger.warn(mod + " is not a Weave mod (no " + WeaveChainLoader.MOD_DESCRIPTOR
                        + "). Weave would stop the game, so it is not chained. Forge mods belong in Alloy's mods folder.");
                return Optional.empty();
            }
        }
        if (mode == WeaveMode.AUTO && mods.isEmpty()) {
            this.logger.info("No Weave mod for Minecraft " + minecraftVersion.get() + ": Weave is not chained");
            return Optional.empty();
        }
        return Optional.of(new WeavePlan(agentJar.get(), premainClass.get(), mods.size()));
    }

    private void start(Instrumentation instrumentation, WeavePlan plan, String minecraftVersion)
            throws IOException, ReflectiveOperationException {
        if (System.getProperty(WeaveChainLoader.VERSION_PROPERTY) == null) {
            System.setProperty(WeaveChainLoader.VERSION_PROPERTY, minecraftVersion);
        }
        this.logger.info("Chaining Weave: " + plan.agentJar() + " (" + plan.modCount() + " Weave mod(s))");
        // The JarFile stays open on purpose: the system loader reads its classes until the end.
        instrumentation.appendToSystemClassLoaderSearch(new JarFile(plan.agentJar().toFile()));
        Class<?> weaveAgent = Class.forName(plan.premainClass(), true, ClassLoader.getSystemClassLoader());
        weaveAgent.getMethod("premain", String.class, Instrumentation.class).invoke(null, null, instrumentation);
    }

    /**
     * The jar from the settings, otherwise the newest jar of the first Weave folder that has an
     * agent: {@code ~/.weave/agents}, then the other folders.
     */
    private Optional<Path> findAgentJar() throws IOException {
        Optional<Path> configured = this.config.weaveAgent();
        if (configured.isPresent()) {
            return configured.filter(Files::isRegularFile);
        }
        List<Path> homes = new ArrayList<>(List.of(this.weaveHome));
        homes.addAll(this.otherAgentHomes);
        for (Path home : homes) {
            List<Path> jars = WeaveChainLoader.jarsIn(home.resolve("agents"));
            if (!jars.isEmpty()) {
                return jars.stream().max(Comparator.comparingLong(WeaveChainLoader::lastModified));
            }
        }
        return Optional.empty();
    }

    /** The mods Weave would load: those of {@code mods/} and of {@code mods/<version>/}. */
    private List<Path> weaveModJars(String minecraftVersion) throws IOException {
        Path modsDirectory = this.weaveHome.resolve("mods");
        List<Path> mods = new ArrayList<>(WeaveChainLoader.jarsIn(modsDirectory));
        mods.addAll(WeaveChainLoader.jarsIn(modsDirectory.resolve(minecraftVersion)));
        return mods;
    }

    private static List<Path> jarsIn(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(WeaveChainLoader.JAR_SUFFIX))
                    .sorted()
                    .toList();
        }
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            // File vanished between listing and reading its date: it simply sorts last.
            return Long.MIN_VALUE;
        }
    }

    private static boolean isWeaveMod(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getEntry(WeaveChainLoader.MOD_DESCRIPTOR) != null;
        }
    }

    private static Optional<String> premainClassOf(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            return manifest == null
                    ? Optional.empty()
                    : Optional.ofNullable(manifest.getMainAttributes().getValue("Premain-Class"));
        }
    }

    /** True if the command line already has a {@code -javaagent} that is Weave itself. */
    private static boolean hasOwnWeaveAgent(List<String> jvmArguments) {
        for (String argument : jvmArguments) {
            if (!argument.startsWith(WeaveChainLoader.AGENT_ARGUMENT_PREFIX)) {
                continue;
            }
            String jarPath = argument.substring(WeaveChainLoader.AGENT_ARGUMENT_PREFIX.length());
            int optionsStart = jarPath.indexOf('=');
            Path jar = Path.of(optionsStart < 0 ? jarPath : jarPath.substring(0, optionsStart));
            try {
                Optional<String> premainClass = Files.isRegularFile(jar) ? WeaveChainLoader.premainClassOf(jar) : Optional.empty();
                if (premainClass.isPresent() && premainClass.get().startsWith(WeaveChainLoader.WEAVE_PACKAGE_PREFIX)) {
                    return true;
                }
            } catch (IOException | RuntimeException e) {
                // Unreadable argument (odd path, damaged jar): not a usable Weave anyway.
                continue;
            }
        }
        return false;
    }
}
