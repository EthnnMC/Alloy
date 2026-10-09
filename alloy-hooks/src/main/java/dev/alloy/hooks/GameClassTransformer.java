package dev.alloy.hooks;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.ClassRewriter;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Alloy's class transformer: modifies the bytecode of the classes it cares about as the JVM
 * defines them. In order, it installs the bridge in Lunar's class loader
 * ({@link GameLoaderBridgePatch}), injects the startup call at the start of {@code Main.main}
 * (remembering the loader that defined it as the game loader), and, once the Forge runtime is
 * ready ({@link #enableGameHooks()}), installs the catalog hooks in Minecraft classes. Only
 * classes of the game loader are modified, since several loaders can define the same name.
 *
 * <p>It runs inside JVM class loading, so it never throws (errors are logged and the class is
 * left unchanged), never loads a game class (bytes only), takes no lock (volatile fields and
 * concurrent collections, since classes load on several threads), and logs only the two
 * one-time startup steps and errors; hook results go to {@link #report()}.</p>
 */
public final class GameClassTransformer implements ClassFileTransformer {

    private final HookCatalog catalog;
    private final BridgeLogger logger;
    private final HookReport report;
    private final ClassPatcher patcher = new ClassPatcher();
    private final GameLoaderBridgePatch bridgePatch = new GameLoaderBridgePatch();

    /** Classes that get hooks on activation; computed once, never modified. */
    private final Set<String> hookedClassNames;

    /** Loader classes in which the bridge is installed. */
    private final Set<LoaderClassKey> bridgedLoaderClasses = ConcurrentHashMap.newKeySet();

    /** The game loader; empty until {@code Main} is defined. */
    private final AtomicReference<ClassLoader> gameLoader = new AtomicReference<>();

    /** Set once the "game without bridge" problem is logged, so it is reported only once. */
    private final AtomicBoolean missingBridgeReported = new AtomicBoolean();

    /** True once the Forge runtime can receive game events. */
    private volatile boolean gameHooksEnabled;

    /** Rewrites the game classes the mods' mixins target; {@code null} until {@link #useModRewriter}. */
    private volatile ClassRewriter modRewriter;

    /** Internal names of the classes to give to {@link #modRewriter}. */
    private volatile Set<String> modTargets = Set.of();

    /**
     * Classes as the mixins left them, by internal name. A retransformation starts again from the
     * game's bytes and must end with the same members, so the first result is reused.
     */
    private final ConcurrentHashMap<String, byte[]> mixedClasses = new ConcurrentHashMap<>();

    /** {@code Main} as the game defined it, kept to recognise Lunar's class cache; {@code null} until seen. */
    private volatile byte[] mainClassBytes;

    /**
     * Creates the transformer; it must still be registered with
     * {@code Instrumentation.addTransformer(transformer, true)}.
     */
    public GameClassTransformer(HookCatalog catalog, BridgeLogger logger) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.report = new HookReport(catalog.hookIds());
        this.hookedClassNames = GameClassTransformer.classesHookedOnActivation(catalog);
    }

    /**
     * Called by the JVM for every defined or retransformed class.
     *
     * @param loader          defining loader; {@code null} for JDK classes
     * @param className       internal class name; may be {@code null}
     * @param classfileBuffer the presented bytecode, not to be modified
     * @return the modified bytecode, or {@code null} to leave the class unchanged
     */
    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        try {
            return this.transformOrNull(loader, className, classBeingRedefined != null, classfileBuffer);
        } catch (Throwable error) {
            this.logFailure(className, error);
            return null;
        }
    }

    /**
     * Rehearses every injection strategy and the loader bridge once on synthetic classes,
     * leaving the transformer state untouched. Call it in {@code premain}, before Lunar loads its
     * classes, so the JVM loads what the transformer needs now rather than in the middle of
     * defining a game class. A failure is logged and does not stop the game.
     */
    public void warmUp() {
        try {
            HookReport rehearsal = TransformerWarmUp.run(this.patcher, this.bridgePatch);
            if (!rehearsal.isComplete()) {
                this.logger.error("Transformer warm-up is incomplete. " + rehearsal, null);
            }
        } catch (RuntimeException error) {
            this.logger.error("Transformer warm-up failed", error);
        }
    }

    /**
     * Installs the rewriter that applies the mods' mixins. Unlike hooks, mixins add members, so
     * they only reach classes defined after this call.
     *
     * @param rewriter the Mixin host
     * @param targets  internal names of the game classes the mixins target
     */
    public void useModRewriter(ClassRewriter rewriter, Set<String> targets) {
        this.modTargets = Set.copyOf(targets);
        this.modRewriter = Objects.requireNonNull(rewriter, "rewriter");
    }

    /** Returns the game main class exactly as the game defined it, or empty before the game starts. */
    public Optional<byte[]> gameMainClassBytes() {
        return Optional.ofNullable(this.mainClassBytes);
    }

    /**
     * Allows hooks to be installed in Minecraft classes. Call once the Forge runtime is
     * installed; already loaded classes must then be retransformed (see {@link #hookedClassNames()}).
     */
    public void enableGameHooks() {
        this.gameHooksEnabled = true;
    }

    /**
     * Returns the game class loader (the one that defined {@code net.minecraft.client.main.Main}
     * and holds the bridge), or empty if the game has not started or the bridge is missing.
     */
    public Optional<ClassLoader> gameLoader() {
        return Optional.ofNullable(this.gameLoader.get());
    }

    /** Returns whether the {@code GameHooks} bridge was installed in a Lunar loader. */
    public boolean isLoaderBridgeInstalled() {
        return !this.bridgedLoaderClasses.isEmpty();
    }

    /**
     * Returns the classes to retransform after {@link #enableGameHooks()} if already loaded, as
     * an unmodifiable set of internal names. {@code Main} is not included: it is injected as it loads.
     */
    public Set<String> hookedClassNames() {
        return this.hookedClassNames;
    }

    /** Returns the live, thread-safe report; it fills in as game classes load. */
    public HookReport report() {
        return this.report;
    }

    private byte[] transformOrNull(ClassLoader loader, String className, boolean retransformed, byte[] classBytes) {
        if (loader == null || className == null || classBytes == null) {
            return null;
        }
        if (this.bridgePatch.isCandidate(className)) {
            return this.installLoaderBridge(loader, className, classBytes);
        }
        List<Hook> hooks = this.catalog.hooksFor(className);
        boolean mixinTarget = this.modTargets.contains(className);
        if (hooks.isEmpty() && !mixinTarget && !this.mixedClasses.containsKey(className)
                || !this.isGameLoader(loader, className)) {
            return null;
        }
        if (GameClasses.MAIN.equals(className) && this.mainClassBytes == null) {
            this.mainClassBytes = classBytes.clone();
        }
        // Mixins first: the hooks must also find their place in what the mods changed.
        byte[] mixed = this.mixedClasses.get(className);
        if (mixed == null && mixinTarget && !retransformed) {
            mixed = this.modRewriter.rewrite(className.replace('/', '.'), classBytes);
            if (mixed != null) {
                this.mixedClasses.put(className, mixed);
                this.logger.info("Mixins applied to " + className.replace('/', '.'));
            }
        }
        byte[] current = mixed == null ? classBytes : mixed;
        List<Hook> activeHooks = this.activeAmong(hooks);
        if (activeHooks.isEmpty()) {
            return mixed;
        }
        return this.patcher.patch(current, activeHooks, this.report).orElse(mixed);
    }

    private byte[] installLoaderBridge(ClassLoader definingLoader, String className, byte[] classBytes) {
        Optional<byte[]> patched = this.bridgePatch.patch(classBytes);
        if (patched.isEmpty()) {
            return null;
        }
        if (this.bridgedLoaderClasses.add(new LoaderClassKey(className, definingLoader))) {
            this.logger.info("Game loader bridge installed in " + className);
        }
        return patched.get();
    }

    /**
     * Returns whether a catalog class is defined by the game loader. Until one is known, the
     * first bridged loader that defines {@code Main} becomes it.
     */
    private boolean isGameLoader(ClassLoader loader, String className) {
        ClassLoader known = this.gameLoader.get();
        if (known != null) {
            return loader == known;
        }
        if (!GameClasses.MAIN.equals(className)) {
            return false;
        }
        if (!this.bridgedLoaderClasses.contains(LoaderClassKey.of(loader.getClass()))) {
            // Without the bridge a GameHooks call would crash the game, so nothing is injected.
            if (this.missingBridgeReported.compareAndSet(false, true)) {
                this.logger.warn("The game main class is defined by " + loader.getClass().getName()
                        + ", a class loader without the Alloy bridge: Forge support stays off");
            }
            return false;
        }
        if (this.gameLoader.compareAndSet(null, loader)) {
            this.logger.info("Game class loader found: " + loader.getClass().getName());
        }
        return this.gameLoader.get() == loader;
    }

    /** Before activation, only the always-active hooks are installed. */
    private List<Hook> activeAmong(List<Hook> hooks) {
        if (this.gameHooksEnabled) {
            return hooks;
        }
        List<Hook> active = new ArrayList<>();
        for (Hook hook : hooks) {
            if (hook.alwaysActive()) {
                active.add(hook);
            }
        }
        return active;
    }

    private void logFailure(String className, Throwable error) {
        try {
            this.logger.error("Cannot transform " + className + ": the class is left unchanged", error);
        } catch (Throwable loggingFailure) {
            // The logger itself failed. There is no safe way to report it, and nothing may reach the
            // JVM mid class load: the class is simply left unchanged.
            error.addSuppressed(loggingFailure);
        }
    }

    private static Set<String> classesHookedOnActivation(HookCatalog catalog) {
        Set<String> classNames = new LinkedHashSet<>();
        for (Hook hook : catalog.hooks()) {
            if (!hook.alwaysActive()) {
                classNames.add(hook.className());
            }
        }
        return Collections.unmodifiableSet(classNames);
    }
}
