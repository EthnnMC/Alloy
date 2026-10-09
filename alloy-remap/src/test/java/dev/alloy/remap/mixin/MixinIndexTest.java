package dev.alloy.remap.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.SrgNameTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.SourceCompiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/** Uses miniature mixins compiled against stand-ins for the Mixin annotations. */
class MixinIndexTest {

    private static final SrgNameTable NAMES = SrgNameTable.of(Map.of("func_1_a", "tick", "field_2_b", "counter"));

    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        MixinIndexTest.compiled = SourceCompiler.compile(8,
                """
                package org.spongepowered.asm.mixin;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.CLASS)
                public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; }
                """,
                """
                package org.spongepowered.asm.mixin.injection;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface At { String value(); String target() default ""; }
                """,
                """
                package org.spongepowered.asm.mixin.injection;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Inject { String[] method(); At[] at(); }
                """,
                """
                package example.game;
                public class Game { }
                """,
                """
                package example.mod;
                import example.game.Game;
                import org.spongepowered.asm.mixin.Mixin;
                import org.spongepowered.asm.mixin.injection.At;
                import org.spongepowered.asm.mixin.injection.Inject;
                @Mixin(value = Game.class, targets = "example.game.Game$Inner")
                public class GameMixin {
                    @Inject(method = {"func_1_a", "untouched"},
                            at = @At(value = "INVOKE", target = "Lexample/game/Game;func_1_a()V"))
                    private void onTick() { }
                }
                """,
                """
                package example.mod;
                public class Plain {
                    @Deprecated void func_1_a() { }
                }
                """);
    }

    @Test
    void targetsOfEveryMixinAreCollected() {
        MixinIndex index = new MixinIndex(MixinIndexTest.NAMES);

        ClassBytes.applyToAll(MixinIndexTest.compiled, index);

        assertEquals(Set.of("example/game/Game", "example/game/Game$Inner"), index.targets());
    }

    @Test
    void srgNamesInMixinAnnotationsAreTranslated() {
        Map<String, byte[]> prepared = ClassBytes.applyToAll(MixinIndexTest.compiled, new MixinIndex(MixinIndexTest.NAMES));

        ClassNode mixin = ClassBytes.read(prepared.get("example/mod/GameMixin"));
        AnnotationNode inject = mixin.methods.stream().filter(method -> method.name.equals("onTick"))
                .findFirst().orElseThrow().visibleAnnotations.get(0);
        assertEquals(List.of("tick", "untouched"), inject.values.get(1));
        AnnotationNode at = (AnnotationNode) ((List<?>) inject.values.get(3)).get(0);
        assertEquals(List.of("value", "INVOKE", "target", "Lexample/game/Game;tick()V"), at.values);
    }

    @Test
    void referenceMapNamesAreTranslated() {
        String refmap = "{\"mappings\":{\"example/mod/GameMixin\":{\"tick\":\"Lexample/game/Game;func_1_a()V\","
                + "\"counter\":\"field_2_b:I\",\"other\":\"func_9_z()V\"}}}";

        String translated = new String(
                new MixinIndex(MixinIndexTest.NAMES).translateNames(refmap.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);

        assertEquals("{\"mappings\":{\"example/mod/GameMixin\":{\"tick\":\"Lexample/game/Game;tick()V\","
                + "\"counter\":\"counter:I\",\"other\":\"func_9_z()V\"}}}", translated);
        assertTrue(MixinIndex.isReferenceMap("mixins.example.refmap.json"));
        assertFalse(MixinIndex.isReferenceMap("mixins.example.json"));
    }

    @Test
    void configurationNamesComeFromTheManifest() throws IOException {
        String manifest = "Manifest-Version: 1.0\nMixinConfigs: mixins.a.json, mixins.b.json\n\n";

        assertEquals(List.of("mixins.a.json", "mixins.b.json"),
                MixinIndex.configNames(Map.of(JarFile.MANIFEST_NAME, manifest.getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of(), MixinIndex.configNames(Map.of()));
    }

    @Test
    void contentsSurviveTheirTextForm() {
        MixinIndex.Contents contents = new MixinIndex.Contents(
                List.of("mixins.a.json"), Set.of("example/game/Game", "example/game/Game$Inner"));

        assertEquals(contents, MixinIndex.Contents.parse(contents.format()));
        assertTrue(new MixinIndex.Contents(List.of(), Set.of()).isEmpty());
    }
}
