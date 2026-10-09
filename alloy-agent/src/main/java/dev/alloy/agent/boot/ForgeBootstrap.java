package dev.alloy.agent.boot;

import dev.alloy.agent.config.AlloyConfig;
import dev.alloy.agent.config.AlloyHome;
import dev.alloy.agent.forge.EmbeddedRuntime;
import dev.alloy.agent.forge.ForgeLibrary;
import dev.alloy.agent.forge.JarCache;
import dev.alloy.agent.forge.ModDiscovery;
import dev.alloy.agent.forge.RuntimeJar;
import dev.alloy.agent.launch.GameFiles;
import dev.alloy.agent.launch.LaunchInfo;
import dev.alloy.agent.loader.AlloyClassLoader;
import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.GameEventSink;
import dev.alloy.bridge.GameHooks;
import dev.alloy.bridge.GameStartListener;
import dev.alloy.bridge.PreparedMod;
import dev.alloy.bridge.RuntimeContext;
import dev.alloy.hooks.GameClassTransformer;
import dev.alloy.remap.PreparedModJar;
import dev.alloy.remap.RemapToolkit;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Starts Forge support when the game begins ({@code Main.main}): locates the game files, rewrites
 * Forge and the mods to the game's names (or reuses the cache), creates the mod class loader, then
 * starts the Forge runtime in it and plugs in the hooks. Facade.
 * If a step fails the error is logged and the game starts normally without Forge mods.
 */
public final class ForgeBootstrap implements GameStartListener {

    /** Runtime start class of the {@code alloy-forge} module; called by reflection because it lives in another loader. */
    private static final String RUNTIME_CLASS = "dev.alloy.forge.ForgeRuntime";
    private static final String RUNTIME_START_METHOD = "start";

    private static final String FORGE_CACHE_GROUP = "forge";
    private static final String MODS_CACHE_GROUP = "mods";
    private static final String RUNTIME_CACHE_GROUP = "runtime";
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    private final AlloyHome home;
    private final AlloyConfig config;
    private final BridgeLogger logger;
    private final LaunchInfo launch;
    private final Instrumentation instrumentation;
    private final GameClassTransformer transformer;

    /**
     * Prepares the startup; nothing happens before {@link #onGameStart(String[])}.
     *
     * @param home            Alloy folder
     * @param config          Alloy settings
     * @param logger          Alloy log
     * @param launch          launch information
     * @param instrumentation JVM access received by the agent
     * @param transformer     the transformer that installs the hooks
     */
    public ForgeBootstrap(
            AlloyHome home,
            AlloyConfig config,
            BridgeLogger logger,
            LaunchInfo launch,
            Instrumentation instrumentation,
            GameClassTransformer transformer) {
        this.home = Objects.requireNonNull(home, "home");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.launch = Objects.requireNonNull(launch, "launch");
        this.instrumentation = Objects.requireNonNull(instrumentation, "instrumentation");
        this.transformer = Objects.requireNonNull(transformer, "transformer");
    }

    @Override
    public void onGameStart(String[] arguments) {
        long start = System.nanoTime();
        try {
            int modCount = this.start(arguments);
            long elapsed = (System.nanoTime() - start) / ForgeBootstrap.NANOSECONDS_PER_MILLISECOND;
            this.logger.info("Forge support ready: " + modCount + " mod file(s) loaded in " + elapsed + " ms");
        } catch (Throwable error) {
            // Throwable, not just Exception: a LinkageError in the runtime must not kill the game.
            this.logger.error("Forge support could not start: the game continues without Forge mods", error);
        }
    }

    private int start(String[] arguments) throws IOException, ReflectiveOperationException {
        ClassLoader gameLoader = this.transformer.gameLoader()
                .orElseThrow(() -> new IllegalStateException("The game class loader was not identified"));
        this.checkBridge(gameLoader);

        String version = LaunchInfo.FORGE_MINECRAFT_VERSION;
        Path mappingsJar = this.launch.lunarMappingsJar(version)
                .orElseThrow(() -> new IOException("Lunar's mapping jar is not on the class path"));
        Path vanillaJar = GameFiles.vanillaJar(gameLoader, version)
                .orElseThrow(() -> new IOException("The Minecraft " + version + " jar was not found"));
        Path gameDirectory = GameFiles.gameDirectory(arguments);
        this.logger.info("Game directory: " + gameDirectory + ", mods folder: " + this.home.modsDirectory());

        RuntimeJar runtime = EmbeddedRuntime.extract(this.home.cacheDirectory().resolve(ForgeBootstrap.RUNTIME_CACHE_GROUP));
        RemapToolkit toolkit = RemapToolkit.forLunar(mappingsJar, version, vanillaJar);
        JarCache cache = new JarCache(this.home.cacheDirectory(), toolkit.fingerprint() + "\n" + runtime.contentHash());

        Path forgeJar = this.prepareForge(toolkit, cache, runtime.overlaidClasses());
        List<PreparedMod> mods = this.prepareMods(toolkit, cache, runtime, version);

        ClassLoader modLoader = new AlloyClassLoader(ForgeBootstrap.classPath(runtime.jar(), forgeJar, mods), gameLoader);
        RuntimeContext context = new RuntimeContext(
                version, gameDirectory, this.home.root(), forgeJar, mods, this.config.asMap(), this.logger);
        GameEventSink sink = ForgeBootstrap.startRuntime(modLoader, context);

        GameHooks.install(sink);
        this.transformer.enableGameHooks();
        this.retransformLoadedGameClasses(gameLoader);
        return mods.size();
    }

