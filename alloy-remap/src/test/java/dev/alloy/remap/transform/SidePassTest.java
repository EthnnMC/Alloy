package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ForgeStubs;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SidePassTest {

    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        SidePassTest.compiled = SourceCompiler.compile(8,
                ForgeStubs.SIDE,
                ForgeStubs.SIDE_ONLY,
                """
                package example.mod;
                import net.minecraftforge.fml.relauncher.Side;
                import net.minecraftforge.fml.relauncher.SideOnly;
                public class Mixed {
                    public int common;
                    @SideOnly(Side.CLIENT) public int clientField;
                    @SideOnly(Side.SERVER) public int serverField;
                    public void commonMethod() { }
                    @SideOnly(Side.CLIENT) public void clientMethod() { }
                    @SideOnly(Side.SERVER) public void serverMethod() { }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.relauncher.Side;
                import net.minecraftforge.fml.relauncher.SideOnly;
                @SideOnly(Side.CLIENT)
                public class ClientOnly {
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.relauncher.Side;
                import net.minecraftforge.fml.relauncher.SideOnly;
                @SideOnly(Side.SERVER)
                public class ServerOnly {
                }
                """);
    }

    private static Map<String, byte[]> transformed(List<String> warnings) {
        return ClassBytes.applyToAll(SidePassTest.compiled, new SidePass(warnings::add));
    }

    @Test
    void serverOnlyMembersAreRemovedAndTheOthersKept() throws ClassNotFoundException {
        Class<?> mixed = new InMemoryClassLoader(SidePassTest.transformed(new ArrayList<>())).loadClass("example.mod.Mixed");

        Set<String> fields = Arrays.stream(mixed.getDeclaredFields()).map(field -> field.getName())
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(mixed.getDeclaredMethods()).map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("common", "clientField"), fields);
        assertEquals(Set.of("commonMethod", "clientMethod"), methods);
    }

    @Test
    void clientOnlyClassIsKept() {
        assertTrue(SidePassTest.transformed(new ArrayList<>()).containsKey("example/mod/ClientOnly"));
    }

    @Test
    void serverOnlyClassIsLeftOutWithAWarning() {
        List<String> warnings = new ArrayList<>();

        Map<String, byte[]> transformed = SidePassTest.transformed(warnings);

        assertFalse(transformed.containsKey("example/mod/ServerOnly"));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("example/mod/ServerOnly"), warnings.get(0));
    }
}
