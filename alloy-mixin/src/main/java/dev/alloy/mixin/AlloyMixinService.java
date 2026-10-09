package dev.alloy.mixin;

import dev.alloy.bridge.MixinSetup;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

/**
 * Mixin's view of the game: where to read classes and resources, how to load mod classes, where to
 * log. Mixin was written for other launchers and asks for all this through a service; this is the
 * one for Lunar Client. Mixin creates it by name, so what the agent provides arrives through
 * {@link #install(MixinSetup)}.
 */
public final class AlloyMixinService
        implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {

    private static final String NAME = "Alloy";
    private static final String OWN_PACKAGE = "dev.alloy.mixin.";
    private static final String DEFAULT_PLATFORM_AGENT = "org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault";

    private static volatile MixinSetup setup;
    private static volatile IMixinTransformer transformer;

    private final ReEntranceLock lock = new ReEntranceLock(1);
    private final Map<String, ILogger> loggers = new ConcurrentHashMap<>();

    /** Gives the service what the agent provides; call it before Mixin starts. */
    static void install(MixinSetup mixinSetup) {
        AlloyMixinService.setup = mixinSetup;
    }

    /** Returns the transformer Mixin handed over while starting; {@code null} before that. */
    static IMixinTransformer transformer() {
        return AlloyMixinService.transformer;
    }

    // ------------------------------------------------------------------ service

    @Override
    public String getName() {
        return AlloyMixinService.NAME;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory factory) {
            AlloyMixinService.transformer = factory.createTransformer();
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void beginPhase() {
    }

    @Override
    public void checkEnv(Object bootSource) {
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return this.lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of(AlloyMixinService.DEFAULT_PLATFORM_AGENT);
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(AlloyMixinService.NAME);
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return List.of();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return AlloyMixinService.setup.resources().apply(name);
    }

    @Override
    public String getSideName() {
        return "CLIENT";
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_6;
    }

    /** Lunar compiles the classes it rewrites for Java 17. */
    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_18;
    }

    @Override
    public ILogger getLogger(String name) {
        return this.loggers.computeIfAbsent(name, id -> new BridgeLoggerAdapter(id, AlloyMixinService.setup.logger()));
    }

    // ------------------------------------------------------------------ classes

    @Override
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return this.findClass(name, true);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        // The host's own helpers (the error handler) are named to Mixin like mod classes.
        ClassLoader loader = name.startsWith(AlloyMixinService.OWN_PACKAGE)
                ? AlloyMixinService.class.getClassLoader()
                : AlloyMixinService.setup.modLoader();
        return Class.forName(name, initialize, loader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, AlloyMixinService.class.getClassLoader());
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, true);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        byte[] classBytes = AlloyMixinService.setup.classBytes().apply(name.replace('.', '/'));
        if (classBytes == null) {
            throw new ClassNotFoundException(name);
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(classBytes).accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }

    @Override
    public void registerInvalidClass(String className) {
    }

    @Override
    public boolean isClassLoaded(String className) {
        return AlloyMixinService.setup.loadedEarly().test(className);
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }

    // ------------------------------------------------------------------ other transformers: none

    @Override
    public Collection<ITransformer> getTransformers() {
        return List.of();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return List.of();
    }

    @Override
    public void addTransformerExclusion(String name) {
    }
}