    /** Checks that the game loader finds the agent's own {@code GameHooks} class, not a copy. */
    private void checkBridge(ClassLoader gameLoader) throws ClassNotFoundException {
        Class<?> seenByGame = Class.forName(GameHooks.class.getName(), false, gameLoader);
        if (seenByGame != GameHooks.class) {
            throw new IllegalStateException("The game class loader resolves a different copy of GameHooks");
        }
    }

    private Path prepareForge(RemapToolkit toolkit, JarCache cache, Set<String> overlaidClasses) throws IOException {
        Path universalJar = new ForgeLibrary(this.home, this.config, this.logger).locate();
        Path forgeJar = cache.locationFor(ForgeBootstrap.FORGE_CACHE_GROUP, universalJar);
        if (Files.isRegularFile(forgeJar)) {
            toolkit.useForgeJar(forgeJar);
            return forgeJar;
        }
        this.logger.info("Preparing Forge for the game's names (first launch only)...");
        Files.createDirectories(forgeJar.getParent());
        toolkit.prepareForge(universalJar, forgeJar, overlaidClasses);
        toolkit.lastForgeWarnings().forEach(warning -> this.logger.warn("Forge: " + warning));
        return forgeJar;
    }

    private List<PreparedMod> prepareMods(RemapToolkit toolkit, JarCache cache, RuntimeJar runtime, String version)
            throws IOException {
        List<PreparedMod> prepared = new ArrayList<>();
        for (Path modJar : ModDiscovery.findModJars(this.home, version)) {
            try {
                prepared.add(this.prepareMod(toolkit, cache, runtime, modJar));
            } catch (IOException | RuntimeException e) {
                // An unreadable mod must not stop the others from loading.
                this.logger.error("Cannot prepare " + modJar.getFileName() + ": this mod is skipped", e);
            }
        }
        return prepared;
    }

    private PreparedMod prepareMod(RemapToolkit toolkit, JarCache cache, RuntimeJar runtime, Path modJar)
            throws IOException {
        Path cachedJar = cache.locationFor(ForgeBootstrap.MODS_CACHE_GROUP, modJar);
        Optional<List<String>> knownModClasses = cache.readModClasses(cachedJar);
        if (knownModClasses.isPresent()) {
            return new PreparedMod(modJar, cachedJar, knownModClasses.get());
        }
        this.logger.info("Preparing mod " + modJar.getFileName() + "...");
        Files.createDirectories(cachedJar.getParent());
        PreparedModJar result = toolkit.prepareMod(modJar, cachedJar, runtime.shims());
        result.warnings().forEach(warning -> this.logger.warn(modJar.getFileName() + ": " + warning));
        if (result.modClassNames().isEmpty()) {
            this.logger.warn(modJar.getFileName() + " has no @Mod class: it is only added to the class path");
        }
        cache.writeModClasses(cachedJar, result.modClassNames());
        return new PreparedMod(modJar, cachedJar, result.modClassNames());
    }

    /** Order matters: runtime first (its classes replace Forge's), then Forge, then the mods. */
    private static URL[] classPath(Path runtimeJar, Path forgeJar, List<PreparedMod> mods) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(runtimeJar.toUri().toURL());
        urls.add(forgeJar.toUri().toURL());
        for (PreparedMod mod : mods) {
            urls.add(mod.loadableJar().toUri().toURL());
        }
        return urls.toArray(new URL[0]);
    }

    private static GameEventSink startRuntime(ClassLoader modLoader, RuntimeContext context)
            throws ReflectiveOperationException {
        Class<?> runtimeClass = Class.forName(ForgeBootstrap.RUNTIME_CLASS, true, modLoader);
        Object sink = runtimeClass.getMethod(ForgeBootstrap.RUNTIME_START_METHOD, RuntimeContext.class).invoke(null, context);
        return (GameEventSink) Objects.requireNonNull(sink, "The Forge runtime returned no event sink");
    }

    /**
     * Re-submits to the transformer any game class loaded before the hooks were enabled (normally
     * none, but a Weave mod may have loaded one).
     */
    private void retransformLoadedGameClasses(ClassLoader gameLoader) {
        Set<String> hookedClassNames = this.transformer.hookedClassNames();
        for (Class<?> loaded : this.instrumentation.getAllLoadedClasses()) {
            if (loaded.getClassLoader() != gameLoader || !hookedClassNames.contains(loaded.getName().replace('.', '/'))) {
                continue;
            }
            try {
                this.instrumentation.retransformClasses(loaded);
                this.logger.info("Hooks installed afterwards in the already loaded class " + loaded.getName());
            } catch (Exception | LinkageError e) {
                this.logger.error("Cannot install hooks in the already loaded class " + loaded.getName(), e);
            }
        }
    }
}
