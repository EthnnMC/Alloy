package dev.alloy.agent.loader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class loader of the Forge mods: it holds the rewritten Alloy runtime, Forge and mod jars, with
 * Lunar's loader as parent, so mods see the game's classes but the game never sees theirs.
 * It defines its own classes first, except those the game already provides (OptiFine bundles a few
 * {@code net.minecraftforge.*} classes), because Lunar's loader answers slowly for unknown
 * classes and a class must never exist twice.
 */
public final class AlloyClassLoader extends URLClassLoader {

    private static final String CLASS_SUFFIX = ".class";

    static {
        // Several game threads load classes at the same time: one lock per class name.
        ClassLoader.registerAsParallelCapable();
    }

    private static final String MIXIN_PACKAGE = "org.spongepowered.asm.";

    /**
     * Mixin classes that code merged into the game uses at run time. Game code and mod code must
     * agree on them, so they always come from the game, never from the Mixin host.
     */
    private static final List<String> MIXIN_RUNTIME_PACKAGES = List.of(
            "org.spongepowered.asm.mixin.injection.callback.", "org.spongepowered.asm.mixin.injection.invoke.arg.");

    private final ConcurrentHashMap<String, Boolean> providedByGame = new ConcurrentHashMap<>();

    /** The Mixin host's loader; {@code null} when no mod has mixins. */
    private volatile MixinHostLoader mixinLibrary;

    /**
     * Creates the loader.
     *
     * @param jars       Alloy runtime, Forge and mods, in priority order
     * @param gameLoader the game's class loader (Lunar's)
     */
    public AlloyClassLoader(URL[] jars, ClassLoader gameLoader) {
        super("Alloy", jars, gameLoader);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> loaded = this.findLoadedClass(name);
            if (loaded == null) {
                if (this.comesFromMixinLibrary(name)) {
                    loaded = this.mixinLibrary.loadClass(name);
                } else {
                    loaded = this.definesItself(name) ? this.findClass(name) : super.loadClass(name, false);
                }
            }
            if (resolve) {
                this.resolveClass(loaded);
            }
            return loaded;
        }
    }

    /**
     * Makes the mods use the Mixin library of the Mixin host. A mixin plugin written by a mod
     * implements Mixin's interfaces: they must be the host's, not those of the game's own copy.
     */
    public void useMixinLibrary(MixinHostLoader library) {
        this.mixinLibrary = library;
    }

    private boolean comesFromMixinLibrary(String name) {
        MixinHostLoader library = this.mixinLibrary;
        if (library == null || !name.startsWith(AlloyClassLoader.MIXIN_PACKAGE)) {
            return false;
        }
        for (String runtimePackage : AlloyClassLoader.MIXIN_RUNTIME_PACKAGES) {
            if (name.startsWith(runtimePackage)) {
                return false;
            }
        }
        return library.has(name);
    }

    /**
     * Returns whether this loader must define the class itself: one of its jars has it and the game does not.
     *
     * @param className dotted class name
     */
    boolean definesItself(String className) {
        String resourceName = className.replace('.', '/') + AlloyClassLoader.CLASS_SUFFIX;
        // findResource only looks in our own jars, not in the parent.
        if (this.findResource(resourceName) == null) {
            return false;
        }
        return !this.providedByGame.computeIfAbsent(className, ignored -> this.getParent().getResource(resourceName) != null);
    }
}
