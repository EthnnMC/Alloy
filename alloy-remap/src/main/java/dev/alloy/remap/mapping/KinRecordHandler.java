package dev.alloy.remap.mapping;

/**
 * Receives each record read by {@link KinReader}, in file order, so a large file can be reduced to
 * a small table without being kept in memory. Class names are full internal names (inner classes
 * already joined to their outer class); descriptors are in the <b>source</b> namespace.
 */
public interface KinRecordHandler {

    /**
     * Called for each class, before its inner classes and members.
     *
     * @throws MappingFormatException if this record contradicts previous ones
     */
    void visitClass(String sourceName, String targetName) throws MappingFormatException;

    /**
     * Called for each renamed field.
     *
     * @throws MappingFormatException if this record contradicts previous ones
     */
    void visitField(String sourceOwner, String sourceName, String sourceDescriptor, String targetName)
            throws MappingFormatException;

    /**
     * Called for each renamed method.
     *
     * @throws MappingFormatException if this record contradicts previous ones
     */
    void visitMethod(String sourceOwner, String sourceName, String sourceDescriptor, String targetName)
            throws MappingFormatException;
}
