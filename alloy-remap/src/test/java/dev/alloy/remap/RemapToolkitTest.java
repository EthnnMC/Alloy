package dev.alloy.remap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.mapping.KinReader;
import dev.alloy.remap.mapping.NotchMappingsBuilder;
import dev.alloy.remap.mapping.NotchRemapper;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ClassScan;
import dev.alloy.remap.testkit.ForgeStubs;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.KinClassSpec;
import dev.alloy.remap.testkit.KinFixture;
import dev.alloy.remap.testkit.SourceCompiler;
import dev.alloy.remap.testkit.TestJars;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of the facade, with no Lunar, Mojang or Forge file. It builds a miniature world
 * shaped like the real one (obfuscated Mojang jar, signed Forge universal jar, a mod compiled against
 * SRG-named Minecraft plus a Forge-added member, and a Lunar mappings jar with two {@code .kin} files),
 * then loads the three prepared jars together and runs the mod against them through Forge.
 */
class RemapToolkitTest {

    private static final String VERSION = "1.8.9";
    private static final String SHIMS = "method\tnet/minecraft/network/NetworkManager\tchannel\t()Ljava/lang/Object;"
            + "\tgetfield\tchannel\tLjava/lang/Object;\n";
    private static final byte[] LANG_FILE = "example.key=Example\n".getBytes(StandardCharsets.UTF_8);

    /** Miniature Minecraft, written with readable names. */
    private static final String[] NAMED_MINECRAFT = {
        """
        package net.minecraft.client.gui;
        public class GuiScreen {
            protected int width = 320;
            public String drawScreen(int mouseX) { return "screen:" + mouseX; }
            public static GuiScreen current() { return new GuiScreen(); }
        }
        """,
        """
        package net.minecraft.network;
        public class NetworkManager {
            private final Object channel = "netty channel";
        }
        """,
        """
        package net.minecraft.util;
        public interface IChatComponent {
            String getText();
        }
        """
    };

    /** Miniature Forge: extends Minecraft, overrides a method and declares an event. */
    private static final String[] FORGE = {
        ForgeStubs.LISTENER_LIST,
        ForgeStubs.EVENT,
        ForgeStubs.CANCELABLE,
        ForgeStubs.SUBSCRIBE_EVENT,
        ForgeStubs.MOD,
        """
        package net.minecraftforge.client;
        import net.minecraft.client.gui.GuiScreen;
        public class ForgeScreen extends GuiScreen {
            @Override public String drawScreen(int mouseX) { return "forge:" + super.drawScreen(mouseX + this.width); }
        }
        """,
        """
        package net.minecraftforge.client.event;
        import net.minecraft.client.gui.GuiScreen;
        import net.minecraftforge.fml.common.eventhandler.Cancelable;
        import net.minecraftforge.fml.common.eventhandler.Event;
        @Cancelable
        public class GuiOpenEvent extends Event {
            public GuiScreen gui;
            public GuiOpenEvent(GuiScreen gui) { this.gui = gui; }
        }
        """,
        """
        package net.minecraftforge.fml.common;
        public class Loader {
            public static String describe() { return new Helper().toString(); }
            static class Helper { }
        }
        """
    };

    /** Minecraft as a mod's compiler sees it: SRG names, plus the accessor added by Forge. */
    private static final String[] SRG_MINECRAFT = {
        """
        package net.minecraft.client.gui;
        public class GuiScreen {
            protected int field_146294_l;
            public String func_73863_a(int mouseX) { return null; }
            public static GuiScreen func_71410_x() { return null; }
        }
        """,
        """
        package net.minecraft.network;
        public class NetworkManager {
            public Object channel() { return null; }
        }
        """,
        """
        package net.minecraft.util;
        public interface IChatComponent {
            String func_150254_d();
        }
        """
    };

