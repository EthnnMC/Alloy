package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alloy.remap.SrgNameTable;
import dev.alloy.remap.mapping.SrgRemapper;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mod reads a Minecraft field by reflection, giving its SRG name in a string. After the game is
 * remapped that name no longer exists; only the pass lets the mod find the field.
 */
class SrgStringPassTest {

    private static final SrgNameTable NAMES = SrgNameTable.of(Map.of("field_73840_e", "persistantChatGUI"));

    private static Class<?> mod;

    @BeforeAll
    static void compileRemapAndLoad() throws ClassNotFoundException {
        Map<String, byte[]> compiled = SourceCompiler.compile(8,
                """
                package net.minecraft.client.gui;
                public class GuiIngame {
                    public String field_73840_e = "chat";
                }
                """,
                """
                package example.mod;
                import net.minecraft.client.gui.GuiIngame;
                public class Reflective {
                    public static final String FIELD_NAME = "field_73840_e";
                    public static String viaLiteral() throws Exception {
                        return (String) GuiIngame.class.getDeclaredField("field_73840_e").get(new GuiIngame());
                    }
                    public static String sentence() { return "field_73840_e is the chat"; }
                    public static String unknownName() { return "field_99999_zz"; }
                }
                """);
        Map<String, byte[]> remapped = ClassBytes.remapAll(compiled, new SrgRemapper(SrgStringPassTest.NAMES));
        Map<String, byte[]> transformed = ClassBytes.applyToAll(remapped, new SrgStringPass(SrgStringPassTest.NAMES));
        SrgStringPassTest.mod = new InMemoryClassLoader(transformed).loadClass("example.mod.Reflective");
    }

    @Test
    void reflectionBySrgNameFindsTheRenamedField() throws ReflectiveOperationException {
        assertEquals("chat", SrgStringPassTest.mod.getMethod("viaLiteral").invoke(null));
    }

    @Test
    void constantFieldHoldingAnSrgNameIsTranslated() throws ReflectiveOperationException {
        assertEquals("persistantChatGUI", SrgStringPassTest.mod.getField("FIELD_NAME").get(null));
    }

    @Test
    void textThatMerelyContainsAnSrgNameIsKept() throws ReflectiveOperationException {
        assertEquals("field_73840_e is the chat", SrgStringPassTest.mod.getMethod("sentence").invoke(null));
    }

    @Test
    void unknownSrgNameIsKept() throws ReflectiveOperationException {
        assertEquals("field_99999_zz", SrgStringPassTest.mod.getMethod("unknownName").invoke(null));
    }
}
