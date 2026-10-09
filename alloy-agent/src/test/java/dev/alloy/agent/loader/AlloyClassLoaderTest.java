package dev.alloy.agent.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the mod loader's delegation rule: own classes first, except those the game already provides. */
class AlloyClassLoaderTest {

    /** Any test class, copied into jars to play a mod class. */
    private static final Class<?> SAMPLE = SampleModClass.class;

    @TempDir
    Path directory;

    @Test
    void definesItsOwnClassWhenTheGameDoesNotHaveIt() throws Exception {
        Path modJar = this.jarContaining(AlloyClassLoaderTest.SAMPLE, "mod.jar");
        ClassLoader emptyGame = new URLClassLoader(new URL[0], null);

        try (AlloyClassLoader loader = new AlloyClassLoader(new URL[] {modJar.toUri().toURL()}, emptyGame)) {
            Class<?> loaded = loader.loadClass(AlloyClassLoaderTest.SAMPLE.getName());

            assertSame(loader, loaded.getClassLoader());
            assertNotSame(AlloyClassLoaderTest.SAMPLE, loaded);
            assertTrue(loader.definesItself(AlloyClassLoaderTest.SAMPLE.getName()));
        }
    }

    @Test
    void leavesTheClassToTheGameWhenTheGameProvidesIt() throws Exception {
        Path modJar = this.jarContaining(AlloyClassLoaderTest.SAMPLE, "mod.jar");
        Path gameJar = this.jarContaining(AlloyClassLoaderTest.SAMPLE, "game.jar");

        try (URLClassLoader game = new URLClassLoader(new URL[] {gameJar.toUri().toURL()}, null);
                AlloyClassLoader loader = new AlloyClassLoader(new URL[] {modJar.toUri().toURL()}, game)) {
            Class<?> loaded = loader.loadClass(AlloyClassLoaderTest.SAMPLE.getName());

            assertSame(game, loaded.getClassLoader());
            assertFalse(loader.definesItself(AlloyClassLoaderTest.SAMPLE.getName()));
        }
    }

    @Test
    void asksTheGameForClassesItDoesNotContain() throws Exception {
        try (AlloyClassLoader loader = new AlloyClassLoader(new URL[0], AlloyClassLoaderTest.class.getClassLoader())) {
            assertSame(String.class, loader.loadClass("java.lang.String"));
            assertSame(AlloyClassLoaderTest.class, loader.loadClass(AlloyClassLoaderTest.class.getName()));
        }
    }

    /** Builds a jar holding the {@code .class} file of the given class. */
    private Path jarContaining(Class<?> type, String jarName) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        Path jar = this.directory.resolve(jarName);
        try (InputStream classFile = type.getClassLoader().getResourceAsStream(entryName);
                OutputStream file = Files.newOutputStream(jar);
                JarOutputStream output = new JarOutputStream(file)) {
            output.putNextEntry(new JarEntry(entryName));
            classFile.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }
}
