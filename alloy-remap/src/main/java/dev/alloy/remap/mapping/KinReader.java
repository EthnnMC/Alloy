package dev.alloy.remap.mapping;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.util.Objects;

/**
 * Reads a {@code .kin} mappings file (Lunar Client's binary format, {@link java.io.DataOutput}
 * encoding) and passes each record to a {@link KinRecordHandler}.
 * <pre>
 *   file  := int magic (0x05E8F046) ; byte version (1)
 *            int n ; n x { UTF key ; UTF value }        metadata, always empty in Lunar files
 *            int n ; n x class
 *   class := UTF sourceName ; UTF targetName            full name at top level, simple name if inner
 *            int n ; n x class                          inner classes (recursive)
 *            int n ; n x { UTF name ; UTF type ; UTF targetName }         fields
 *            int n ; n x { UTF name ; UTF descriptor ; UTF targetName }   methods
 * </pre>
 */
public final class KinReader {

    /** The first four bytes of every {@code .kin} file. */
    public static final int MAGIC = 0x05E8F046;

    /** The only format version Lunar's own reader accepts. */
    public static final int SUPPORTED_VERSION = 1;

    private static final char INNER_SEPARATOR = '$';

    /** Safety limit: Lunar nests at most three classes, so anything deeper is a bad file. */
    private static final int MAX_NESTING = 32;

    private static final KinSummary NOTHING = new KinSummary(0, 0, 0, 0);
    private static final KinSummary ONE_TOP_LEVEL_CLASS = new KinSummary(1, 0, 0, 0);
    private static final KinSummary ONE_INNER_CLASS = new KinSummary(0, 1, 0, 0);

    private final KinRecordHandler handler;

    public KinReader(KinRecordHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Reads a whole {@code .kin} file.
     *
     * @param stream the file content; not closed by this method
     * @return the number of records read, by kind
     * @throws MappingFormatException if the content is not a complete version 1 {@code .kin}
     * @throws IOException            if reading itself fails
     */
    public KinSummary read(InputStream stream) throws IOException {
        DataInputStream input = new DataInputStream(new BufferedInputStream(stream));
        try {
            KinReader.checkHeader(input);
            KinReader.skipMetadata(input);
            KinSummary summary = KinReader.NOTHING;
            int classCount = KinReader.readCount(input, "class");
            for (int i = 0; i < classCount; i++) {
                summary = summary.plus(this.readClass(input, null, null, 0));
            }
            if (input.read() >= 0) {
                throw new MappingFormatException("Unexpected data after the last record of the .kin file");
            }
            return summary;
        } catch (EOFException e) {
            throw new MappingFormatException("The .kin file ends in the middle of a record", e);
        } catch (UTFDataFormatException e) {
            throw new MappingFormatException("The .kin file contains a malformed string", e);
        }
    }

    private static void checkHeader(DataInputStream input) throws IOException {
        int magic = input.readInt();
        if (magic != KinReader.MAGIC) {
            throw new MappingFormatException(String.format(
                    "Not a .kin file: magic number is 0x%08X, expected 0x%08X", magic, KinReader.MAGIC));
        }
        int version = input.readByte();
        if (version != KinReader.SUPPORTED_VERSION) {
            throw new MappingFormatException(
                    "Unsupported .kin version " + version + " (only version " + KinReader.SUPPORTED_VERSION
                            + " is known)");
        }
    }

    /** Metadata (key/value pairs) is read then ignored, like Lunar's own reader does. */
    private static void skipMetadata(DataInputStream input) throws IOException {
        int pairCount = KinReader.readCount(input, "metadata");
        for (int i = 0; i < pairCount; i++) {
            input.readUTF();
            input.readUTF();
        }
    }

    /**
     * Reads a class, then its inner classes and members.
     *
     * @param sourceOuter source name of the enclosing class, or {@code null} at top level
     * @param targetOuter target name of the enclosing class, or {@code null} at top level
     * @param depth       nesting level, 0 at top level
     */
    private KinSummary readClass(DataInputStream input, String sourceOuter, String targetOuter, int depth)
            throws IOException {
        if (depth > KinReader.MAX_NESTING) {
            throw new MappingFormatException("Classes are nested more than " + KinReader.MAX_NESTING + " levels deep");
        }
        boolean inner = sourceOuter != null;
        String sourceName = input.readUTF();
        String targetName = input.readUTF();
        if (inner) {
            sourceName = sourceOuter + KinReader.INNER_SEPARATOR + sourceName;
            targetName = targetOuter + KinReader.INNER_SEPARATOR + KinReader.simpleName(targetName);
        }
        this.handler.visitClass(sourceName, targetName);
        KinSummary summary = inner ? KinReader.ONE_INNER_CLASS : KinReader.ONE_TOP_LEVEL_CLASS;

        int innerCount = KinReader.readCount(input, "inner class");
        for (int i = 0; i < innerCount; i++) {
            summary = summary.plus(this.readClass(input, sourceName, targetName, depth + 1));
        }

        int fieldCount = KinReader.readCount(input, "field");
        for (int i = 0; i < fieldCount; i++) {
            String name = input.readUTF();
            String descriptor = input.readUTF();
            String target = input.readUTF();
            this.handler.visitField(sourceName, name, descriptor, target);
        }

        int methodCount = KinReader.readCount(input, "method");
        for (int i = 0; i < methodCount; i++) {
            String name = input.readUTF();
            String descriptor = input.readUTF();
            String target = input.readUTF();
            this.handler.visitMethod(sourceName, name, descriptor, target);
        }
        return summary.plus(new KinSummary(0, 0, fieldCount, methodCount));
    }

    /**
     * An inner class's target name is normally simple; if written in full ({@code Outer$Inner}),
     * Lunar's library keeps only the last part, so do we.
     */
    private static String simpleName(String innerName) {
        return innerName.substring(innerName.lastIndexOf(KinReader.INNER_SEPARATOR) + 1);
    }

    private static int readCount(DataInputStream input, String what) throws IOException {
        int count = input.readInt();
        if (count < 0) {
            throw new MappingFormatException("Negative " + what + " count in the .kin file: " + count);
        }
        return count;
    }
}
