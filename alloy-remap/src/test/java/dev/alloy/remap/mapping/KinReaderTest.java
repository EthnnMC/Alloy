package dev.alloy.remap.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.KinClassSpec;
import dev.alloy.remap.testkit.KinFixture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class KinReaderTest {

    /** Records each received record as a line of text. */
    private static final class RecordingHandler implements KinRecordHandler {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void visitClass(String sourceName, String targetName) {
            this.lines.add("C " + sourceName + " " + targetName);
        }

        @Override
        public void visitField(String sourceOwner, String sourceName, String sourceDescriptor, String targetName) {
            this.lines.add("F " + sourceOwner + " " + sourceName + " " + sourceDescriptor + " " + targetName);
        }

        @Override
        public void visitMethod(String sourceOwner, String sourceName, String sourceDescriptor, String targetName) {
            this.lines.add("M " + sourceOwner + " " + sourceName + " " + sourceDescriptor + " " + targetName);
        }
    }

    private static KinFixture sample() {
        return new KinFixture()
                .add(KinClassSpec.type("ave", "net/minecraft/client/Minecraft")
                        .field("h", "Lbew;", "thePlayer")
                        .method("A", "()Lave;", "getMinecraft")
                        .inner(KinClassSpec.type("a", "Timer")
                                .field("a", "I", "ticks")
                                .inner(KinClassSpec.type("1", "1").method("run", "()V", "runTick"))))
                .add(KinClassSpec.type("bew", "net/minecraft/client/entity/EntityPlayerSP"));
    }

    private static KinSummary read(byte[] kin, KinRecordHandler handler) throws IOException {
        return new KinReader(handler).read(new ByteArrayInputStream(kin));
    }

    @Test
    void reportsRecordsInFileOrderWithFullInnerClassNames() throws IOException {
        RecordingHandler handler = new RecordingHandler();

        KinReaderTest.read(KinReaderTest.sample().toBytes(), handler);

        assertEquals(
                List.of(
                        "C ave net/minecraft/client/Minecraft",
                        "C ave$a net/minecraft/client/Minecraft$Timer",
                        "C ave$a$1 net/minecraft/client/Minecraft$Timer$1",
                        "M ave$a$1 run ()V runTick",
                        "F ave$a a I ticks",
                        "F ave h Lbew; thePlayer",
                        "M ave A ()Lave; getMinecraft",
                        "C bew net/minecraft/client/entity/EntityPlayerSP"),
                handler.lines);
    }

    @Test
    void countsEachKindOfRecord() throws IOException {
        KinSummary summary = KinReaderTest.read(KinReaderTest.sample().toBytes(), new RecordingHandler());

        assertEquals(new KinSummary(2, 2, 2, 2), summary);
    }

    @Test
    void keepsOnlyTheSimpleNameOfAnInnerTargetWrittenInFull() throws IOException {
        RecordingHandler handler = new RecordingHandler();
        KinFixture kin = new KinFixture()
                .add(KinClassSpec.type("a", "net/minecraft/Outer").inner(KinClassSpec.type("b", "Outer$Inner")));

        KinReaderTest.read(kin.toBytes(), handler);

        assertEquals("C a$b net/minecraft/Outer$Inner", handler.lines.get(1));
    }

    @Test
    void rejectsWrongMagicNumber() {
        byte[] kin = KinReaderTest.sample().toBytes(0xCAFEBABE, 1);

        MappingFormatException error =
                assertThrows(MappingFormatException.class, () -> KinReaderTest.read(kin, new RecordingHandler()));

        assertTrue(error.getMessage().contains("0xCAFEBABE"), error.getMessage());
    }

    @Test
    void rejectsUnknownVersion() {
        byte[] kin = KinReaderTest.sample().toBytes(KinReader.MAGIC, 2);

        MappingFormatException error =
                assertThrows(MappingFormatException.class, () -> KinReaderTest.read(kin, new RecordingHandler()));

        assertTrue(error.getMessage().contains("version 2"), error.getMessage());
    }

    @Test
    void rejectsTruncatedFile() {
        byte[] complete = KinReaderTest.sample().toBytes();
        byte[] truncated = Arrays.copyOf(complete, complete.length - 5);

        assertThrows(MappingFormatException.class, () -> KinReaderTest.read(truncated, new RecordingHandler()));
    }

    @Test
    void rejectsDataAfterTheLastRecord() {
        byte[] complete = KinReaderTest.sample().toBytes();
        byte[] padded = Arrays.copyOf(complete, complete.length + 1);

        assertThrows(MappingFormatException.class, () -> KinReaderTest.read(padded, new RecordingHandler()));
    }

    @Test
    void rejectsNegativeCount() {
        // Valid header, zero metadata, then a negative class count.
        byte[] kin = {0x05, (byte) 0xE8, (byte) 0xF0, 0x46, 1, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        assertThrows(MappingFormatException.class, () -> KinReaderTest.read(kin, new RecordingHandler()));
    }
}
