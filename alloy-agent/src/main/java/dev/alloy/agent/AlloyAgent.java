package dev.alloy.agent;

import dev.alloy.agent.boot.ForgeBootstrap;
import dev.alloy.agent.config.AlloyConfig;
import dev.alloy.agent.config.AlloyHome;
import dev.alloy.agent.launch.LaunchInfo;
import dev.alloy.agent.loader.AlloyClassLoader;
import dev.alloy.agent.log.AlloyLog;
import dev.alloy.agent.weave.WeaveChainLoader;
import dev.alloy.bridge.GameHooks;
import dev.alloy.hooks.GameClassTransformer;
import dev.alloy.hooks.HookCatalog;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Entry point of Alloy: the JVM calls {@link #premain(String, Instrumentation)} before the game's
 * {@code main}. It only installs what acts later: the class transformer, the game-start listener
 * ({@link ForgeBootstrap}) and, if installed, Weave.
 */
public final class AlloyAgent {

    private static final String WEAVE_HOME_DIRECTORY = ".weave";

    private final Instrumentation instrumentation;

    private AlloyAgent(Instrumentation instrumentation) {
        this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
    }

    /**
     * Called by the JVM at startup. Every error is caught so Alloy never prevents the game from starting.
     *
     * @param arguments       text after {@code =} in the {@code -javaagent} option (unused)
     * @param instrumentation JVM access reserved for agents
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        try {
            new AlloyAgent(instrumentation).start();
        } catch (Throwable error) {
            // Last resort: Alloy's log may not even exist yet.
            System.err.println("[Alloy] Could not start; the game continues without Alloy: " + error);
            error.printStackTrace(System.err);
        }
    }

    private void start() throws IOException {
        AlloyHome home = AlloyHome.resolve();
        home.createDirectories();
        AlloyConfig config = AlloyConfig.load(home.configFile());
        AlloyLog log = AlloyLog.open(home.logsDirectory(), config.debug());
        GameHooks.setLogger(log);

        LaunchInfo launch = LaunchInfo.detect();
        log.info("Alloy attached (Minecraft " + launch.minecraftVersion().orElse("unknown")
                + ", Lunar Client: " + launch.lunar() + ", home: " + home + ")");

        if (!config.forgeEnabled()) {
            log.info("Forge support is disabled in " + home.configFile().getFileName());
        } else if (!launch.supportsForge()) {
            log.info("Forge mods are only loaded on Lunar Client " + LaunchInfo.FORGE_MINECRAFT_VERSION
                    + ": nothing to do for this launch");
        } else {
            this.installForgeSupport(home, config, log, launch);
        }

        Path weaveHome = Path.of(System.getProperty("user.home"), AlloyAgent.WEAVE_HOME_DIRECTORY);
        List<String> jvmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        new WeaveChainLoader(config, log, weaveHome).chain(this.instrumentation, launch.minecraftVersion(), jvmArguments);
    }

    private void installForgeSupport(AlloyHome home, AlloyConfig config, AlloyLog log, LaunchInfo launch) {
        GameClassTransformer transformer = new GameClassTransformer(HookCatalog.forgeDefaults(), log);
        // Dry run: the JVM loads what the transformer needs now, not in the middle of defining a game class.
        transformer.warmUp();
        // Weave rewrites every URLClassLoader subclass loaded after it, so ours must load before.
        AlloyAgent.loadNow(AlloyClassLoader.class);

        // true: runs after Weave's transformers and can be replayed on an already loaded class.
        this.instrumentation.addTransformer(transformer, true);
        GameHooks.setGameStartListener(new ForgeBootstrap(home, config, log, launch, this.instrumentation, transformer));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> AlloyAgent.logSessionSummary(log, transformer), "Alloy summary"));
        log.info("Forge support installed: waiting for the game to start");
    }

    /** Forces loading and initialisation of an agent class. */
    private static void loadNow(Class<?> type) {
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            // Cannot happen: the class is already known to the caller.
            throw new IllegalStateException(e);
        }
    }

    /** At game exit: which hooks were installed and which fired. */
    private static void logSessionSummary(AlloyLog log, GameClassTransformer transformer) {
        log.info(transformer.report().summary());
        log.debug(transformer.report().toString());
        log.debug("Hooks that fired at least once: " + GameHooks.firedHooks());
    }
}
