package dev.alloy.remap.mapping;

import dev.alloy.remap.hierarchy.MemberKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a {@link NotchMappings} from the records of a {@code .kin} file.
 * Usage: {@code new KinReader(builder).read(stream)} then {@link #build()}.
 */
public final class NotchMappingsBuilder implements KinRecordHandler {

    private final Map<String, String> classNames = new HashMap<>();
    private final Map<String, Map<MemberKey, String>> fieldNames = new HashMap<>();
    private final Map<String, Map<MemberKey, String>> methodNames = new HashMap<>();

    public NotchMappingsBuilder() {
        // Nothing to prepare: tables fill up as records arrive.
    }

    @Override
    public void visitClass(String sourceName, String targetName) throws MappingFormatException {
        NotchMappingsBuilder.putOnce(this.classNames, sourceName, targetName, "class " + sourceName);
    }

    @Override
    public void visitField(String sourceOwner, String sourceName, String sourceDescriptor, String targetName)
            throws MappingFormatException {
        Map<MemberKey, String> fields = this.fieldNames.computeIfAbsent(sourceOwner, owner -> new HashMap<>());
        NotchMappingsBuilder.putOnce(
                fields, new MemberKey(sourceName, sourceDescriptor), targetName, "field " + sourceOwner + "." + sourceName);
    }

    @Override
    public void visitMethod(String sourceOwner, String sourceName, String sourceDescriptor, String targetName)
            throws MappingFormatException {
        Map<MemberKey, String> methods = this.methodNames.computeIfAbsent(sourceOwner, owner -> new HashMap<>());
        NotchMappingsBuilder.putOnce(
                methods,
                new MemberKey(sourceName, sourceDescriptor),
                targetName,
                "method " + sourceOwner + "." + sourceName + sourceDescriptor);
    }

    /**
     * Returns the built table: immutable and independent of this builder.
     */
    public NotchMappings build() {
        return new NotchMappings(this.classNames, this.fieldNames, this.methodNames);
    }

    /**
     * Stores a mapping, refusing two different names for one key: such a file contradicts itself.
     */
    private static <K> void putOnce(Map<K, String> table, K key, String targetName, String what)
            throws MappingFormatException {
        String previous = table.putIfAbsent(key, targetName);
        if (previous != null && !previous.equals(targetName)) {
            throw new MappingFormatException(
                    "Conflicting names for " + what + ": '" + previous + "' and '" + targetName + "'");
        }
    }
}
