package dev.alloy.remap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ClassScan;
import dev.alloy.remap.testkit.LocalArtifacts;
import dev.alloy.remap.testkit.MemberRef;
import dev.alloy.remap.testkit.RuntimeClasses;
import dev.alloy.remap.testkit.TestJars;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Prepares the real Forge 1.8.9 universal jar and checks the result, including against the classes
 * Lunar really runs. Skipped when the machine's files are absent.
 */
class ForgeUniversalLocalTest {

    private static final String EVENT = "net/minecraftforge/fml/common/eventhandler/Event";
    private static final String FORGE = "net/minecraftforge/";
    private static final String MINECRAFT = "net/minecraft/";
    private static final long NANOS_PER_MILLI = 1_000_000L;

    @TempDir
    static Path directory;

    private static Path universalJar;
    private static Path preparedJar;
    private static Path preparedAgain;
    private static RemapToolkit toolkit;

    /** Internal name to bytes, for the original jar and the prepared jar. */
    private static Map<String, byte[]> inputClasses;
    private static Map<String, byte[]> outputClasses;

    @BeforeAll
    static void prepare() throws IOException {
        ForgeUniversalLocalTest.universalJar = LocalArtifacts.forgeUniversal();
        ForgeUniversalLocalTest.toolkit = LocalArtifacts.toolkit();

        long start = System.nanoTime();
        ForgeUniversalLocalTest.preparedJar = ForgeUniversalLocalTest.toolkit.prepareForge(
                ForgeUniversalLocalTest.universalJar, ForgeUniversalLocalTest.directory.resolve("forge.jar"), Set.of());
        long firstCall = (System.nanoTime() - start) / ForgeUniversalLocalTest.NANOS_PER_MILLI;

        start = System.nanoTime();
        ForgeUniversalLocalTest.preparedAgain = ForgeUniversalLocalTest.toolkit.prepareForge(
                ForgeUniversalLocalTest.universalJar, ForgeUniversalLocalTest.directory.resolve("again.jar"), Set.of());
        long secondCall = (System.nanoTime() - start) / ForgeUniversalLocalTest.NANOS_PER_MILLI;

        LocalArtifacts.report("prepareForge wall time: " + firstCall + " ms for the first call (it also reads Lunar's"
                + " notch table and the headers of the vanilla jar), " + secondCall + " ms for the second call");
        ForgeUniversalLocalTest.inputClasses = TestJars.readClasses(ForgeUniversalLocalTest.universalJar);
        ForgeUniversalLocalTest.outputClasses = TestJars.readClasses(ForgeUniversalLocalTest.preparedJar);
    }

    private static HeaderTable outputHeaders() {
        List<ClassHeader> headers = new ArrayList<>();
        ForgeUniversalLocalTest.outputClasses.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        return HeaderTable.of(headers);
    }

    private static boolean isNotchName(String internalName) {
        // Obfuscated Minecraft classes are all in the default package.
        return internalName.indexOf('/') < 0;
    }

    @Test
    void preparingTwiceGivesTheSameFile() throws IOException {
        assertArrayEquals(
                Files.readAllBytes(ForgeUniversalLocalTest.preparedJar),
                Files.readAllBytes(ForgeUniversalLocalTest.preparedAgain));
    }

