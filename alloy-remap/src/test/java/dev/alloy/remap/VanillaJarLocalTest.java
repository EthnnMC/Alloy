package dev.alloy.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.hierarchy.MemberKey;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.LocalArtifacts;
import dev.alloy.remap.testkit.RuntimeClasses;
import dev.alloy.remap.testkit.SourceCompiler;
import dev.alloy.remap.testkit.TestJars;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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
 * Builds the Minecraft jar with game names from the real Mojang jar and compares it with the
 * classes Lunar really runs. Skipped when the machine's files are absent.
 */
class VanillaJarLocalTest {

    private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    @TempDir
    static Path directory;

    private static Path namedJar;

    /** Internal name to bytes of the classes in the produced jar. */
    private static Map<String, byte[]> outputClasses;

    @BeforeAll
    static void prepare() throws IOException {
        RemapToolkit toolkit = LocalArtifacts.toolkit();

        long start = System.nanoTime();
        VanillaJarLocalTest.namedJar = toolkit.prepareVanilla(VanillaJarLocalTest.directory.resolve("minecraft.jar"));
        long elapsed = (System.nanoTime() - start) / VanillaJarLocalTest.NANOS_PER_MILLI;

        VanillaJarLocalTest.outputClasses = TestJars.readClasses(VanillaJarLocalTest.namedJar);
        LocalArtifacts.report("prepareVanilla wall time: " + elapsed + " ms, "
                + VanillaJarLocalTest.outputClasses.size() + " classes, "
                + Files.size(VanillaJarLocalTest.namedJar) + " bytes");
    }

