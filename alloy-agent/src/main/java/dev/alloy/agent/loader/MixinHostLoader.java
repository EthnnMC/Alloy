package dev.alloy.agent.loader;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Class loader of the Mixin host: Alloy's {@code alloy-mixin} module with the Mixin library and
 * what it needs. Lunar ships its own, different copy of Mixin under the same class names, so this
 * loader defines everything its jar has itself and only asks the game for the rest.
 */
public final class MixinHostLoader extends URLClassLoader {

    private static final String CLASS_SUFFIX = ".class";

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * @param hostJar    the Mixin host jar extracted from the agent
     * @param gameLoader the game's class loader (Lunar's)
     */
    public MixinHostLoader(URL hostJar, ClassLoader gameLoader) {
        super("Alloy Mixin", new URL[] {hostJar}, gameLoader);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> loaded = this.findLoadedClass(name);
            if (loaded == null) {
                loaded = this.has(name) ? this.findClass(name) : super.loadClass(name, false);
            }
            if (resolve) {
                this.resolveClass(loaded);
            }
            return loaded;
        }
    }

    /**
     * Tells whether the host jar holds a class.
     *
     * @param className dotted class name
     */
    public boolean has(String className) {
        return this.findResource(className.replace('.', '/') + MixinHostLoader.CLASS_SUFFIX) != null;
    }
}