    @Test
    void everyClassIsKeptParsesAndPassesAsmDataFlowChecks() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : ForgeUniversalLocalTest.outputClasses.entrySet()) {
            try {
                // Checks the structure and, method by method, stack consistency (sizes included).
                new ClassReader(entry.getValue()).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
            } catch (RuntimeException e) {
                failures.add(entry.getKey() + ": " + e);
            }
        }
        LocalArtifacts.report("Forge classes: " + ForgeUniversalLocalTest.inputClasses.size() + " in, "
                + ForgeUniversalLocalTest.outputClasses.size() + " out, " + failures.size() + " failing ASM checks");

        assertEquals(List.of(), failures);
        assertEquals(1142, ForgeUniversalLocalTest.outputClasses.size());
        assertEquals(List.of(), ForgeUniversalLocalTest.toolkit.lastForgeWarnings());
    }

    @Test
    void noObfuscatedClassNameIsLeft() {
        Set<String> notchBefore = new TreeSet<>();
        int notchOwnerRefsBefore = 0;
        for (byte[] classFile : ForgeUniversalLocalTest.inputClasses.values()) {
            ClassScan.referencedClasses(classFile).stream()
                    .filter(ForgeUniversalLocalTest::isNotchName).forEach(notchBefore::add);
            notchOwnerRefsBefore += (int) ClassScan.memberReferences(classFile).stream()
                    .filter(reference -> ForgeUniversalLocalTest.isNotchName(reference.owner())).count();
        }
        Set<String> notchAfter = new TreeSet<>();
        List<String> notchOwnerRefsAfter = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : ForgeUniversalLocalTest.outputClasses.entrySet()) {
            ClassScan.referencedClasses(entry.getValue()).stream()
                    .filter(ForgeUniversalLocalTest::isNotchName).forEach(notchAfter::add);
            ClassScan.memberReferences(entry.getValue()).stream()
                    .filter(reference -> ForgeUniversalLocalTest.isNotchName(reference.owner()))
                    .forEach(reference -> notchOwnerRefsAfter.add(entry.getKey() + " -> " + reference));
        }
        LocalArtifacts.report("notch-named classes referenced: " + notchBefore.size() + " before, " + notchAfter.size()
                + " after; member references with a notch-named owner: " + notchOwnerRefsBefore + " before, "
                + notchOwnerRefsAfter.size() + " after");

        assertTrue(notchBefore.size() > 200, "the input is expected to be notch-named");
        assertEquals(Set.of(), notchAfter);
        assertEquals(List.of(), notchOwnerRefsAfter);
    }

    @Test
    void everyEventSubclassHasItsListenerListAndAPublicNoArgConstructor() {
        ClassHierarchy hierarchy = new ClassHierarchy(ForgeUniversalLocalTest.outputHeaders());
        int events = 0;
        List<String> incomplete = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : ForgeUniversalLocalTest.outputClasses.entrySet()) {
            ClassNode classNode = ClassBytes.read(entry.getValue());
            if (classNode.name.equals(ForgeUniversalLocalTest.EVENT)
                    || !hierarchy.superclassChain(classNode.name).contains(ForgeUniversalLocalTest.EVENT)) {
                continue;
            }
            events++;
            boolean listenerList = classNode.fields.stream().anyMatch(field -> field.name.equals("LISTENER_LIST")
                    && field.access == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC));
            boolean constructor = ForgeUniversalLocalTest.declares(classNode, "<init>", "()V", Opcodes.ACC_PUBLIC);
            boolean setup = ForgeUniversalLocalTest.declares(classNode, "setup", "()V", Opcodes.ACC_PROTECTED);
            boolean getter = ForgeUniversalLocalTest.declares(classNode, "getListenerList",
                    "()Lnet/minecraftforge/fml/common/eventhandler/ListenerList;", Opcodes.ACC_PUBLIC);
            if (!(listenerList && constructor && setup && getter)) {
                incomplete.add(classNode.name);
            }
        }
        LocalArtifacts.report("Forge event subclasses: " + events + ", incomplete: " + incomplete.size());

        assertEquals(List.of(), incomplete);
        assertEquals(245, events);
    }

    private static boolean declares(ClassNode classNode, String name, String descriptor, int access) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor) && (method.access & access) == access) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode outputClass(String internalName) {
        return ClassBytes.read(ForgeUniversalLocalTest.outputClasses.get(internalName));
    }

    private static String fieldType(String className, String fieldName) {
        for (FieldNode field : ForgeUniversalLocalTest.outputClass(className).fields) {
            if (field.name.equals(fieldName)) {
                return field.desc;
            }
        }
        return null;
    }

    @Test
    void wellKnownForgeClassesUseTheNamesOfTheGame() {
        assertEquals("Lnet/minecraft/client/gui/GuiScreen;",
                ForgeUniversalLocalTest.fieldType("net/minecraftforge/client/event/GuiOpenEvent", "gui"));
        assertEquals("Lnet/minecraft/util/IChatComponent;",
                ForgeUniversalLocalTest.fieldType("net/minecraftforge/client/event/ClientChatReceivedEvent", "message"));
        assertEquals("Lnet/minecraft/entity/player/EntityPlayer;", ForgeUniversalLocalTest.fieldType(
                "net/minecraftforge/fml/common/gameevent/TickEvent$PlayerTickEvent", "player"));

        ClassNode commandHandler = ForgeUniversalLocalTest.outputClass("net/minecraftforge/client/ClientCommandHandler");
        assertEquals("net/minecraft/command/CommandHandler", commandHandler.superName);
        // The override "int a(m, String)" must have taken the name of the Minecraft method.
        assertTrue(ForgeUniversalLocalTest.declares(commandHandler, "executeCommand",
                "(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;)I", Opcodes.ACC_PUBLIC));
    }

    @Test
    void cancelableEventGetsIsCancelableUnlessItAlreadyDefinesIt() {
        ClassNode overlay = ForgeUniversalLocalTest.outputClass("net/minecraftforge/client/event/RenderGameOverlayEvent");
        ClassNode post = ForgeUniversalLocalTest.outputClass("net/minecraftforge/client/event/RenderGameOverlayEvent$Post");

        // The parent class carries @Cancelable, so the method is added there. Post already overrides it
        // (answering "no"), so its own method is kept, without a duplicate.
        assertTrue(ForgeUniversalLocalTest.declares(overlay, "isCancelable", "()Z", Opcodes.ACC_PUBLIC));
        assertEquals(1, post.methods.stream().filter(method -> method.name.equals("isCancelable")).count());
    }

    @Test
    void newInnerClassesOfMinecraftFollowTheirOuterClass() {
        Set<String> moved = new TreeSet<>();
        for (String name : ForgeUniversalLocalTest.outputClasses.keySet()) {
            if (name.startsWith(ForgeUniversalLocalTest.MINECRAFT) && !name.startsWith(ForgeUniversalLocalTest.FORGE)) {
                moved.add(name);
            }
        }
        LocalArtifacts.report("classes of the universal jar that land under net/minecraft/: " + moved);

        assertEquals(13, moved.size());
        assertTrue(moved.stream().allMatch(name -> name.contains("$")), moved.toString());
    }

    @Test
    void referencesToMinecraftMembersExistInTheRealGame() throws IOException {
        RuntimeClasses runtime =
                new RuntimeClasses(LocalArtifacts.bakeClasses(), ForgeUniversalLocalTest.outputHeaders());
        Map<String, Set<String>> forgeAdded = ForgeUniversalLocalTest.readForgeAddedMembers();

        Set<MemberRef> references = new HashSet<>();
        for (Map.Entry<String, byte[]> entry : ForgeUniversalLocalTest.outputClasses.entrySet()) {
            if (entry.getKey().startsWith(ForgeUniversalLocalTest.FORGE)) {
                ClassScan.memberReferences(entry.getValue()).stream()
                        .filter(reference -> reference.owner().startsWith(ForgeUniversalLocalTest.MINECRAFT)
                                && !reference.owner().startsWith(ForgeUniversalLocalTest.FORGE))
                        .forEach(references::add);
            }
        }
        Map<String, List<MemberRef>> misses = new TreeMap<>();
        int found = 0;
        for (MemberRef reference : references) {
            if (runtime.resolve(reference) == RuntimeClasses.Resolution.FOUND) {
                found++;
            } else {
                String category = ForgeUniversalLocalTest.classify(reference, runtime, forgeAdded);
                misses.computeIfAbsent(category, key -> new ArrayList<>()).add(reference);
            }
        }
        int missCount = references.size() - found;
        LocalArtifacts.report(String.format("distinct references from Forge to Minecraft members: %d, found in the"
                + " real game: %d, missing: %d (%.2f %%)", references.size(), found, missCount,
                100.0 * missCount / references.size()));
        misses.forEach((category, list) -> {
            Set<String> owners = new TreeSet<>();
            list.forEach(reference -> owners.add(reference.owner()));
            LocalArtifacts.report("  missing, " + category + ": " + list.size() + " references on " + owners.size()
                    + " classes " + owners);
        });
        List<MemberRef> unexplained = misses.getOrDefault(ForgeUniversalLocalTest.UNEXPLAINED, List.of());
        unexplained.forEach(reference -> LocalArtifacts.report("  UNEXPLAINED " + reference));

        assertEquals(List.of(), unexplained);
        assertTrue(found > references.size() / 2, "most references must resolve");
    }

    private static final String UNEXPLAINED = "unexplained";

    /** Puts a missing reference in an expected category, or declares it unexplained. */
    private static String classify(MemberRef reference, RuntimeClasses runtime, Map<String, Set<String>> forgeAdded) {
        if (reference.owner().startsWith("net/minecraft/launchwrapper/")) {
            return "owner belongs to LaunchWrapper (a library of the Forge launcher, not Minecraft)";
        }
        if (!runtime.containsGameClass(reference.owner())) {
            return ForgeUniversalLocalTest.outputClasses.containsKey(reference.owner())
                    ? "owner is an inner class that Forge adds to Minecraft"
                    : "owner class is absent from the client runtime (dedicated-server class)";
        }
        Set<String> addedTo = forgeAdded.getOrDefault(ForgeUniversalLocalTest.memberId(reference), Set.of());
        for (String ancestor : runtime.ancestors(reference.owner())) {
            if (addedTo.contains(ancestor)) {
                return "member added to Minecraft by a Forge patch";
            }
        }
        if (reference.descriptor().contains(ForgeUniversalLocalTest.FORGE)) {
            return "descriptor uses a Forge type (cannot exist in an unpatched game)";
        }
        return ForgeUniversalLocalTest.UNEXPLAINED;
    }

    private static String memberId(MemberRef reference) {
        return (reference.field() ? "FIELD " : "METHOD ") + reference.name() + " " + reference.descriptor();
    }

    /**
     * Reads the reverse-engineered list of members that Forge's patches add to Minecraft classes.
     *
     * @return "kind name descriptor" to the Minecraft classes that receive this member
     */
    private static Map<String, Set<String>> readForgeAddedMembers() throws IOException {
        Map<String, Set<String>> added = new HashMap<>();
        Path file = LocalArtifacts.research("forge-added-members.tsv");
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] columns = line.split("\t", -1);
            if (columns.length >= 9 && columns[8].equals("ADDED_BY_FORGE")) {
                String id = columns[2] + " " + columns[3] + " " + columns[6];
                added.computeIfAbsent(id, key -> new HashSet<>()).add(columns[0]);
            }
        }
        return added;
    }

    @Test
    void overridesRenamedInForgeClassesMatchMethodsOfTheRealGame() {
        RuntimeClasses runtime =
                new RuntimeClasses(LocalArtifacts.bakeClasses(), ForgeUniversalLocalTest.outputHeaders());
        int renamed = 0;
        List<String> orphans = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : ForgeUniversalLocalTest.outputClasses.entrySet()) {
            byte[] original = ForgeUniversalLocalTest.inputClasses.get(entry.getKey());
            if (original == null || !entry.getKey().startsWith(ForgeUniversalLocalTest.FORGE)) {
                continue;
            }
            List<MethodNode> before = ClassBytes.read(original).methods;
            List<MethodNode> after = ClassBytes.read(entry.getValue()).methods;
            for (int i = 0; i < before.size(); i++) {
                MethodNode method = after.get(i);
                if (before.get(i).name.equals(method.name)) {
                    continue;
                }
                renamed++;
                // A Forge method is only renamed if it overrides a Minecraft method, so that method
                // must exist under this name on a real ancestor.
                boolean overridesRealMethod = false;
                for (String ancestor : runtime.ancestors(entry.getKey())) {
                    if (ancestor.startsWith(ForgeUniversalLocalTest.MINECRAFT)
                            && !ancestor.startsWith(ForgeUniversalLocalTest.FORGE)
                            && runtime.resolve(new MemberRef(false, ancestor, method.name, method.desc))
                                    == RuntimeClasses.Resolution.FOUND) {
                        overridesRealMethod = true;
                    }
                }
                if (!overridesRealMethod) {
                    orphans.add(entry.getKey() + "." + method.name + method.desc);
                }
            }
        }
        LocalArtifacts.report("method declarations renamed in Forge classes (overrides of Minecraft methods): "
                + renamed + ", without a matching method in the real game: " + orphans.size() + " " + orphans);

        assertTrue(renamed > 100, "Forge is expected to override many Minecraft methods");
        assertEquals(List.of(), orphans);
    }

    @Test
    void signatureIsRemovedAndOtherResourcesAreUntouched() throws IOException {
        Map<String, byte[]> input = TestJars.read(ForgeUniversalLocalTest.universalJar);
        Map<String, byte[]> output = TestJars.read(ForgeUniversalLocalTest.preparedJar);

        int compared = 0;
        for (Map.Entry<String, byte[]> entry : input.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith(".class") || name.startsWith("META-INF/")) {
                continue;
            }
            assertArrayEquals(entry.getValue(), output.get(name), name);
            compared++;
        }
        Manifest manifest = new Manifest(new ByteArrayInputStream(output.get("META-INF/MANIFEST.MF")));
        LocalArtifacts.report("resources identical byte for byte: " + compared + "; entries: " + input.size() + " in, "
                + output.size() + " out");

        assertFalse(output.containsKey("META-INF/FORGE.SF"));
        assertFalse(output.containsKey("META-INF/FORGE.DSA"));
        assertTrue(manifest.getEntries().isEmpty(), "per-file digests must be gone");
        assertNull(manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH));
        assertEquals("net.minecraftforge.fml.common.launcher.FMLTweaker",
                manifest.getMainAttributes().getValue("TweakClass"));
        assertEquals(input.size() - 2, output.size());
    }

    @Test
    void overlaidClassesAndTheirInnerClassesAreLeftOut() throws IOException {
        Set<String> overlays = Set.of(
                "net/minecraftforge/fml/common/Loader", "net/minecraftforge/fml/common/ModClassLoader");

        Path jar = ForgeUniversalLocalTest.toolkit.prepareForge(ForgeUniversalLocalTest.universalJar,
                ForgeUniversalLocalTest.directory.resolve("overlaid.jar"), overlays);

        Set<String> remaining = TestJars.readClasses(jar).keySet();
        Set<String> removed = new TreeSet<>(ForgeUniversalLocalTest.outputClasses.keySet());
        removed.removeAll(remaining);
        LocalArtifacts.report("classes left out for 2 overlays: " + removed);

        assertTrue(removed.containsAll(overlays));
        assertTrue(removed.stream().allMatch(name -> overlays.contains(name)
                || name.startsWith("net/minecraftforge/fml/common/Loader$")
                || name.startsWith("net/minecraftforge/fml/common/ModClassLoader$")), removed.toString());
        assertTrue(remaining.contains("net/minecraftforge/fml/common/LoaderState"));
    }
}
