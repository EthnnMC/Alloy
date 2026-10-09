package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.alloy.hooks.testing.BytecodeChecks;
import dev.alloy.hooks.testing.ClassHierarchy;
import dev.alloy.hooks.testing.MethodDump;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * Acceptance test on the real files of this machine: the whole catalog applied to Lunar 1.8.9's
 * real classes, and the bridge to its real class loader. Real classes are only read, patched and
 * analyzed, <b>never loaded</b>; the patched bytecode goes to {@code target/patched-classes} for
 * inspection with {@code javap}.
 *
 * <p>The files are located through system properties ({@code alloy.test.bakeClasses}: game
 * classes as Lunar runs them; {@code alloy.test.genesisJar}: Lunar's bootstrap jar, the local
 * install by default; {@code alloy.test.weavePatchedLoader}: game loader class already rewritten
 * by Weave). Each test <b>skips itself</b> if its files are absent, so the build stays green on
 * another machine.</p>
 */
class RealArtifactsTest {

    private static final Path OUTPUT_DIRECTORY = Path.of("target", "patched-classes");
    private static final String DEFAULT_GENESIS_JAR = ".lunarclient/offline/multiver/genesis-0.1.0-SNAPSHOT-all.jar";

    private final HookCatalog catalog = HookCatalog.forgeDefaults();
    private final ClassPatcher patcher = new ClassPatcher();
    private final GameLoaderBridgePatch bridgePatch = new GameLoaderBridgePatch();

    @Test
    void everyCatalogHookAppliesToTheRealGameClasses() throws IOException {
        Path bakeDirectory = RealArtifactsTest.bakeDirectory();
        HookReport report = this.newReport();

        Map<String, byte[]> patchedClasses = this.patchGameClasses(bakeDirectory, report);

        RealArtifactsTest.write("hook-report.txt", report.toString().getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> patched : patchedClasses.entrySet()) {
            RealArtifactsTest.write(patched.getKey() + ".class", patched.getValue());
        }
        assertTrue(report.isComplete(), report.toString());
    }

    @Test
    void patchedGameClassesPassTheBytecodeChecks() throws IOException {
        Path bakeDirectory = RealArtifactsTest.bakeDirectory();
        Map<String, byte[]> patchedClasses = this.patchGameClasses(bakeDirectory, this.newReport());

        List<String> introduced = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        for (Map.Entry<String, byte[]> patched : patchedClasses.entrySet()) {
            byte[] original = Files.readAllBytes(bakeDirectory.resolve(patched.getKey() + ".class"));
            ClassHierarchy hierarchy = new ClassHierarchy()
                    .withClass(patched.getValue()).withDirectory(bakeDirectory).withSystemResources();
            // Lunar's classes have quirks of their own: only what did not exist before us is a fault.
            List<String> before = BytecodeChecks.problems(original, hierarchy);
            List<String> after = BytecodeChecks.problems(patched.getValue(), hierarchy);
            List<String> added = new ArrayList<>(after);
            added.removeAll(before);
            introduced.addAll(added);
            summary.add(patched.getKey() + ": " + before.size() + " problem(s) before, " + after.size() + " after, "
                    + hierarchy.unresolvedNames().size() + " type(s) outside the checked hierarchy "
                    + new TreeSet<>(hierarchy.unresolvedNames()));
            before.forEach(problem -> summary.add("    already there: " + problem));
        }

        RealArtifactsTest.write("bytecode-checks.txt", String.join("\n", summary).getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(), introduced);
    }

    @Test
    void methodsWithoutHookCallAreLeftExactlyAsTheyWere() throws IOException {
        Path bakeDirectory = RealArtifactsTest.bakeDirectory();
        Map<String, byte[]> patchedClasses = this.patchGameClasses(bakeDirectory, this.newReport());

        List<String> altered = new ArrayList<>();
        for (Map.Entry<String, byte[]> patched : patchedClasses.entrySet()) {
            byte[] original = Files.readAllBytes(bakeDirectory.resolve(patched.getKey() + ".class"));
            Map<String, String> before = MethodDump.ofMethods(original);
            Map<String, String> after = MethodDump.ofMethods(patched.getValue());
            // No member added, removed or moved: the change can be replayed by retransformation.
            assertEquals(MethodDump.ofFields(original), MethodDump.ofFields(patched.getValue()), patched.getKey());
            assertEquals(List.copyOf(before.keySet()), List.copyOf(after.keySet()), patched.getKey());
            for (Map.Entry<String, String> method : after.entrySet()) {
                boolean hooked = method.getValue().contains(MethodDump.HOOK_CALL_MARK);
                if (!hooked && !method.getValue().equals(before.get(method.getKey()))) {
                    altered.add(patched.getKey() + "." + method.getKey());
                }
            }
        }

        assertEquals(List.of(), altered);
    }

