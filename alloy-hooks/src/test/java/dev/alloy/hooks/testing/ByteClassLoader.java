package dev.alloy.hooks.testing;

/**
 * Test class loader that defines a class from bytes supplied by the test.
 *
 * <p>Defining and running modified bytecode is the strictest check: the JVM's real verifier
 * checks it, frames included.</p>
 */
public final class ByteClassLoader extends ClassLoader {

    /**
     * Creates a loader whose parent is the tests' loader, so defined classes see
     * {@code GameHooks} and {@link GameTrace}.
     */
    public ByteClassLoader() {
        super(ByteClassLoader.class.getClassLoader());
    }

    /** Creates a loader with the given parent. */
    public ByteClassLoader(ClassLoader parent) {
        super(parent);
    }

    /** Defines a class and has the JVM verify it right away. */
    public Class<?> define(byte[] bytes) {
        Class<?> defined = this.defineClass(null, bytes, 0, bytes.length);
        try {
            // Initializing forces linking, so all the bytecode is verified now rather than when
            // a test calls the faulty method.
            return Class.forName(defined.getName(), true, this);
        } catch (ClassNotFoundException impossible) {
            throw new IllegalStateException("A class that was just defined cannot be found", impossible);
        }
    }
}
