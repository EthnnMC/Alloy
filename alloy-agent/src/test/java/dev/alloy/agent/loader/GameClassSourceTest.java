package dev.alloy.agent.loader;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.alloy.bridge.BridgeLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests what the game's class loader is given when it asks Alloy for a class. */
class GameClassSourceTest {

    private static final Class<?> SAMPLE = SampleModClass.class;
    private static final BridgeLogger SILENT = (level, message, error) -> { };

    @TempDir
    Path directory;

    @Test
    void modClassIsGivenToTheGame() throws Exception {
        Path modJar = this.jarContaining(GameClassSourceTest.SAMPLE);
        try (URLClassLoader game = new URLClassLoader(new URL[0], null);
                AlloyClassLoader mods = new AlloyClassLoader(new URL[] {modJar.toUri().toURL()}, game)) {
            GameClassSource source = new GameClassSource(mods, game, GameClassSourceTest.anyMethod(), GameClassSourceTest.SILENT);

            Class<?> given = source.apply(GameClassSourceTest.SAMPLE.getName());

            assertSame(mods, given.getClassLoader());
        }
    }

    @Test
    void classTheModsDoNotHaveIsLeftToTheGame() throws Exception {
        try (URLClassLoader game = new URLClassLoader(new URL[0], null);
                AlloyClassLoader mods = new AlloyClassLoader(new URL[0], game)) {
            GameClassSource source = new GameClassSource(mods, game, GameClassSourceTest.anyMethod(), GameClassSourceTest.SILENT);

            assertNull(source.apply("java.lang.String"));
            assertNull(source.apply("example.game.Missing$Inner"));
        }
    }

    /** The method is only called to define classes Mixin generates, which these tests do not do. */
    private static Method anyMethod() throws NoSuchMethodException {
        return Object.class.getMethod("toString");
    }

    private Path jarContaining(Class<?> type) throws IOException {
        String entryName = type.getName().replace('.', '/') + ".class";
        Path jar = this.directory.resolve("mod.jar");
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
