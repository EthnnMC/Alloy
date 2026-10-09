package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ForgeStubs;
import dev.alloy.remap.testkit.SourceCompiler;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ModClassCollectorTest {

    @Test
    void collectsDottedNamesOfClassesAnnotatedWithMod() {
        Map<String, byte[]> compiled = SourceCompiler.compile(8,
                ForgeStubs.MOD,
                """
                package example.mod;
                import net.minecraftforge.fml.common.Mod;
                @Mod(modid = "example")
                public class ExampleMod {
                }
                """,
                """
                package example.mod;
                public class Helper {
                }
                """);
        ModClassCollector collector = new ModClassCollector();

        ClassBytes.applyToAll(compiled, collector);

        assertEquals(List.of("example.mod.ExampleMod"), collector.modClassNames());
    }
}
