package com.moonsworth.lunar.genesis;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

/**
 * Test imitation of Lunar's game class loader ({@code GenesisClassLoader}): same package
 * ({@code com.moonsworth.lunar.genesis}), extends {@link URLClassLoader} directly, declares
 * {@code loadClass(String, boolean)}, and resolves every non-JDK class itself, <b>never asking
 * its parent</b>, so agent classes are invisible to it until the bridge is in place.
 *
 * <p>Tests read its bytecode, patch it and define the patched copy in another loader.</p>
 */
public final class FakeGenesisLoader extends URLClassLoader {

    /** Bytecode of the "game classes", by dotted class name. */
    private final Map<String, byte[]> gameClasses;

    /**
     * Creates the loader; {@code gameClasses} is not copied, so a test can add classes later, and
     * {@code parent} is consulted only for JDK classes.
     */
    public FakeGenesisLoader(Map<String, byte[]> gameClasses, ClassLoader parent) {
        super("FakeGenesis", new URL[0], parent);
        this.gameClasses = gameClasses;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("java.")) {
            return super.loadClass(name, resolve);
        }
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> alreadyLoaded = this.findLoadedClass(name);
            if (alreadyLoaded != null) {
                return alreadyLoaded;
            }
            byte[] classBytes = this.gameClasses.get(name);
            if (classBytes == null) {
                throw new ClassNotFoundException("Failed to find class " + name);
            }
            return this.defineClass(name, classBytes, 0, classBytes.length);
        }
    }
}
