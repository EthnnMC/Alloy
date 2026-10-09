package dev.alloy.remap.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.SrgNameTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ClassScan;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;

import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A fake Minecraft and a fake mod are compiled with SRG names, like a real Forge mod. One table
 * remaps both, and the remapped mod must run against the remapped Minecraft.
 */
class SrgRemapperTest {

    private static final SrgNameTable NAMES = SrgNameTable.of(Map.of(
            "field_146294_l", "width",
            "func_73863_a", "drawScreen",
            "func_71410_x", "getMinecraft",
            "func_150254_d", "getFormattedText"));

    /** Internal name to remapped class. */
    private static Map<String, byte[]> remapped;

    private static ClassLoader loader;

    @BeforeAll
    static void compileAndRemap() {
        Map<String, byte[]> compiled = SourceCompiler.compile(8,
                """
                package net.minecraft.client.gui;
                public class GuiScreen {
                    public int field_146294_l = 7;
                    public String func_73863_a(int mouseX) { return "screen" + mouseX; }
                    public static GuiScreen func_71410_x() { return new GuiScreen(); }
                    public int func_175613_B() { return 99; }
                }
                """,
                """
                package net.minecraft.util;
                public interface IChatComponent {
                    String func_150254_d();
                }
                """,
                """
                package example.mod;
                import java.util.function.IntFunction;
                import net.minecraft.client.gui.GuiScreen;
                import net.minecraft.util.IChatComponent;
                public class ModScreen extends GuiScreen {
                    @Override public String func_73863_a(int mouseX) {
                        return "mod:" + super.func_73863_a(mouseX + this.field_146294_l);
                    }
                    public int widthOfNewScreen() { return GuiScreen.func_71410_x().field_146294_l; }
                    public int unnamed() { return this.func_175613_B(); }
                    public IChatComponent component() { return () -> "lambda"; }
                    public IntFunction<String> reference() { return this::func_73863_a; }
                }
                """);
        SrgRemapperTest.remapped = ClassBytes.remapAll(compiled, new SrgRemapper(SrgRemapperTest.NAMES));
        SrgRemapperTest.loader = new InMemoryClassLoader(SrgRemapperTest.remapped);
    }

    private static Set<String> modScreen() {
        return ClassScan.describe(SrgRemapperTest.remapped.get("example/mod/ModScreen"));
    }

    private static Object newModScreen() throws ReflectiveOperationException {
        return SrgRemapperTest.loader.loadClass("example.mod.ModScreen").getConstructor().newInstance();
    }

    @Test
    void renamesReferencesToMinecraftMembers() {
        Set<String> lines = SrgRemapperTest.modScreen();

        assertTrue(lines.contains("INVOKESTATIC net/minecraft/client/gui/GuiScreen.getMinecraft "
                + "()Lnet/minecraft/client/gui/GuiScreen;"), lines.toString());
        assertTrue(lines.contains("GETFIELD net/minecraft/client/gui/GuiScreen.width I"), lines.toString());
        assertTrue(lines.contains("INVOKESPECIAL net/minecraft/client/gui/GuiScreen.drawScreen (I)Ljava/lang/String;"),
                lines.toString());
    }

    @Test
    void renamesReferencesWrittenThroughAModClassOwner() {
        Set<String> lines = SrgRemapperTest.modScreen();

        // For "this.field_146294_l" the bytecode names ModScreen, which is in no table.
        assertTrue(lines.contains("GETFIELD example/mod/ModScreen.width I"), lines.toString());
    }

    @Test
    void renamesOverridesDeclaredByTheMod() {
        Set<String> lines = SrgRemapperTest.modScreen();

        assertTrue(lines.contains("method drawScreen (I)Ljava/lang/String;"), lines.toString());
    }

    @Test
    void leavesUnmappedSrgNamesAlone() {
        Set<String> lines = SrgRemapperTest.modScreen();

        assertTrue(lines.contains("INVOKEVIRTUAL example/mod/ModScreen.func_175613_B ()I"), lines.toString());
    }

    @Test
    void renamesLambdaNamesAndMethodReferences() {
        Set<String> lines = SrgRemapperTest.modScreen();

        assertTrue(lines.contains("INVOKEDYNAMIC getFormattedText ()Lnet/minecraft/util/IChatComponent;"),
                lines.toString());
        assertTrue(lines.contains("HANDLE example/mod/ModScreen.drawScreen (I)Ljava/lang/String;"), lines.toString());
    }

    @Test
    void leavesNoMappedSrgNameBehind() {
        for (Map.Entry<String, byte[]> entry : SrgRemapperTest.remapped.entrySet()) {
            for (String line : ClassScan.describe(entry.getValue())) {
                for (String srgName : SrgRemapperTest.NAMES.asMap().keySet()) {
                    assertFalse(line.contains(srgName), entry.getKey() + ": " + line);
                }
            }
        }
    }

    @Test
    void leavesClassNamesUntouched() {
        assertEquals(
                Set.of("net/minecraft/client/gui/GuiScreen", "net/minecraft/util/IChatComponent", "example/mod/ModScreen"),
                SrgRemapperTest.remapped.keySet());
    }

    @Test
    void remappedModRunsAgainstRemappedGame() throws ReflectiveOperationException {
        Object screen = SrgRemapperTest.newModScreen();
        Class<?> guiScreen = SrgRemapperTest.loader.loadClass("net.minecraft.client.gui.GuiScreen");

        // Virtual call by the game's name must reach the mod's override.
        Object drawn = guiScreen.getMethod("drawScreen", int.class).invoke(screen, 1);

        assertEquals("mod:screen8", drawn);
        assertEquals(7, screen.getClass().getMethod("widthOfNewScreen").invoke(screen));
        assertEquals(99, screen.getClass().getMethod("unnamed").invoke(screen));
    }

    @Test
    void remappedLambdaAndMethodReferenceRun() throws ReflectiveOperationException {
        Object screen = SrgRemapperTest.newModScreen();

        Object component = screen.getClass().getMethod("component").invoke(screen);
        Object text = SrgRemapperTest.loader.loadClass("net.minecraft.util.IChatComponent")
                .getMethod("getFormattedText").invoke(component);
        @SuppressWarnings("unchecked")
        IntFunction<String> reference = (IntFunction<String>) screen.getClass().getMethod("reference").invoke(screen);

        assertEquals("lambda", text);
        assertEquals("mod:screen9", reference.apply(2));
    }
}
