package dev.alloy.remap.mapping;

import dev.alloy.remap.SrgNameTable;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the {@link SrgNameTable} from Lunar's {@code searge2lunar} file. The file repeats each
 * member over every Minecraft subclass; only the "SRG name to game name" pair is kept, ignoring
 * class and descriptor. This relies on an SRG name denoting the same member everywhere, which is
 * checked here: a file that breaks it is refused.
 */
public final class SrgTableBuilder implements KinRecordHandler {

    private final Map<String, String> runtimeNames = new HashMap<>();

    public SrgTableBuilder() {
        // Nothing to prepare: the table fills up as records arrive.
    }

    @Override
    public void visitClass(String sourceName, String targetName) {
        // Classes keep the same name between SRG and the game: nothing to keep.
    }

    @Override
    public void visitField(String sourceOwner, String sourceName, String sourceDescriptor, String targetName)
            throws MappingFormatException {
        this.put(sourceName, targetName);
    }

    @Override
    public void visitMethod(String sourceOwner, String sourceName, String sourceDescriptor, String targetName)
            throws MappingFormatException {
        this.put(sourceName, targetName);
    }

    /**
     * Returns the built table: immutable and independent of this builder.
     */
    public SrgNameTable build() {
        return SrgNameTable.of(this.runtimeNames);
    }

    private void put(String srgName, String runtimeName) throws MappingFormatException {
        String previous = this.runtimeNames.putIfAbsent(srgName, runtimeName);
        if (previous != null && !previous.equals(runtimeName)) {
            throw new MappingFormatException("SRG name '" + srgName + "' maps to two different names: '" + previous
                    + "' and '" + runtimeName + "'; a name-only table cannot represent that");
        }
    }
}
