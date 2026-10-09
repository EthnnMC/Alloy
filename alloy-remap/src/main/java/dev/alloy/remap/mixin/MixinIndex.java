package dev.alloy.remap.mixin;

import dev.alloy.remap.SrgNameTable;
import dev.alloy.remap.transform.Annotations;
import dev.alloy.remap.transform.ClassPass;
import dev.alloy.remap.transform.Verdict;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Prepares a mod's mixins for the Mixin library, which Alloy runs on game names. Mixin classes
 * are already renamed like any other class; what is left are the SRG names kept as text, in
 * annotations and in the reference map (the file telling Mixin which member each annotation string
 * stands for). This pass translates the annotations and records which game classes the mixins
 * target; {@link #translateNames} handles the reference map.
 */
public final class MixinIndex implements ClassPass {

    /** Jar entry written by {@link Contents#format()} in a prepared mod that has mixins. */
    public static final String JAR_ENTRY = "META-INF/alloy/mixins.tsv";

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String CONFIGS_ATTRIBUTE = "MixinConfigs";
    private static final String REFMAP_MARK = "refmap";
    private static final String JSON_SUFFIX = ".json";

    /** An SRG member name inside a longer text, e.g. in {@code Lnet/minecraft/client/Minecraft;func_147116_af()V}. */
    private static final Pattern SRG_NAME = Pattern.compile("\\b(?:func|field)_\\d+_[A-Za-z0-9_]+");

    private final SrgNameTable names;
    private final Set<String> targets = new LinkedHashSet<>();

    /**
     * @param names SRG names to game names
     */
    public MixinIndex(SrgNameTable names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    /** Returns the internal names of the classes targeted by the mixins seen so far. */
    public Set<String> targets() {
        return Set.copyOf(this.targets);
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        Optional<AnnotationNode> mixin = Annotations.find(classNode.invisibleAnnotations, MixinIndex.MIXIN)
                .or(() -> Annotations.find(classNode.visibleAnnotations, MixinIndex.MIXIN));
        if (mixin.isEmpty()) {
            return Verdict.KEEP;
        }
        MixinIndex.valuesOf(mixin.get(), "value").forEach(type -> this.targets.add(((Type) type).getInternalName()));
        MixinIndex.valuesOf(mixin.get(), "targets").forEach(name -> this.targets.add(((String) name).replace('.', '/')));

        this.translate(classNode.visibleAnnotations);
        this.translate(classNode.invisibleAnnotations);
        for (FieldNode field : classNode.fields) {
            this.translate(field.visibleAnnotations);
            this.translate(field.invisibleAnnotations);
        }
        for (MethodNode method : classNode.methods) {
            this.translate(method.visibleAnnotations);
            this.translate(method.invisibleAnnotations);
        }
        return Verdict.KEEP;
    }

    /**
     * Tells whether a jar entry is a mixin reference map.
     */
    public static boolean isReferenceMap(String entryName) {
        return entryName.endsWith(MixinIndex.JSON_SUFFIX) && entryName.contains(MixinIndex.REFMAP_MARK);
    }

    /**
     * Replaces every SRG member name found in a text file with its game name.
     *
     * @param content a UTF-8 text file, typically a reference map
     */
    public byte[] translateNames(byte[] content) {
        return this.translate(new String(content, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Reads the names of the mixin configuration files a mod jar declares in its manifest.
     *
     * @param resources the non-class entries of the jar
     * @throws IOException if the manifest is unreadable
     */
    public static List<String> configNames(Map<String, byte[]> resources) throws IOException {
        byte[] manifestBytes = resources.get(JarFile.MANIFEST_NAME);
        if (manifestBytes == null) {
            return List.of();
        }
        String names = new Manifest(new ByteArrayInputStream(manifestBytes))
                .getMainAttributes().getValue(MixinIndex.CONFIGS_ATTRIBUTE);
        return names == null || names.isBlank() ? List.of() : List.of(names.trim().split("\\s*,\\s*"));
    }

    private String translate(String text) {
        return MixinIndex.SRG_NAME.matcher(text).replaceAll(match -> this.names.runtimeName(match.group()));
    }

    private void translate(List<AnnotationNode> annotations) {
        if (annotations != null) {
            annotations.forEach(annotation -> this.translateValues(annotation.values));
        }
    }

    /** An annotation's values alternate names and values; a value may be a list or another annotation. */
    @SuppressWarnings("unchecked") // ASM stores annotation values as raw lists
    private void translateValues(List<Object> values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof String text) {
                values.set(i, this.translate(text));
            } else if (value instanceof List<?> list) {
                this.translateValues((List<Object>) list);
            } else if (value instanceof AnnotationNode nested) {
                this.translateValues(nested.values);
            }
        }
    }

    private static List<?> valuesOf(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key) && annotation.values.get(i + 1) instanceof List<?> list) {
                    return list;
                }
            }
        }
        return List.of();
    }

    /**
     * What the agent needs to know about the mixins of a prepared mod.
     *
     * @param configs names of its mixin configuration files
     * @param targets internal names of the game classes its mixins target
     */
    public record Contents(List<String> configs, Set<String> targets) {

        private static final String CONFIG = "config";
        private static final String TARGET = "target";
        private static final String SEPARATOR = "\t";

        public Contents {
            configs = List.copyOf(configs);
            targets = Set.copyOf(targets);
        }

        /** Tells whether the mod has no mixin at all. */
        public boolean isEmpty() {
            return this.configs.isEmpty() && this.targets.isEmpty();
        }

        /** Writes the text of {@link MixinIndex#JAR_ENTRY}: one {@code config} or {@code target} per line. */
        public String format() {
            StringBuilder text = new StringBuilder();
            this.configs.forEach(config -> text.append(Contents.CONFIG).append(Contents.SEPARATOR).append(config).append('\n'));
            this.targets.stream().sorted().forEach(
                    target -> text.append(Contents.TARGET).append(Contents.SEPARATOR).append(target).append('\n'));
            return text.toString();
        }

        /**
         * Reads back the text written by {@link #format()}.
         *
         * @throws IllegalArgumentException if a line is malformed
         */
        public static Contents parse(String text) {
            List<String> configs = new ArrayList<>();
            Set<String> targets = new LinkedHashSet<>();
            for (String line : text.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(Contents.SEPARATOR, -1);
                if (columns.length == 2 && columns[0].equals(Contents.CONFIG)) {
                    configs.add(columns[1]);
                } else if (columns.length == 2 && columns[0].equals(Contents.TARGET)) {
                    targets.add(columns[1]);
                } else {
                    throw new IllegalArgumentException("Malformed mixin index line: " + line);
                }
            }
            return new Contents(configs, targets);
        }
    }
}
