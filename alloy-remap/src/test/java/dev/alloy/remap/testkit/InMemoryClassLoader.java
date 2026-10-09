package dev.alloy.remap.testkit;

import java.util.Map;

/**
 * Test helper: class loader that defines classes from in-memory bytes, so the JVM bytecode
 * verifier checks rewritten classes.
 */
public final class InMemoryClassLoader extends ClassLoader {

    /** Internal class name to {@code .class} bytes. */
    private final Map<String, byte[]> classFiles;

    /**
     * Creates the loader; its parent is the test class loader.
     *
     * @param classFiles the classes this loader can define
     */
    public InMemoryClassLoader(Map<String, byte[]> classFiles) {
        super(InMemoryClassLoader.class.getClassLoader());
        this.classFiles = Map.copyOf(classFiles);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = this.classFiles.get(name.replace('.', '/'));
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return this.defineClass(name, bytes, 0, bytes.length);
    }
}
