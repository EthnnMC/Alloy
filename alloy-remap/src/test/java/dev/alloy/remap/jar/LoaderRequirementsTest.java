package dev.alloy.remap.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

class LoaderRequirementsTest {

    private static List<String> warningsFor(String manifest) throws IOException {
        List<String> warnings = new ArrayList<>();
        LoaderRequirements.report(
                Map.of(JarFile.MANIFEST_NAME, manifest.getBytes(StandardCharsets.UTF_8)), warnings::add);
        return warnings;
    }

    @Test
    void coremodAndTweakerAreEachReported() throws IOException {
        List<String> warnings = LoaderRequirementsTest.warningsFor("""
                Manifest-Version: 1.0
                TweakClass: example.ExampleTweaker
                FMLCorePlugin: example.CorePlugin

                """);

        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("example.CorePlugin"), warnings.get(0));
        assertTrue(warnings.get(1).contains("example.ExampleTweaker"), warnings.get(1));
    }

    @Test
    void mixinTweakerIsNotReported() throws IOException {
        List<String> warnings = LoaderRequirementsTest.warningsFor("""
                Manifest-Version: 1.0
                TweakClass: org.spongepowered.asm.launch.MixinTweaker
                MixinConfigs: mixins.example.json

                """);

        assertEquals(List.of(), warnings);
    }

    @Test
    void plainModIsNotReported() throws IOException {
        assertEquals(List.of(), LoaderRequirementsTest.warningsFor("Manifest-Version: 1.0\n\n"));
    }

    @Test
    void jarWithoutManifestIsNotReported() throws IOException {
        List<String> warnings = new ArrayList<>();

        LoaderRequirements.report(Map.of(), warnings::add);

        assertEquals(List.of(), warnings);
    }
}