    private static final String[] MOD = {
        """
        package example.mod;
        import net.minecraftforge.fml.common.Mod;
        @Mod(modid = "example")
        public class ExampleMod {
        }
        """,
        """
        package example.mod;
        import net.minecraftforge.client.ForgeScreen;
        public class ModScreen extends ForgeScreen {
            @Override public String func_73863_a(int mouseX) {
                return "mod:" + super.func_73863_a(mouseX) + ":" + this.field_146294_l;
            }
        }
        """,
        """
        package example.mod;
        import net.minecraft.client.gui.GuiScreen;
        import net.minecraftforge.client.event.GuiOpenEvent;
        public class ScreenShownEvent extends GuiOpenEvent {
            public ScreenShownEvent(GuiScreen gui) { super(gui); }
        }
        """,
        """
        package example.mod;
        import net.minecraftforge.client.event.GuiOpenEvent;
        import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
        class Handler {
            @SubscribeEvent void onOpen(GuiOpenEvent event) { event.setCanceled(true); }
        }
        """,
        """
        package example.mod;
        import net.minecraft.client.gui.GuiScreen;
        import net.minecraft.network.NetworkManager;
        import net.minecraft.util.IChatComponent;
        public class Calls {
            public static Object channelOf(NetworkManager manager) { return manager.channel(); }
            public static String drawCurrent() { return GuiScreen.func_71410_x().func_73863_a(5); }
            public static String widthFieldName() throws Exception {
                return GuiScreen.class.getDeclaredField("field_146294_l").getName();
            }
            public static IChatComponent component() { return () -> "text"; }
        }
        """
    };

    @TempDir
    static Path directory;

    private static Path mappingsJar;
    private static Path vanillaJar;
    private static Path universalJar;
    private static Path modJar;

    private static RemapToolkit toolkit;
    private static Path preparedVanilla;
    private static Path preparedForge;
    private static PreparedModJar preparedMod;

    /** Loader holding the three prepared jars: the game, Forge and the mod. */
    private static ClassLoader game;

    @BeforeAll
    static void buildTheMiniatureWorldAndPrepareIt() throws IOException {
        RemapToolkitTest.buildInputJars();
        RemapToolkitTest.toolkit =
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, RemapToolkitTest.VERSION, RemapToolkitTest.vanillaJar);

        RemapToolkitTest.preparedVanilla =
                RemapToolkitTest.toolkit.prepareVanilla(RemapToolkitTest.directory.resolve("out/minecraft.jar"));
        RemapToolkitTest.preparedForge = RemapToolkitTest.toolkit.prepareForge(
                RemapToolkitTest.universalJar, RemapToolkitTest.directory.resolve("out/forge.jar"), Set.of());
        RemapToolkitTest.preparedMod = RemapToolkitTest.toolkit.prepareMod(RemapToolkitTest.modJar,
                RemapToolkitTest.directory.resolve("out/mod.jar"), MemberShimTable.parse(RemapToolkitTest.SHIMS));

