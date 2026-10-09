package dev.alloy.hooks;

/**
 * Identity of a loader class: its name <em>and</em> the loader that defined it. The name alone
 * is not enough, because Lunar loads its bootstrap classes twice through two loaders, which the
 * JVM treats as two distinct classes.
 *
 * @param internalName   internal class name (with slashes)
 * @param definingLoader loader that defined the class; {@code null} for the JVM bootstrap loader
 */
record LoaderClassKey(String internalName, ClassLoader definingLoader) {

    /** Creates the key of an already loaded class. */
    static LoaderClassKey of(Class<?> loaderClass) {
        return new LoaderClassKey(loaderClass.getName().replace('.', '/'), loaderClass.getClassLoader());
    }
}
