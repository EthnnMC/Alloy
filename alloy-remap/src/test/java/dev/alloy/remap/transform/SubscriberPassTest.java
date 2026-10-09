package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ForgeStubs;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SubscriberPassTest {

    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        SubscriberPassTest.compiled = SourceCompiler.compile(8,
                ForgeStubs.SUBSCRIBE_EVENT,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
                class Handler {
                    Handler() { }
                    @SubscribeEvent void onTick(Object event) { }
                    @SubscribeEvent protected void onDraw(Object event) { }
                    void helper() { }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
                class SecretiveHandler {
                    @SubscribeEvent private void hidden(Object event) { }
                    @SubscribeEvent void visible(Object event) { }
                }
                """,
                """
                package example.mod;
                class Bystander {
                    void onTick(Object event) { }
                }
                """);
    }

    private static Class<?> load(String className, List<String> warnings) throws ClassNotFoundException {
        Map<String, byte[]> transformed =
                ClassBytes.applyToAll(SubscriberPassTest.compiled, new SubscriberPass(warnings::add));
        return new InMemoryClassLoader(transformed).loadClass(className);
    }

    @Test
    void subscriberClassAndItsHandlersBecomePublic() throws ReflectiveOperationException {
        Class<?> handler = SubscriberPassTest.load("example.mod.Handler", new ArrayList<>());

        assertTrue(Modifier.isPublic(handler.getModifiers()));
        // getMethod only returns public methods, which is what the Forge bus uses.
        assertTrue(Modifier.isPublic(handler.getMethod("onTick", Object.class).getModifiers()));
        assertTrue(Modifier.isPublic(handler.getMethod("onDraw", Object.class).getModifiers()));
    }

    @Test
    void otherMembersOfASubscriberKeepTheirAccess() throws ReflectiveOperationException {
        Class<?> handler = SubscriberPassTest.load("example.mod.Handler", new ArrayList<>());

        assertFalse(Modifier.isPublic(handler.getDeclaredMethod("helper").getModifiers()));
        assertFalse(Modifier.isPublic(handler.getDeclaredConstructor().getModifiers()));
    }

    @Test
    void privateHandlerIsReportedAndStaysPrivate() throws ReflectiveOperationException {
        List<String> warnings = new ArrayList<>();

        Class<?> handler = SubscriberPassTest.load("example.mod.SecretiveHandler", warnings);

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("example/mod/SecretiveHandler/hidden(Ljava/lang/Object;)V"), warnings.get(0));
        assertThrows(NoSuchMethodException.class, () -> handler.getMethod("hidden", Object.class));
        assertTrue(Modifier.isPublic(handler.getMethod("visible", Object.class).getModifiers()));
    }

    @Test
    void classWithoutHandlerIsNotModified() {
        Map<String, byte[]> transformed =
                ClassBytes.applyToAll(SubscriberPassTest.compiled, new SubscriberPass(message -> { }));

        byte[] expected = ClassBytes.write(ClassBytes.read(SubscriberPassTest.compiled.get("example/mod/Bystander")));
        assertArrayEquals(expected, transformed.get("example/mod/Bystander"));
    }
}
