package dev.alloy.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.ClassRewriter;
import dev.alloy.bridge.MixinSetup;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Mixin library through the host on fixture classes. Mixin keeps its state for the
 * whole JVM, so the host is started once for all tests.
 */
class MixinHostTest {

    private static final String COUNTER = "dev.alloy.mixin.fixture.Counter";
    private static final BridgeLogger SILENT = (level, message, error) -> { };

    private static ClassRewriter host;

    @BeforeAll
    static void startHost() throws ReflectiveOperationException {
        ClassLoader loader = MixinHostTest.class.getClassLoader();
        MixinSetup setup = new MixinSetup(
                List.of("mixins.alloytest.json"), loader,
                internalName -> MixinHostTest.read(loader, internalName + ".class"),
                loader::getResourceAsStream, name -> false, MixinHostTest.SILENT);
        MixinHostTest.host = MixinHost.start(setup);
    }

    @Test
    void mixinIsAppliedToItsTarget() throws ReflectiveOperationException {
        byte[] original = MixinHostTest.read(MixinHostTest.class.getClassLoader(), MixinHostTest.COUNTER.replace('.', '/') + ".class");

        byte[] rewritten = MixinHostTest.host.rewrite(MixinHostTest.COUNTER, original);

        assertNotNull(rewritten);
        Class<?> counterClass = new SingleClassLoader(MixinHostTest.COUNTER, rewritten).loadClass(MixinHostTest.COUNTER);
        Object counter = counterClass.getConstructor().newInstance();
        assertEquals(10, counterClass.getMethod("value").invoke(counter));
        counterClass.getMethod("click").invoke(counter);
        assertEquals(0, counterClass.getMethod("value").invoke(counter));
    }

    @Test
    void classNoMixinTargetsIsLeftAlone() {
        String bystander = "dev.alloy.mixin.fixture.Bystander";
        byte[] original = MixinHostTest.read(MixinHostTest.class.getClassLoader(), bystander.replace('.', '/') + ".class");

        assertNull(MixinHostTest.host.rewrite(bystander, original));
    }

    @Test
    void nothingIsGeneratedForAnUnknownClass() {
        assertNull(MixinHostTest.host.rewrite("dev.alloy.mixin.fixture.Counter$Unknown", null));
    }

    private static byte[] read(ClassLoader loader, String resourceName) {
        try (InputStream input = loader.getResourceAsStream(resourceName)) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Defines one class from the given bytes and takes every other class from the test class path. */
    private static final class SingleClassLoader extends ClassLoader {

        private final String className;
        private final byte[] classBytes;

        SingleClassLoader(String className, byte[] classBytes) {
            super(MixinHostTest.class.getClassLoader());
            this.className = className;
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(this.className)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = this.findLoadedClass(name);
            return loaded != null ? loaded : this.defineClass(name, this.classBytes, 0, this.classBytes.length);
        }
    }
}
