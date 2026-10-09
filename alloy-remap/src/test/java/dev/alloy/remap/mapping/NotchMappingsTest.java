package dev.alloy.remap.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.MemberKey;
import dev.alloy.remap.testkit.KinClassSpec;
import dev.alloy.remap.testkit.KinFixture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class NotchMappingsTest {

    private static NotchMappings sample() throws IOException {
        KinFixture kin = new KinFixture()
                .add(KinClassSpec.type("ady", "net/minecraft/world/biome/BiomeGenBase")
                        .field("a", "I", "color")
                        .method("a", "()V", "decorate")
                        .method("a", "(I)V", "setColor")
                        .method("b", "()V", "tick")
                        .inner(KinClassSpec.type("a", "Height")));
        NotchMappingsBuilder builder = new NotchMappingsBuilder();
        new KinReader(builder).read(new ByteArrayInputStream(kin.toBytes()));
        return builder.build();
    }

    @Test
    void translatesKnownClassNames() throws IOException {
        NotchMappings mappings = NotchMappingsTest.sample();

        assertEquals("net/minecraft/world/biome/BiomeGenBase", mappings.className("ady"));
        assertEquals("net/minecraft/world/biome/BiomeGenBase$Height", mappings.className("ady$a"));
    }

    @Test
    void translatesUnknownInnerClassThroughItsOuterClass() throws IOException {
        NotchMappings mappings = NotchMappingsTest.sample();

        // Inner classes added by Forge are absent from Lunar's table.
        assertEquals("net/minecraft/world/biome/BiomeGenBase$FlowerEntry", mappings.className("ady$FlowerEntry"));
        assertEquals("net/minecraft/world/biome/BiomeGenBase$Height$1", mappings.className("ady$a$1"));
    }

    @Test
    void leavesForeignClassNamesAlone() throws IOException {
        NotchMappings mappings = NotchMappingsTest.sample();

        assertEquals("java/util/Map$Entry", mappings.className("java/util/Map$Entry"));
        assertEquals("net/minecraftforge/common/MinecraftForge", mappings.className("net/minecraftforge/common/MinecraftForge"));
    }

    @Test
    void knowsOnlyTheClassesOfTheTable() throws IOException {
        NotchMappings mappings = NotchMappingsTest.sample();

        assertTrue(mappings.knowsClass("ady"));
        assertFalse(mappings.knowsClass("ady$FlowerEntry"));
    }

    @Test
    void findsDeclaredMembersByNameAndDescriptor() throws IOException {
        NotchMappings mappings = NotchMappingsTest.sample();

        assertEquals(Optional.of("color"), mappings.fieldName("ady", new MemberKey("a", "I")));
        assertEquals(Optional.of("setColor"), mappings.methodName("ady", new MemberKey("a", "(I)V")));
        assertEquals(Optional.empty(), mappings.methodName("ady", new MemberKey("a", "(J)V")));
        assertEquals(Optional.empty(), mappings.fieldName("unknown", new MemberKey("a", "I")));
    }

    @Test
    void listsEveryTargetOfAnOverloadedName() throws IOException {
        NotchMappings mappings = NotchMappingsTest.sample();

        assertEquals(Set.of("decorate", "setColor"), mappings.methodNamesIgnoringDescriptor("ady", "a"));
        assertEquals(Set.of("tick"), mappings.methodNamesIgnoringDescriptor("ady", "b"));
    }

    @Test
    void rejectsTwoDifferentNamesForTheSameMember() {
        NotchMappingsBuilder builder = new NotchMappingsBuilder();

        assertThrows(MappingFormatException.class, () -> {
            builder.visitMethod("a", "a", "()V", "first");
            builder.visitMethod("a", "a", "()V", "second");
        });
    }
}
