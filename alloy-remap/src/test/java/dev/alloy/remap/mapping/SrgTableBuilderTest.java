package dev.alloy.remap.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.SrgNameTable;
import dev.alloy.remap.testkit.KinClassSpec;
import dev.alloy.remap.testkit.KinFixture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SrgTableBuilderTest {

    private static SrgNameTable build(KinFixture kin) throws IOException {
        SrgTableBuilder builder = new SrgTableBuilder();
        new KinReader(builder).read(new ByteArrayInputStream(kin.toBytes()));
        return builder.build();
    }

    @Test
    void keepsOneEntryPerSrgNameWhateverTheOwner() throws IOException {
        // As in the real file, the same member is repeated on the class and its subclass.
        KinFixture kin = new KinFixture()
                .add(KinClassSpec.type("net/minecraft/entity/Entity", "net/minecraft/entity/Entity")
                        .field("field_70165_t", "D", "posX")
                        .method("func_70071_h_", "()V", "onUpdate"))
                .add(KinClassSpec.type("net/minecraft/entity/EntityLivingBase", "net/minecraft/entity/EntityLivingBase")
                        .field("field_70165_t", "D", "posX")
                        .method("func_70071_h_", "()V", "onUpdate"));

        SrgNameTable table = SrgTableBuilderTest.build(kin);

        assertEquals(Map.of("field_70165_t", "posX", "func_70071_h_", "onUpdate"), table.asMap());
    }

    @Test
    void rejectsSrgNameWithTwoDifferentTargets() {
        KinFixture kin = new KinFixture()
                .add(KinClassSpec.type("net/minecraft/A", "net/minecraft/A").method("func_1_a", "()V", "first"))
                .add(KinClassSpec.type("net/minecraft/B", "net/minecraft/B").method("func_1_a", "()V", "second"));

        MappingFormatException error =
                assertThrows(MappingFormatException.class, () -> SrgTableBuilderTest.build(kin));

        assertTrue(error.getMessage().contains("func_1_a"), error.getMessage());
    }
}
