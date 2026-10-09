package dev.alloy.remap.testkit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: builds a synthetic {@code .kin} file the way Lunar writes it, so tests need no Lunar file.
 */
public final class KinFixture {

    /** Same constants as the reader, copied on purpose so the test does not reuse the code under test. */
    private static final int MAGIC = 0x05E8F046;
    private static final int VERSION = 1;

    private final List<KinClassSpec> classes = new ArrayList<>();

    public KinFixture add(KinClassSpec topLevelClass) {
        this.classes.add(topLevelClass);
        return this;
    }

    /** Returns the bytes of a version 1 {@code .kin} file. */
    public byte[] toBytes() {
        return this.toBytes(KinFixture.MAGIC, KinFixture.VERSION);
    }

    /** Returns the file bytes with a chosen header, to test rejection of invalid files. */
    public byte[] toBytes(int magic, int version) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(magic);
            output.writeByte(version);
            output.writeInt(0);
            output.writeInt(this.classes.size());
            for (KinClassSpec topLevelClass : this.classes) {
                topLevelClass.writeTo(output);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
