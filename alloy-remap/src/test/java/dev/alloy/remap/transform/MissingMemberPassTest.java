package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.SourceCompiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mod is compiled against a Minecraft that has two Forge-added members, then examined against a
 * Minecraft without them.
 */
class MissingMemberPassTest {

    private static final String GUI = """
            package net.minecraft.client.gui;
            public class Gui {
                public void drawRect() { }
            }
            """;

    private static Map<String, byte[]> modClasses;
    private static Map<String, byte[]> runtimeClasses;

    @BeforeAll
    static void compile() {
        Map<String, byte[]> compiled = new LinkedHashMap<>(SourceCompiler.compile(8,
                MissingMemberPassTest.GUI,
                """
                package net.minecraft.client.gui;
                public class GuiScreen extends Gui {
                    public int width;
                    public int forgeAdded;
                    public void drawScreen() { }
                    public void drawHoveringText() { }
                }
                """,
                """
                package example.mod;
                import net.minecraft.client.gui.GuiScreen;
                public class ModScreen extends GuiScreen {
                    void own() { }
                    int use() {
                        this.drawScreen();
                        this.drawRect();
                        this.own();
                        this.drawHoveringText();
                        this.drawHoveringText();
                        return this.width + this.forgeAdded + this.hashCode();
                    }
                }
                """,
                """
                package example.mod;
                public class ModList extends java.util.ArrayList<String> {
                    int use() { return this.size(); }
                }
                """));
        compiled.keySet().removeIf(name -> name.startsWith("net/minecraft/"));
        MissingMemberPassTest.modClasses = compiled;
        MissingMemberPassTest.runtimeClasses = SourceCompiler.compile(8,
                MissingMemberPassTest.GUI,
                """
                package net.minecraft.client.gui;
                public class GuiScreen extends Gui {
                    public int width;
                    public void drawScreen() { }
                }
                """);
    }

    private static Map<String, byte[]> examine(List<String> warnings) {
        List<ClassHeader> headers = new ArrayList<>();
        MissingMemberPassTest.modClasses.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        MissingMemberPassTest.runtimeClasses.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        MissingMemberPass pass = new MissingMemberPass(new ClassHierarchy(HeaderTable.of(headers)), warnings::add);
        return ClassBytes.applyToAll(MissingMemberPassTest.modClasses, pass);
    }

    @Test
    void membersAbsentFromTheGameAreReportedOnce() {
        List<String> warnings = new ArrayList<>();

        MissingMemberPassTest.examine(warnings);

        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("example/mod/ModScreen.drawHoveringText()V"), warnings.get(0));
        assertTrue(warnings.get(1).contains("example/mod/ModScreen.forgeAdded I"), warnings.get(1));
    }

    @Test
    void classesAreLeftUnchanged() {
        Map<String, byte[]> examined = MissingMemberPassTest.examine(new ArrayList<>());

        for (Map.Entry<String, byte[]> original : MissingMemberPassTest.modClasses.entrySet()) {
            byte[] expected = ClassBytes.write(ClassBytes.read(original.getValue()));
            assertArrayEquals(expected, examined.get(original.getKey()), original.getKey());
        }
    }
}