    @Test
    void realGameLoaderClassIsRecognisedAndPatched() throws IOException {
        Path genesisJar = RealArtifactsTest.genesisJar();
        try (ZipFile jar = new ZipFile(genesisJar.toFile())) {
            Map<String, byte[]> patchedLoaders = new LinkedHashMap<>();
            for (ZipEntry entry : Collections.list(jar.entries())) {
                String className = entry.getName().replaceFirst("\\.class$", "");
                if (entry.getName().endsWith(".class") && this.bridgePatch.isCandidate(className)) {
                    byte[] original = jar.getInputStream(entry).readAllBytes();
                    this.bridgePatch.patch(original).ifPresent(patched -> patchedLoaders.put(className, patched));
                }
            }

            // Only one class of the package has the shape of the game loader.
            assertEquals(1, patchedLoaders.size(), patchedLoaders.keySet().toString());
            Map.Entry<String, byte[]> loader = patchedLoaders.entrySet().iterator().next();
            RealArtifactsTest.write(loader.getKey() + ".class", loader.getValue());
            byte[] original = jar.getInputStream(jar.getEntry(loader.getKey() + ".class")).readAllBytes();
            RealArtifactsTest.assertNoNewProblem(original, loader.getValue(), jar);
        }
    }

    @Test
    void gameLoaderClassAlreadyRewrittenByWeaveIsPatchedToo() throws IOException {
        Path weavePatchedLoader = RealArtifactsTest.existingPath("alloy.test.weavePatchedLoader", null);
        byte[] original = Files.readAllBytes(weavePatchedLoader);

        Optional<byte[]> patched = this.bridgePatch.patch(original);

        assertTrue(patched.isPresent());
        RealArtifactsTest.write("weave-then-alloy-game-loader.class", patched.get());
        try (ZipFile jar = new ZipFile(RealArtifactsTest.genesisJar().toFile())) {
            RealArtifactsTest.assertNoNewProblem(original, patched.get(), jar);
        }
    }

    /** Checks the patched loader, using the classes of Lunar's bootstrap jar as hierarchy. */
    private static void assertNoNewProblem(byte[] originalLoader, byte[] patchedLoader, ZipFile genesisJar) {
        ClassHierarchy hierarchy = new ClassHierarchy()
                .withClass(patchedLoader).withJar(genesisJar).withSystemResources();

        List<String> before = BytecodeChecks.problems(originalLoader, hierarchy);
        List<String> after = BytecodeChecks.problems(patchedLoader, hierarchy);

        assertEquals(before, after);
    }

    private HookReport newReport() {
        return new HookReport(this.catalog.hookIds());
    }

    /** Applies the catalog to each targeted class, read from the directory of real classes. */
    private Map<String, byte[]> patchGameClasses(Path bakeDirectory, HookReport report) throws IOException {
        Map<String, byte[]> patchedClasses = new LinkedHashMap<>();
        for (String className : this.catalog.classNames()) {
            Path classFile = bakeDirectory.resolve(className + ".class");
            if (Files.isRegularFile(classFile)) {
                byte[] original = Files.readAllBytes(classFile);
                this.patcher.patch(original, this.catalog.hooksFor(className), report)
                        .ifPresent(patched -> patchedClasses.put(className, patched));
            }
        }
        return patchedClasses;
    }

    private static Path bakeDirectory() {
        return RealArtifactsTest.existingPath("alloy.test.bakeClasses", null);
    }

    private static Path genesisJar() {
        Path defaultLocation = Path.of(System.getProperty("user.home"), RealArtifactsTest.DEFAULT_GENESIS_JAR);
        return RealArtifactsTest.existingPath("alloy.test.genesisJar", defaultLocation.toString());
    }

    /** Reads a path from a system property; the calling test is skipped if the file is missing. */
    private static Path existingPath(String property, String defaultValue) {
        String value = System.getProperty(property, defaultValue);
        assumeTrue(value != null && !value.isBlank(), "System property " + property + " is not set");
        Path path = Path.of(value);
        assumeTrue(Files.exists(path), property + " points to a missing file: " + path);
        return path;
    }

    private static void write(String relativeName, byte[] content) throws IOException {
        Path file = RealArtifactsTest.OUTPUT_DIRECTORY.resolve(relativeName);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