        Map<String, byte[]> everything = new LinkedHashMap<>(TestJars.readClasses(RemapToolkitTest.preparedVanilla));
        everything.putAll(TestJars.readClasses(RemapToolkitTest.preparedForge));
        everything.putAll(TestJars.readClasses(RemapToolkitTest.preparedMod.jar()));
        RemapToolkitTest.game = new InMemoryClassLoader(everything);
    }

    private static void buildInputJars() throws IOException {
        // 1. Minecraft and Forge are compiled together, with readable names...
        List<String> sources = new ArrayList<>(List.of(RemapToolkitTest.NAMED_MINECRAFT));
        sources.addAll(List.of(RemapToolkitTest.FORGE));
        Map<String, byte[]> named = SourceCompiler.compile(8, sources.toArray(String[]::new));

        // 2. ...then obfuscated, as Mojang does for Minecraft and Forge does for its references.
        KinFixture obfuscation = new KinFixture()
                .add(KinClassSpec.type("net/minecraft/client/gui/GuiScreen", "axu")
                        .field("width", "I", "l")
                        .method("drawScreen", "(I)Ljava/lang/String;", "a")
                        .method("current", "()Lnet/minecraft/client/gui/GuiScreen;", "A"))
                .add(KinClassSpec.type("net/minecraft/network/NetworkManager", "ek")
                        .field("channel", "Ljava/lang/Object;", "k"))
                .add(KinClassSpec.type("net/minecraft/util/IChatComponent", "eu")
                        .method("getText", "()Ljava/lang/String;", "e"));
        NotchMappingsBuilder obfuscationTable = new NotchMappingsBuilder();
        new KinReader(obfuscationTable).read(new ByteArrayInputStream(obfuscation.toBytes()));
        List<ClassHeader> headers = new ArrayList<>();
        named.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        Map<String, byte[]> obfuscated = ClassBytes.remapAll(named,
                new NotchRemapper(obfuscationTable.build(), new ClassHierarchy(HeaderTable.of(headers))));

        Map<String, byte[]> vanillaEntries = new LinkedHashMap<>();
        Map<String, byte[]> forgeClasses = new LinkedHashMap<>();
        obfuscated.forEach((name, classFile) -> {
            if (name.indexOf('/') < 0) {
                vanillaEntries.put(name + ".class", classFile);
            } else {
                forgeClasses.put(name, classFile);
            }
        });
        vanillaEntries.put("assets/minecraft/lang/en_US.lang", RemapToolkitTest.LANG_FILE);
        RemapToolkitTest.vanillaJar = TestJars.write(RemapToolkitTest.directory.resolve("1.8.9.jar"), vanillaEntries);

        Map<String, byte[]> universalEntries = new LinkedHashMap<>();
        universalEntries.put("META-INF/MANIFEST.MF", String.join("\r\n",
                "Manifest-Version: 1.0",
                "Class-Path: libraries/launchwrapper-1.12.jar",
                "",
                "Name: net/minecraftforge/client/ForgeScreen.class",
                "SHA-256-Digest: MdWIiX4OOfE2aefloA+1GaXaZKW52cSH/VI5Y1e2CuE=",
                "",
                "").getBytes(StandardCharsets.UTF_8));
        universalEntries.put("META-INF/FORGE.SF", new byte[] {1});
        universalEntries.put("META-INF/FORGE.DSA", new byte[] {2});
        universalEntries.put("forge_at.cfg", "public net.minecraft.client.gui.GuiScreen *\n".getBytes(StandardCharsets.UTF_8));
        universalEntries.putAll(TestJars.asEntries(forgeClasses));
        RemapToolkitTest.universalJar =
                TestJars.write(RemapToolkitTest.directory.resolve("forge-universal.jar"), universalEntries);

        // 3. The mod is compiled against SRG-named Minecraft and the Forge classes.
        Map<String, byte[]> forgeForCompilation = new LinkedHashMap<>(named);
        forgeForCompilation.keySet().removeIf(name -> !name.startsWith("net/minecraftforge/"));
        Path forgeApi = TestJars.write(
                RemapToolkitTest.directory.resolve("forge-api.jar"), TestJars.asEntries(forgeForCompilation));
        List<String> modSources = new ArrayList<>(List.of(RemapToolkitTest.SRG_MINECRAFT));
        modSources.addAll(List.of(RemapToolkitTest.MOD));
        Map<String, byte[]> modClasses =
                new LinkedHashMap<>(SourceCompiler.compile(8, List.of(forgeApi), modSources.toArray(String[]::new)));
        modClasses.keySet().removeIf(name -> !name.startsWith("example/"));
        Map<String, byte[]> modEntries = TestJars.asEntries(modClasses);
        modEntries.put("mcmod.info", "[{\"modid\": \"example\"}]".getBytes(StandardCharsets.UTF_8));
        RemapToolkitTest.modJar = TestJars.write(RemapToolkitTest.directory.resolve("example-mod.jar"), modEntries);

        // 4. Lunar's tables: obfuscated names to game names, and SRG names to game names.
        RemapToolkitTest.mappingsJar =
                RemapToolkitTest.writeMappingsJar(RemapToolkitTest.directory.resolve("lunar-mappings.jar"), "width");
    }

    /**
     * Writes the mappings jar.
     *
     * @param widthName game name given to the {@code GuiScreen.width} field (lets the fingerprint
     *                  test fake a Lunar update)
     */
    private static Path writeMappingsJar(Path jar, String widthName) throws IOException {
        KinFixture notchToGame = new KinFixture()
                .add(KinClassSpec.type("axu", "net/minecraft/client/gui/GuiScreen")
                        .field("l", "I", widthName)
                        .method("a", "(I)Ljava/lang/String;", "drawScreen")
                        .method("A", "()Laxu;", "current"))
                .add(KinClassSpec.type("ek", "net/minecraft/network/NetworkManager")
                        .field("k", "Ljava/lang/Object;", "channel"))
                .add(KinClassSpec.type("eu", "net/minecraft/util/IChatComponent")
                        .method("e", "()Ljava/lang/String;", "getText"));
        KinFixture srgToGame = new KinFixture()
                .add(KinClassSpec.type("net/minecraft/client/gui/GuiScreen", "net/minecraft/client/gui/GuiScreen")
                        .field("field_146294_l", "I", widthName)
                        .method("func_73863_a", "(I)Ljava/lang/String;", "drawScreen")
                        .method("func_71410_x", "()Lnet/minecraft/client/gui/GuiScreen;", "current"))
                .add(KinClassSpec.type("net/minecraft/network/NetworkManager", "net/minecraft/network/NetworkManager")
                        .field("field_150746_k", "Ljava/lang/Object;", "channel"))
                .add(KinClassSpec.type("net/minecraft/util/IChatComponent", "net/minecraft/util/IChatComponent")
                        .method("func_150254_d", "()Ljava/lang/String;", "getText"));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("lunar/lunar_named_b5_1.8.9.kin", notchToGame.toBytes());
        entries.put("lunar/searge2lunar_1.8.9.kin", srgToGame.toBytes());
        return TestJars.write(jar, entries);
    }

    private static Object invokeStatic(String className, String method) throws ReflectiveOperationException {
        return RemapToolkitTest.game.loadClass(className).getMethod(method).invoke(null);
    }

    private static Object newInstance(String className) throws ReflectiveOperationException {
        return RemapToolkitTest.game.loadClass(className).getConstructor().newInstance();
    }

    @Test
    void inputJarsHaveTheShapeOfTheRealOnes() throws IOException {
        Map<String, byte[]> universal = TestJars.readClasses(RemapToolkitTest.universalJar);
        Set<String> forgeScreen = ClassScan.describe(universal.get("net/minecraftforge/client/ForgeScreen"));
        Set<String> modScreen =
                ClassScan.describe(TestJars.readClasses(RemapToolkitTest.modJar).get("example/mod/ModScreen"));

        // Forge: obfuscated Minecraft superclass, override and inherited field with obfuscated names.
        assertTrue(forgeScreen.contains("class net/minecraftforge/client/ForgeScreen extends axu"), forgeScreen.toString());
        assertTrue(forgeScreen.contains("method a (I)Ljava/lang/String;"), forgeScreen.toString());
        assertTrue(forgeScreen.contains("GETFIELD net/minecraftforge/client/ForgeScreen.l I"), forgeScreen.toString());
        // Mod: SRG names, written on its own classes.
        assertTrue(modScreen.contains("method func_73863_a (I)Ljava/lang/String;"), modScreen.toString());
        assertTrue(modScreen.contains("GETFIELD example/mod/ModScreen.field_146294_l I"), modScreen.toString());
        assertEquals(Set.of("axu", "ek", "eu"), TestJars.readClasses(RemapToolkitTest.vanillaJar).keySet());
    }

    @Test
    void modRunsThroughForgeAgainstTheGame() throws ReflectiveOperationException {
        Object screen = RemapToolkitTest.newInstance("example.mod.ModScreen");
        Class<?> guiScreen = RemapToolkitTest.game.loadClass("net.minecraft.client.gui.GuiScreen");

        // Call by the game name goes through the mod's override, then Forge's, then Minecraft.
        Object drawn = guiScreen.getMethod("drawScreen", int.class).invoke(screen, 1);

        assertEquals("mod:forge:screen:321:320", drawn);
        assertEquals("screen:5", RemapToolkitTest.invokeStatic("example.mod.Calls", "drawCurrent"));
    }

    @Test
    void forgeAddedAccessorIsReplacedByAFieldRead() throws ReflectiveOperationException {
        Object manager = RemapToolkitTest.newInstance("net.minecraft.network.NetworkManager");
        Class<?> calls = RemapToolkitTest.game.loadClass("example.mod.Calls");

        Object channel = calls.getMethod("channelOf", manager.getClass()).invoke(null, manager);

        assertEquals("netty channel", channel);
    }

    @Test
    void reflectionAndLambdasOfTheModUseTheNamesOfTheGame() throws ReflectiveOperationException {
        Object component = RemapToolkitTest.invokeStatic("example.mod.Calls", "component");
        Object text = RemapToolkitTest.game.loadClass("net.minecraft.util.IChatComponent")
                .getMethod("getText").invoke(component);

        assertEquals("text", text);
        assertEquals("width", RemapToolkitTest.invokeStatic("example.mod.Calls", "widthFieldName"));
    }

    @Test
    void eventDeclaredByTheModIsCompletedLikeTheForgeOnes() throws ReflectiveOperationException {
        Object modEvent = RemapToolkitTest.newInstance("example.mod.ScreenShownEvent");
        Object forgeEvent = RemapToolkitTest.newInstance("net.minecraftforge.client.event.GuiOpenEvent");

        Object modList = modEvent.getClass().getMethod("getListenerList").invoke(modEvent);
        Object forgeList = forgeEvent.getClass().getMethod("getListenerList").invoke(forgeEvent);
        modEvent.getClass().getMethod("setCanceled", boolean.class).invoke(modEvent, true);

        assertEquals(forgeList, modList.getClass().getMethod("getParent").invoke(modList));
        assertEquals(true, modEvent.getClass().getMethod("isCanceled").invoke(modEvent));
    }

    @Test
    void subscriberOfTheModBecomesPublic() throws ReflectiveOperationException {
        Class<?> handler = RemapToolkitTest.game.loadClass("example.mod.Handler");
        Class<?> event = RemapToolkitTest.game.loadClass("net.minecraftforge.client.event.GuiOpenEvent");

        assertTrue(Modifier.isPublic(handler.getModifiers()));
        assertTrue(Modifier.isPublic(handler.getMethod("onOpen", event).getModifiers()));
    }

    @Test
    void modClassesAndWarningsAreReported() {
        assertEquals(List.of("example.mod.ExampleMod"), RemapToolkitTest.preparedMod.modClassNames());
        assertEquals(List.of(), RemapToolkitTest.preparedMod.warnings());
        assertEquals(List.of(), RemapToolkitTest.toolkit.lastForgeWarnings());
    }

    @Test
    void preparedForgeJarIsUnsignedAndKeepsItsResources() throws IOException {
        Map<String, byte[]> entries = TestJars.read(RemapToolkitTest.preparedForge);
        String manifest = new String(entries.get("META-INF/MANIFEST.MF"), StandardCharsets.UTF_8);

        assertFalse(entries.containsKey("META-INF/FORGE.SF"));
        assertFalse(entries.containsKey("META-INF/FORGE.DSA"));
        assertFalse(manifest.contains("Digest"), manifest);
        assertFalse(manifest.contains("Class-Path"), manifest);
        assertArrayEquals(TestJars.read(RemapToolkitTest.universalJar).get("forge_at.cfg"), entries.get("forge_at.cfg"));
    }

    @Test
    void preparedModAndVanillaJarsKeepTheirResources() throws IOException {
        assertArrayEquals(TestJars.read(RemapToolkitTest.modJar).get("mcmod.info"),
                TestJars.read(RemapToolkitTest.preparedMod.jar()).get("mcmod.info"));
        assertArrayEquals(RemapToolkitTest.LANG_FILE,
                TestJars.read(RemapToolkitTest.preparedVanilla).get("assets/minecraft/lang/en_US.lang"));
    }

    @Test
    void overlaidClassesAreLeftOutWithTheirInnerClasses() throws IOException {
        Path jar = RemapToolkitTest.toolkit.prepareForge(RemapToolkitTest.universalJar,
                RemapToolkitTest.directory.resolve("out/forge-overlaid.jar"), Set.of("net/minecraftforge/fml/common/Loader"));

        Set<String> classes = TestJars.readClasses(jar).keySet();

        assertFalse(classes.contains("net/minecraftforge/fml/common/Loader"));
        assertFalse(classes.contains("net/minecraftforge/fml/common/Loader$Helper"));
        assertTrue(classes.contains("net/minecraftforge/client/ForgeScreen"));
    }

    @Test
    void withoutKnowingForgeTheDoubtAboutEventsIsReported() throws IOException {
        RemapToolkit fresh =
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, RemapToolkitTest.VERSION, RemapToolkitTest.vanillaJar);

        PreparedModJar prepared = fresh.prepareMod(RemapToolkitTest.modJar,
                RemapToolkitTest.directory.resolve("out/mod-without-forge.jar"), MemberShimTable.empty());

        assertTrue(prepared.warnings().stream().anyMatch(warning -> warning.contains("example/mod/ScreenShownEvent")),
                prepared.warnings().toString());
    }

    @Test
    void declaringAPreparedForgeJarGivesTheSameModAsPreparingForgeFirst() throws IOException {
        RemapToolkit fresh =
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, RemapToolkitTest.VERSION, RemapToolkitTest.vanillaJar);
        fresh.useForgeJar(RemapToolkitTest.preparedForge);

        PreparedModJar prepared = fresh.prepareMod(RemapToolkitTest.modJar,
                RemapToolkitTest.directory.resolve("out/mod-with-declared-forge.jar"),
                MemberShimTable.parse(RemapToolkitTest.SHIMS));

        assertEquals(List.of(), prepared.warnings());
        assertArrayEquals(Files.readAllBytes(RemapToolkitTest.preparedMod.jar()), Files.readAllBytes(prepared.jar()));
    }

    @Test
    void srgNamesComeFromTheMappingsJar() {
        assertEquals(
                Map.of("field_146294_l", "width", "func_73863_a", "drawScreen", "func_71410_x", "current",
                        "field_150746_k", "channel", "func_150254_d", "getText"),
                RemapToolkitTest.toolkit.srgNames().asMap());
    }

    @Test
    void fingerprintIsStableForTheSameInputs() {
        RemapToolkit other =
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, RemapToolkitTest.VERSION, RemapToolkitTest.vanillaJar);

        assertEquals(RemapToolkitTest.toolkit.fingerprint(), other.fingerprint());
        assertTrue(RemapToolkitTest.toolkit.fingerprint().matches("[0-9a-f]{32}"), RemapToolkitTest.toolkit.fingerprint());
    }

    @Test
    void fingerprintChangesWithTheMappingsTheGameJarOrTheVersion() throws IOException {
        Path updatedMappings = RemapToolkitTest.writeMappingsJar(
                RemapToolkitTest.directory.resolve("lunar-mappings-updated.jar"), "guiWidth");
        String reference = RemapToolkitTest.toolkit.fingerprint();

        assertNotEquals(reference,
                RemapToolkit.forLunar(updatedMappings, RemapToolkitTest.VERSION, RemapToolkitTest.vanillaJar).fingerprint());
        assertNotEquals(reference,
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, RemapToolkitTest.VERSION, RemapToolkitTest.universalJar)
                        .fingerprint());
        assertNotEquals(reference,
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, "1.7.10", RemapToolkitTest.vanillaJar).fingerprint());
    }

    @Test
    void missingMappingFileIsALoudError() {
        RemapToolkit wrongVersion =
                RemapToolkit.forLunar(RemapToolkitTest.mappingsJar, "1.7.10", RemapToolkitTest.vanillaJar);

        IOException error = assertThrows(IOException.class, () -> wrongVersion.prepareMod(RemapToolkitTest.modJar,
                RemapToolkitTest.directory.resolve("out/never-written.jar"), MemberShimTable.empty()));

        assertTrue(error.getMessage().contains("lunar/searge2lunar_1.7.10.kin"), error.getMessage());
        assertFalse(Files.exists(RemapToolkitTest.directory.resolve("out/never-written.jar")));
    }
}
