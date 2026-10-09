package dev.alloy.remap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.jar.JarSanitizer;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ClassScan;
import dev.alloy.remap.testkit.LocalArtifacts;
import dev.alloy.remap.testkit.MemberRef;
import dev.alloy.remap.testkit.RuntimeClasses;
import dev.alloy.remap.testkit.TestJars;
import dev.alloy.remap.transform.Annotations;
import dev.alloy.remap.transform.ForgeNames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Prepares a real Forge 1.8.9 mod of this machine ({@code -Dalloy.test.modJar=<jar>}) and checks,
 * against the real game classes, what must hold for any mod Alloy claims to load. Skipped when the
 * machine's files are absent.
 */
class ModJarLocalTest {

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String CLASS_SUFFIX = ".class";
    private static final String LDC_PREFIX = "LDC ";

    /** A mod's own copy of Mixin never runs under Alloy, which brings its own. */
    private static final String BUNDLED_MIXIN = "org/spongepowered/";

    /** The launcher Forge mods expect; not part of the game. */
    private static final String LAUNCH_WRAPPER = "net/minecraft/launchwrapper/";

    /** Entries the toolkit adds to a prepared jar. */
    private static final String ALLOY_ENTRIES = "META-INF/alloy/";

    /** A Forge-added member Alloy replaces: {@code NetworkManager.channel()} becomes a field read. */
    private static final MemberShimTable SHIMS = MemberShimTable.parse("method\tnet/minecraft/network/NetworkManager"
            + "\tchannel\t()Lio/netty/channel/Channel;\tgetfield\tchannel\tLio/netty/channel/Channel;");

    @TempDir
    static Path directory;

    private static Path modJar;
    private static PreparedModJar prepared;
    private static SrgNameTable names;

    /** Internal name to bytes, for the original mod and the prepared mod. */
    private static Map<String, byte[]> inputClasses;
    private static Map<String, byte[]> outputClasses;

    @BeforeAll
    static void prepare() throws IOException {
        ModJarLocalTest.modJar = LocalArtifacts.modJar();
        RemapToolkit toolkit = LocalArtifacts.toolkit();

        long start = System.nanoTime();
        ModJarLocalTest.prepared = toolkit.prepareMod(
                ModJarLocalTest.modJar, ModJarLocalTest.directory.resolve("mod.jar"), ModJarLocalTest.SHIMS);
        long firstCall = (System.nanoTime() - start) / ModJarLocalTest.NANOS_PER_MILLI;

        start = System.nanoTime();
        toolkit.prepareMod(ModJarLocalTest.modJar, ModJarLocalTest.directory.resolve("again.jar"), ModJarLocalTest.SHIMS);
        long secondCall = (System.nanoTime() - start) / ModJarLocalTest.NANOS_PER_MILLI;

        LocalArtifacts.report("prepareMod wall time: " + firstCall + " ms for the first call (it also reads Lunar's"
                + " searge2lunar table), " + secondCall + " ms for the second call");
        LocalArtifacts.report("prepareMod warnings: " + ModJarLocalTest.prepared.warnings());
        ModJarLocalTest.names = toolkit.srgNames();
        ModJarLocalTest.inputClasses = TestJars.readClasses(ModJarLocalTest.modJar);
        ModJarLocalTest.outputClasses = TestJars.readClasses(ModJarLocalTest.prepared.jar());
    }

    @Test
    void noSrgNameRemainsAsAMemberName() {
        Set<String> leftovers = new TreeSet<>();
        ModJarLocalTest.outputClasses.values().forEach(classFile -> leftovers.addAll(ClassScan.memberNames(classFile)));
        leftovers.retainAll(ModJarLocalTest.names.asMap().keySet());

        assertEquals(Set.of(), leftovers);
    }

