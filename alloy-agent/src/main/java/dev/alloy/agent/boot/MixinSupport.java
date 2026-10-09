package dev.alloy.agent.boot;

import dev.alloy.agent.config.AlloyHome;
import dev.alloy.agent.forge.EmbeddedMixinHost;
import dev.alloy.agent.launch.BakedClasses;
import dev.alloy.agent.launch.LaunchInfo;
import dev.alloy.agent.loader.AlloyClassLoader;
import dev.alloy.agent.loader.GameClassSource;
import dev.alloy.agent.loader.MixinHostLoader;
import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.ClassRewriter;
import dev.alloy.bridge.MixinSetup;
import dev.alloy.bridge.PreparedMod;
import dev.alloy.hooks.GameClassTransformer;
import dev.alloy.remap.mixin.MixinIndex;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Starts the Mixin library for the mods that use it: collects their mixin configurations, loads
 * the Mixin host in its own class loader, tells it where to read classes and resources, and plugs
 * it into the class transformer. Nothing happens when no mod has mixins. Facade.
 */
final class MixinSupport {

    private static final String HOST_CLASS = "dev.alloy.mixin.MixinHost";
    private static final String HOST_START_METHOD = "start";
    private static final String HOST_CACHE_GROUP = "mixin";
    private static final String CLASS_SUFFIX = ".class";

    private final AlloyHome home;
    private final BridgeLogger logger;
    private final LaunchInfo launch;
    private final Instrumentation instrumentation;
    private final GameClassTransformer transformer;

    MixinSupport(
            AlloyHome home, BridgeLogger logger, LaunchInfo launch,
            Instrumentation instrumentation, GameClassTransformer transformer) {
        this.home = home;
        this.logger = logger;
        this.launch = launch;
        this.instrumentation = instrumentation;
        this.transformer = transformer;
    }

    /**
     * Starts Mixin if a mod needs it.
     *
     * @param mods        the mods of this launch
     * @param modLoader   the class loader holding them
     * @param gameLoader  the game's class loader
     * @param classSource what the game's loader asks for mod classes; it will also define the
     *                    classes Mixin generates
     * @throws IOException                  if a prepared jar or the host jar cannot be read
     * @throws ReflectiveOperationException if the Mixin host cannot be started
     */
    void start(List<PreparedMod> mods, AlloyClassLoader modLoader, ClassLoader gameLoader, GameClassSource classSource)
            throws IOException, ReflectiveOperationException {
        List<String> configs = new ArrayList<>();
        Set<String> targets = new LinkedHashSet<>();
        for (PreparedMod mod : mods) {
            MixinIndex.Contents contents = MixinSupport.mixinsOf(mod);
            configs.addAll(contents.configs());
            targets.addAll(contents.targets());
        }
        if (configs.isEmpty()) {
            return;
        }

        Path hostJar = EmbeddedMixinHost.extract(this.home.cacheDirectory().resolve(MixinSupport.HOST_CACHE_GROUP));
        MixinHostLoader hostLoader = new MixinHostLoader(hostJar.toUri().toURL(), gameLoader);
        modLoader.useMixinLibrary(hostLoader);

        Set<String> loadedEarly = this.classesLoadedBy(gameLoader);
        MixinSetup setup = new MixinSetup(
                configs, modLoader, this.classBytes(modLoader, gameLoader), MixinSupport.modResources(modLoader),
                loadedEarly::contains, this.logger);
        ClassRewriter host = (ClassRewriter) Class.forName(MixinSupport.HOST_CLASS, true, hostLoader)
                .getMethod(MixinSupport.HOST_START_METHOD, MixinSetup.class).invoke(null, setup);

        this.transformer.useModRewriter(host, targets);
        classSource.useGenerator(host);
        this.logger.info("Mixin started with " + configs + ": " + targets.size() + " game class(es) targeted");
        for (String target : targets) {
            if (loadedEarly.contains(target.replace('/', '.'))) {
                this.logger.warn("Mixin target " + target + " was loaded before the mods: its mixins cannot be applied");
            }
        }
    }

    private static MixinIndex.Contents mixinsOf(PreparedMod mod) throws IOException {
        try (JarFile jar = new JarFile(mod.loadableJar().toFile())) {
            JarEntry entry = jar.getJarEntry(MixinIndex.JAR_ENTRY);
            if (entry == null) {
                return new MixinIndex.Contents(List.of(), Set.of());
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return MixinIndex.Contents.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    /** Dotted names of the classes the game loader already holds. */
    private Set<String> classesLoadedBy(ClassLoader gameLoader) {
        Set<String> names = new HashSet<>();
        for (Class<?> loaded : this.instrumentation.getInitiatedClasses(gameLoader)) {
            names.add(loaded.getName());
        }
        return names;
    }

    /**
     * Where Mixin reads a class by internal name: the mods and Forge first, then the game as
     * Lunar runs it, then the JDK. The game loader's own answer comes last, as it is the class
     * before Lunar rewrote it.
     */
    private Function<String, byte[]> classBytes(AlloyClassLoader modLoader, ClassLoader gameLoader) {
        Optional<BakedClasses> baked = this.transformer.gameMainClassBytes()
                .flatMap(mainClass -> BakedClasses.locate(this.launch.classPath(), mainClass));
        if (baked.isPresent()) {
            this.logger.info("Lunar's class cache: " + baked.get().file());
        } else {
            this.logger.warn("Lunar's class cache was not found (first launch after an update?): on this launch"
                    + " mixins that need to know what a game class inherits may fail");
        }
        return internalName -> {
            String resourceName = internalName + MixinSupport.CLASS_SUFFIX;
            byte[] found = MixinSupport.read(modLoader.findResource(resourceName));
            if (found == null && baked.isPresent()) {
                found = baked.get().read(internalName);
            }
            if (found == null) {
                found = MixinSupport.read(ClassLoader.getPlatformClassLoader().getResource(resourceName));
            }
            return found != null ? found : MixinSupport.read(gameLoader.getResource(resourceName));
        };
    }

    /** Resources of the mod jars only: the game's loader hides or replaces some of the names Mixin asks for. */
    private static Function<String, InputStream> modResources(AlloyClassLoader modLoader) {
        return name -> {
            URL resource = modLoader.findResource(name);
            try {
                return resource == null ? null : resource.openStream();
            } catch (IOException e) {
                return null;
            }
        };
    }

    private static byte[] read(URL resource) {
        if (resource == null) {
            return null;
        }
        try (InputStream input = resource.openStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