    @Test
    void everyClassIsRenamedParsesAndPassesAsmDataFlowChecks() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : VanillaJarLocalTest.outputClasses.entrySet()) {
            try {
                new ClassReader(entry.getValue()).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
            } catch (RuntimeException e) {
                failures.add(entry.getKey() + ": " + e);
            }
        }

        assertEquals(List.of(), failures);
        assertEquals(2507, VanillaJarLocalTest.outputClasses.size());
        assertTrue(VanillaJarLocalTest.outputClasses.keySet().stream().allMatch(name -> name.startsWith("net/minecraft/")));
    }

    @Test
    void classesAndDeclaredMembersCarryTheNamesOfTheRealGame() {
        RuntimeClasses runtime = new RuntimeClasses(LocalArtifacts.bakeClasses(), HeaderTable.empty());
        int classesFound = 0;
        int fields = 0;
        int methods = 0;
        List<String> missingFields = new ArrayList<>();
        List<String> missingMethods = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : VanillaJarLocalTest.outputClasses.entrySet()) {
            Optional<ClassHeader> real = runtime.find(entry.getKey());
            if (real.isEmpty() || !runtime.containsGameClass(entry.getKey())) {
                continue;
            }
            classesFound++;
            ClassHeader ours = ClassHeader.read(entry.getValue());
            for (MemberKey field : ours.fields()) {
                fields++;
                if (!real.get().declaresField(field)) {
                    missingFields.add(entry.getKey() + "." + field.name() + " " + field.descriptor());
                }
            }
            for (MemberKey method : ours.methods()) {
                methods++;
                if (!real.get().declaresMethod(method)) {
                    missingMethods.add(entry.getKey() + "." + method.name() + method.descriptor());
                }
            }
        }
        LocalArtifacts.report("named vanilla jar vs real game: classes " + classesFound + "/"
                + VanillaJarLocalTest.outputClasses.size() + "; declared fields found on the same class "
                + (fields - missingFields.size()) + "/" + fields + "; declared methods " + (methods - missingMethods.size())
                + "/" + methods + " (static initialisers included)");
        LocalArtifacts.report("  methods not in the real game (OptiFine-patched classes): " + missingMethods);

        // Same numbers as the reverse engineering (mappings.md, section 7): the gaps are the members
        // OptiFine removes or changes in the classes it replaces.
        assertEquals(2507, classesFound);
        assertEquals(109, missingFields.size());
        assertEquals(11, missingMethods.size());
    }

    @Test
    void everythingASourceFileCanNameIsPublic() {
        List<String> hidden = new ArrayList<>();
        for (byte[] classFile : VanillaJarLocalTest.outputClasses.values()) {
            ClassNode classNode = ClassBytes.read(classFile);
            if ((classNode.access & Opcodes.ACC_PUBLIC) == 0 || (classNode.access & Opcodes.ACC_FINAL) != 0) {
                hidden.add(classNode.name);
            }
            for (FieldNode field : classNode.fields) {
                if ((field.access & Opcodes.ACC_SYNTHETIC) == 0
                        && (field.access & VanillaJarLocalTest.VISIBILITY) != Opcodes.ACC_PUBLIC) {
                    hidden.add(classNode.name + "." + field.name);
                }
            }
            for (MethodNode method : classNode.methods) {
                if ((method.access & Opcodes.ACC_SYNTHETIC) == 0 && !method.name.equals("<clinit>")
                        && (method.access & (VanillaJarLocalTest.VISIBILITY | Opcodes.ACC_FINAL)) != Opcodes.ACC_PUBLIC) {
                    hidden.add(classNode.name + "." + method.name + method.desc);
                }
            }
        }

        assertEquals(List.of(), hidden);
    }

    @Test
    void accessFlagsAgreeWithTheRealGame() throws IOException {
        Path bake = LocalArtifacts.bakeClasses();
        Map<String, Integer> differences = new TreeMap<>();
        List<String> constants = new ArrayList<>();
        int compared = 0;
        for (Map.Entry<String, byte[]> entry : VanillaJarLocalTest.outputClasses.entrySet()) {
            Path realFile = bake.resolve(entry.getKey() + ".class");
            if (!Files.isRegularFile(realFile)) {
                continue;
            }
            ClassNode real = ClassBytes.read(Files.readAllBytes(realFile));
            ClassNode named = ClassBytes.read(entry.getValue());
            boolean isInterface = (named.access & Opcodes.ACC_INTERFACE) != 0;
            for (FieldNode ours : named.fields) {
                for (FieldNode theirs : real.fields) {
                    if (ours.name.equals(theirs.name) && ours.desc.equals(theirs.desc)) {
                        compared++;
                        String kind = (isInterface ? "interface " : "") + "field" + (ours.value != null ? " (constant)" : "");
                        VanillaJarLocalTest.compare(kind, ours.access, theirs.access, differences);
                        if (ours.value != null && !isInterface) {
                            constants.add(named.name + "." + ours.name + " = " + ours.value);
                        }
                    }
                }
            }
            for (MethodNode ours : named.methods) {
                for (MethodNode theirs : real.methods) {
                    if (ours.name.equals(theirs.name) && ours.desc.equals(theirs.desc)) {
                        compared++;
                        String kind = ours.name.equals("<clinit>") ? "static initialiser" : "method";
                        VanillaJarLocalTest.compare(kind, ours.access, theirs.access, differences);
                    }
                }
            }
        }
        int different = differences.values().stream().mapToInt(Integer::intValue).sum();
        LocalArtifacts.report("access flags (visibility + final) vs real game: " + compared + " common members, "
                + different + " differ: " + differences);
        LocalArtifacts.report("  compile-time constants of classes (final kept on purpose): " + constants);

        // Only allowed differences, none visible to source code: synthetic members and static
        // initialisers (no source can name them, and Lunar partly widens them), and the "final" kept
        // on purpose on compile-time constants.
        for (String difference : differences.keySet()) {
            assertTrue(difference.contains("synthetic") || difference.contains("(constant)")
                    || difference.contains("static initialiser"), difference);
        }
    }

    /** Records, by category, a member whose visibility or final flag differs from the real game's. */
    private static void compare(String kind, int ours, int theirs, Map<String, Integer> differences) {
        int mask = VanillaJarLocalTest.VISIBILITY | Opcodes.ACC_FINAL;
        if ((ours & mask) != (theirs & mask)) {
            String synthetic = (ours & Opcodes.ACC_SYNTHETIC) != 0 ? "synthetic " : "";
            String key = synthetic + kind + ": ours " + VanillaJarLocalTest.flags(ours) + ", real "
                    + VanillaJarLocalTest.flags(theirs);
            differences.merge(key, 1, Integer::sum);
        }
    }

    private static String flags(int access) {
        String visibility = (access & Opcodes.ACC_PUBLIC) != 0 ? "public"
                : (access & Opcodes.ACC_PROTECTED) != 0 ? "protected"
                : (access & Opcodes.ACC_PRIVATE) != 0 ? "private" : "package";
        return visibility + ((access & Opcodes.ACC_FINAL) != 0 ? " final" : "");
    }

    @Test
    void signatureOfMojangIsRemoved() throws IOException {
        Map<String, byte[]> entries = TestJars.read(VanillaJarLocalTest.namedJar);

        assertFalse(entries.containsKey("META-INF/MOJANGCS.SF"));
        assertFalse(entries.containsKey("META-INF/MOJANGCS.RSA"));
        assertTrue(entries.containsKey("assets/minecraft/lang/en_US.lang"));
    }

    @Test
    void sourceUsingAMemberThatMojangDeclaredPrivateCompilesAgainstTheJar() {
        // rightClickDelayTimer is private in the Mojang jar and public in the game Lunar runs.
        Map<String, byte[]> compiled = SourceCompiler.compile(8, List.of(VanillaJarLocalTest.namedJar),
                """
                package example.forge;
                import net.minecraft.client.Minecraft;
                public class Probe {
                    public static int delay() { return Minecraft.getMinecraft().rightClickDelayTimer; }
                    public static String name(net.minecraft.entity.Entity entity) { return entity.getName(); }
                }
                """);

        assertTrue(compiled.containsKey("example/forge/Probe"));
    }
}
