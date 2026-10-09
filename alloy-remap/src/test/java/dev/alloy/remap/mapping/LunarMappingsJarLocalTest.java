package dev.alloy.remap.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.SrgNameTable;
import dev.alloy.remap.io.Sha256;
import dev.alloy.remap.testkit.LocalArtifacts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reads the real Lunar {@code .kin} files and compares them with the counts from the reverse
 * engineering report ({@code mappings.md}, section 4). Skipped if the Lunar jar is absent or is
 * another build than the measured one.
 */
class LunarMappingsJarLocalTest {

    /** Hash of the Lunar jar the reference counts were measured on. */
    private static final String MEASURED_BUILD = "5d53fd9767c085475ad139fd7e667a28cb3c0fedaf526b495efc8fe515add521";

    /** Expected counts: top-level classes, inner classes, fields, methods. */
    private static final Map<String, KinSummary> EXPECTED = new LinkedHashMap<>();

    static {
        LunarMappingsJarLocalTest.EXPECTED.put("forge/mcp_searge_1.8.9.kin", new KinSummary(1606, 911, 8737, 15072));
        LunarMappingsJarLocalTest.EXPECTED.put("lunar/lunar_named_b5_1.8.9.kin", new KinSummary(1606, 911, 8737, 15072));
        LunarMappingsJarLocalTest.EXPECTED.put("lunar/searge2lunar_1.8.9.kin", new KinSummary(1557, 489, 27925, 73179));
        LunarMappingsJarLocalTest.EXPECTED.put("lunar/lunar2searge_1.8.9.kin", new KinSummary(1557, 489, 27925, 73179));
        LunarMappingsJarLocalTest.EXPECTED.put("v1_8_inflight_vanilla.kin", new KinSummary(1470, 629, 3716, 46087));
        LunarMappingsJarLocalTest.EXPECTED.put("v1_8_inflight_optifine.kin", new KinSummary(1470, 632, 3870, 46481));
        LunarMappingsJarLocalTest.EXPECTED.put("v1_8_inflight_forge.kin", new KinSummary(1475, 657, 3698, 53874));
        LunarMappingsJarLocalTest.EXPECTED.put("v1_8_inflight_optiforge.kin", new KinSummary(1475, 653, 3821, 54183));
    }

    private static Path mappingsJar;

    @BeforeAll
    static void locate() throws IOException {
        LunarMappingsJarLocalTest.mappingsJar = LocalArtifacts.lunarMappings();
        Assumptions.assumeTrue(
                LunarMappingsJarLocalTest.MEASURED_BUILD.equals(Sha256.ofFile(LunarMappingsJarLocalTest.mappingsJar)),
                "another build of Lunar's mapping jar: the reference numbers do not apply");
    }

    /** Keeps nothing; only the reader's summary matters. */
    private static final class IgnoringHandler implements KinRecordHandler {

        @Override
        public void visitClass(String sourceName, String targetName) {
            // Nothing to keep.
        }

        @Override
        public void visitField(String sourceOwner, String sourceName, String sourceDescriptor, String targetName) {
            // Nothing to keep.
        }

        @Override
        public void visitMethod(String sourceOwner, String sourceName, String sourceDescriptor, String targetName) {
            // Nothing to keep.
        }
    }

    @Test
    void everyKinFileDecodesCompletelyWithTheMeasuredCounts() throws IOException {
        Map<String, KinSummary> decoded = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(LunarMappingsJarLocalTest.mappingsJar.toFile())) {
            for (String entryName : LunarMappingsJarLocalTest.EXPECTED.keySet()) {
                try (InputStream input = zip.getInputStream(zip.getEntry(entryName))) {
                    // The reader rejects any extra byte, so a returned summary proves a complete read.
                    decoded.put(entryName, new KinReader(new IgnoringHandler()).read(input));
                }
            }
        }
        decoded.forEach((name, summary) -> LocalArtifacts.report("decoded " + name + ": " + summary));

        assertEquals(LunarMappingsJarLocalTest.EXPECTED, decoded);
    }

    @Test
    void flatSrgTableEqualsTheExportedReferenceTable() throws IOException {
        Path reference = LocalArtifacts.research("mappings/export/srg2lunar-global_1.8.9.tsv");
        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(reference, StandardCharsets.UTF_8)) {
            if (!line.startsWith("#") && !line.isBlank()) {
                String[] columns = line.split("\t");
                expected.put(columns[0], columns[1]);
            }
        }

        SrgNameTable table =
                new LunarMappingsJar(LunarMappingsJarLocalTest.mappingsJar, LocalArtifacts.MINECRAFT_VERSION).srgNames();

        LocalArtifacts.report("flat SRG table: " + table.size() + " names, reference TSV: " + expected.size());
        assertEquals(13165, table.size());
        assertEquals(expected, new TreeMap<>(table.asMap()));
    }

    @Test
    void notchTableKnowsEveryClassOfTheNotchFile() throws IOException {
        NotchMappings mappings =
                new LunarMappingsJar(LunarMappingsJarLocalTest.mappingsJar, LocalArtifacts.MINECRAFT_VERSION)
                        .notchMappings();

        assertEquals(1606 + 911, mappings.classCount());
        assertEquals("net/minecraft/client/Minecraft", mappings.className("ave"));
        assertEquals("net/minecraft/client/gui/GuiScreenBook$NextPageButton", mappings.className("ayo$a"));
        assertTrue(mappings.className("ady$FlowerEntry").endsWith("$FlowerEntry"));
    }
}