    @Test
    void methodsDeclaredWithAnSrgNameAreRenamed() {
        List<String> renamed = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : ModJarLocalTest.inputClasses.entrySet()) {
            byte[] output = ModJarLocalTest.outputClasses.get(entry.getKey());
            if (output == null) {
                continue;
            }
            Set<String> after = new LinkedHashSet<>();
            ClassBytes.read(output).methods.forEach(method -> after.add(method.name + method.desc));
            for (MethodNode before : ClassBytes.read(entry.getValue()).methods) {
                Optional<String> runtimeName = ModJarLocalTest.names.find(before.name);
                // A method of the other side is removed, not renamed.
                boolean removed = Annotations.isPresent(before.visibleAnnotations, ForgeNames.SIDE_ONLY_ANNOTATION);
                if (runtimeName.isPresent() && !removed) {
                    assertTrue(after.contains(runtimeName.get() + before.desc), entry.getKey() + "." + before.name);
                    renamed.add(entry.getKey() + "." + before.name + " -> " + runtimeName.get());
                }
            }
        }
        LocalArtifacts.report("SRG-named method declarations renamed: " + renamed.size() + " " + renamed);
    }

    @Test
    void noSrgNameRemainsAsAStringConstant() {
        Set<String> leftovers = new TreeSet<>();
        for (byte[] classFile : ModJarLocalTest.outputClasses.values()) {
            for (String line : ClassScan.describe(classFile)) {
                boolean srgConstant = line.startsWith(ModJarLocalTest.LDC_PREFIX)
                        && ModJarLocalTest.names.find(line.substring(ModJarLocalTest.LDC_PREFIX.length())).isPresent();
                if (srgConstant) {
                    leftovers.add(line);
                }
            }
        }

        assertEquals(Set.of(), leftovers);
    }

    /**
     * Looks up in the real game classes each member the mod references on a Minecraft class or on
     * one of its own classes (which may inherit it).
     */
    @Test
    void everyReferenceToAMinecraftMemberExistsInTheRealGame() throws IOException {
        List<ClassHeader> headers = new ArrayList<>();
        ModJarLocalTest.outputClasses.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        RuntimeClasses runtime = new RuntimeClasses(LocalArtifacts.bakeClasses(), HeaderTable.of(headers));

        List<String> missing = new ArrayList<>();
        Set<String> undecidable = new TreeSet<>();
        int checked = 0;
        for (Map.Entry<String, byte[]> entry : ModJarLocalTest.outputClasses.entrySet()) {
            if (entry.getKey().startsWith(ModJarLocalTest.BUNDLED_MIXIN)) {
                continue;
            }
            for (MemberRef reference : ClassScan.memberReferences(entry.getValue())) {
                boolean gameOwner = reference.owner().startsWith(ForgeNames.MINECRAFT_PACKAGE)
                        && !reference.owner().startsWith(ModJarLocalTest.LAUNCH_WRAPPER);
                if (!gameOwner && !ModJarLocalTest.outputClasses.containsKey(reference.owner())) {
                    continue;
                }
                checked++;
                RuntimeClasses.Resolution resolution = runtime.resolve(reference);
                if (resolution == RuntimeClasses.Resolution.MISSING) {
                    missing.add(entry.getKey() + " -> " + reference);
                } else if (resolution == RuntimeClasses.Resolution.UNKNOWN_ANCESTOR) {
                    // The member comes from a library missing from the classes directory: undecidable here.
                    undecidable.add(reference.toString());
                }
            }
        }
        LocalArtifacts.report("mod member references checked against the real game: " + checked + ", missing: "
                + missing.size() + ", inherited from a library outside the game classes: " + undecidable.size()
                + " " + undecidable);

        assertEquals(List.of(), missing);
    }

    @Test
    void modClassesAreDiscovered() {
        Set<String> expected = new TreeSet<>();
        ModJarLocalTest.inputClasses.forEach((name, classFile) -> {
            if (Annotations.isPresent(ClassBytes.read(classFile).visibleAnnotations, ForgeNames.MOD_ANNOTATION)) {
                expected.add(name.replace('/', '.'));
            }
        });

        assertEquals(List.copyOf(expected), ModJarLocalTest.prepared.modClassNames());
    }

    @Test
    void classAnnotationsAreKept() {
        for (Map.Entry<String, byte[]> entry : ModJarLocalTest.outputClasses.entrySet()) {
            ClassNode before = ClassBytes.read(ModJarLocalTest.inputClasses.get(entry.getKey()));
            ClassNode after = ClassBytes.read(entry.getValue());

            assertEquals(ModJarLocalTest.descriptorsOf(before.visibleAnnotations),
                    ModJarLocalTest.descriptorsOf(after.visibleAnnotations), entry.getKey());
        }
    }

    @Test
    void eventHandlersAndTheirClassArePublic() {
        int handlers = 0;
        for (byte[] classFile : ModJarLocalTest.outputClasses.values()) {
            ClassNode classNode = ClassBytes.read(classFile);
            for (MethodNode method : classNode.methods) {
                if (Annotations.isPresent(method.visibleAnnotations, ForgeNames.SUBSCRIBE_EVENT_ANNOTATION)) {
                    handlers++;
                    assertTrue((method.access & Opcodes.ACC_PUBLIC) != 0, classNode.name + "." + method.name);
                    assertTrue((classNode.access & Opcodes.ACC_PUBLIC) != 0, classNode.name);
                }
            }
        }
        LocalArtifacts.report("event handler methods found: " + handlers);
    }

    @Test
    void everyPreparedClassStillPassesAsmDataFlowChecks() {
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : ModJarLocalTest.outputClasses.entrySet()) {
            byte[] original = ModJarLocalTest.inputClasses.get(entry.getKey());
            // Only what the preparation broke counts: some published classes do not pass on their own.
            if (ModJarLocalTest.passesChecks(original) && !ModJarLocalTest.passesChecks(entry.getValue())) {
                broken.add(entry.getKey());
            }
        }
        LocalArtifacts.report("classes: " + ModJarLocalTest.inputClasses.size() + " in, "
                + ModJarLocalTest.outputClasses.size() + " out");

        assertEquals(List.of(), broken);
        assertTrue(ModJarLocalTest.inputClasses.keySet().containsAll(ModJarLocalTest.outputClasses.keySet()));
    }

    @Test
    void resourcesAreKept() throws IOException {
        Map<String, byte[]> input = TestJars.read(ModJarLocalTest.modJar);
        Map<String, byte[]> output = TestJars.read(ModJarLocalTest.prepared.jar());

        int compared = 0;
        for (Map.Entry<String, byte[]> entry : input.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith(ModJarLocalTest.CLASS_SUFFIX) || JarSanitizer.isSignatureFile(name)) {
                continue;
            }
            assertTrue(output.containsKey(name), name);
            // The manifest loses its signature entries and reference maps get game names; the rest is untouched.
            boolean rewritten = name.equals(JarFile.MANIFEST_NAME) || name.endsWith(".json") && name.contains("refmap");
            if (!rewritten) {
                assertArrayEquals(entry.getValue(), output.get(name), name);
                compared++;
            }
        }
        LocalArtifacts.report("mod resources identical byte for byte: " + compared + "; entries: " + input.size()
                + " in, " + output.size() + " out");

        for (String name : output.keySet()) {
            assertTrue(input.containsKey(name) || name.startsWith(ModJarLocalTest.ALLOY_ENTRIES), name);
        }
    }

    @Test
    void preparingTwiceGivesTheSameFile() throws IOException {
        assertArrayEquals(
                Files.readAllBytes(ModJarLocalTest.prepared.jar()),
                Files.readAllBytes(ModJarLocalTest.directory.resolve("again.jar")));
    }

    private static List<String> descriptorsOf(List<AnnotationNode> annotations) {
        List<String> descriptors = new ArrayList<>();
        if (annotations != null) {
            annotations.forEach(annotation -> descriptors.add(annotation.desc));
        }
        return descriptors;
    }

    private static boolean passesChecks(byte[] classFile) {
        try {
            new ClassReader(classFile).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
