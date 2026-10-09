package dev.alloy.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class MemberShimTableTest {

    /** The two example lines from the specification, preceded by their comment line. */
    private static final String SAMPLE = String.join("\n",
            "# kind\towner\tname\tdescriptor\taction\ttarget...",
            "",
            "method\tnet/minecraft/network/NetworkManager\tchannel\t()Lio/netty/channel/Channel;"
                    + "\tgetfield\tchannel\tLio/netty/channel/Channel;",
            "method\tnet/minecraft/client/gui/GuiScreen\tdrawHoveringText"
                    + "\t(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V"
                    + "\tinvokestatic\tdev/alloy/forge/shim/ForgeMemberShims\tdrawHoveringText",
            "");

    private static final String CHANNEL_TYPE = "Lio/netty/channel/Channel;";
    private static final String NETWORK_MANAGER = "net/minecraft/network/NetworkManager";
    private static final String GUI_SCREEN = "net/minecraft/client/gui/GuiScreen";

    private static IllegalArgumentException parseFailure(String text) {
        return assertThrows(IllegalArgumentException.class, () -> MemberShimTable.parse(text));
    }

    @Test
    void parsesTheDocumentedExample() {
        MemberShimTable table = MemberShimTable.parse(MemberShimTableTest.SAMPLE);

        assertEquals(
                List.of(
                        new MemberShim(MemberKind.METHOD, MemberShimTableTest.NETWORK_MANAGER, "channel",
                                "()" + MemberShimTableTest.CHANNEL_TYPE,
                                new ReadFieldAction("channel", MemberShimTableTest.CHANNEL_TYPE)),
                        new MemberShim(MemberKind.METHOD, MemberShimTableTest.GUI_SCREEN, "drawHoveringText",
                                "(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V",
                                new CallStaticAction("dev/alloy/forge/shim/ForgeMemberShims", "drawHoveringText"))),
                table.shims());
    }

    @Test
    void acceptsWindowsLineEndings() {
        MemberShimTable table = MemberShimTable.parse(MemberShimTableTest.SAMPLE.replace("\n", "\r\n"));

        assertEquals(2, table.shims().size());
    }

    @Test
    void findsCandidatesByKindNameAndDescriptor() {
        MemberShimTable table = MemberShimTable.parse(MemberShimTableTest.SAMPLE);

        assertEquals(1,
                table.candidates(MemberKind.METHOD, "channel", "()" + MemberShimTableTest.CHANNEL_TYPE).size());
        assertEquals(List.of(), table.candidates(MemberKind.FIELD, "channel", "()" + MemberShimTableTest.CHANNEL_TYPE));
        assertEquals(List.of(), table.candidates(MemberKind.METHOD, "channel", "()Ljava/lang/Object;"));
    }

    @Test
    void readsFromStreamAndFromFile(@TempDir Path directory) throws IOException {
        byte[] bytes = MemberShimTableTest.SAMPLE.getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(directory.resolve("member-shims.tsv"), bytes);

        assertEquals(2, MemberShimTable.read(new ByteArrayInputStream(bytes)).shims().size());
        assertEquals(2, MemberShimTable.read(file).shims().size());
    }

    @Test
    void emptyTableHasNoShim() {
        assertTrue(MemberShimTable.empty().isEmpty());
        assertTrue(MemberShimTable.parse("# only a comment\n\n").isEmpty());
    }

    @Test
    void reportsTheLineNumberOfAMalformedLine() {
        IllegalArgumentException error = MemberShimTableTest.parseFailure("# header\nmethod\ta/B\tc\t()V\tgetfield\tonlyOneTarget");

        assertTrue(error.getMessage().contains("line 2"), error.getMessage());
    }

    @Test
    void rejectsUnknownKindAndUnknownAction() {
        assertTrue(MemberShimTableTest.parseFailure("class\ta/B\tc\t()V\tinvokestatic\tx/Y\tz").getMessage()
                .contains("unknown kind"));
        assertTrue(MemberShimTableTest.parseFailure("method\ta/B\tc\t()V\tinvokevirtual\tx/Y\tz").getMessage()
                .contains("unknown action"));
    }

    @Test
    void rejectsDescriptorThatDoesNotFitTheKind() {
        MemberShimTableTest.parseFailure("field\ta/B\tc\t()V\tinvokestatic\tx/Y\tz");
        MemberShimTableTest.parseFailure("method\ta/B\tc\tI\tinvokestatic\tx/Y\tz");
    }

    @Test
    void rejectsGetfieldForAMethodWithArguments() {
        MemberShimTableTest.parseFailure("method\ta/B\tc\t(I)I\tgetfield\tvalue\tI");
        MemberShimTableTest.parseFailure("field\ta/B\tc\tI\tgetfield\tvalue\tI");
    }

    @Test
    void rejectsConstructorsAndDuplicates() {
        MemberShimTableTest.parseFailure("method\ta/B\t<init>\t()V\tinvokestatic\tx/Y\tz");
        MemberShimTableTest.parseFailure(
                "method\ta/B\tc\t()V\tinvokestatic\tx/Y\tz\nmethod\ta/B\tc\t()V\tinvokestatic\tx/Y\tother");
    }

    @Test
    void readFieldActionProducesGetfieldOnTheListedOwner() {
        MemberShim shim = MemberShimTable.parse(MemberShimTableTest.SAMPLE).shims().get(0);

        Optional<AbstractInsnNode> replacement = shim.action().replacement(shim, Opcodes.INVOKEVIRTUAL);

        FieldInsnNode read = (FieldInsnNode) replacement.orElseThrow();
        assertEquals(Opcodes.GETFIELD, read.getOpcode());
        assertEquals(MemberShimTableTest.NETWORK_MANAGER, read.owner);
        assertEquals("channel", read.name);
        assertEquals(MemberShimTableTest.CHANNEL_TYPE, read.desc);
        assertEquals(Optional.empty(), shim.action().replacement(shim, Opcodes.INVOKESTATIC));
    }

    @Test
    void callStaticActionPrependsTheReceiverForInstanceCalls() {
        MemberShim shim = MemberShimTable.parse(MemberShimTableTest.SAMPLE).shims().get(1);

        MethodInsnNode call = (MethodInsnNode) shim.action().replacement(shim, Opcodes.INVOKEVIRTUAL).orElseThrow();

        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("dev/alloy/forge/shim/ForgeMemberShims", call.owner);
        assertEquals("drawHoveringText", call.name);
        assertEquals("(Lnet/minecraft/client/gui/GuiScreen;Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V",
                call.desc);
        assertEquals(Optional.empty(), shim.action().replacement(shim, Opcodes.GETFIELD));
    }

    @Test
    void callStaticActionDerivesFieldAccessorDescriptors() {
        MemberShim shim = new MemberShim(MemberKind.FIELD, "a/B", "count", "J", new CallStaticAction("x/Shims", "count"));

        assertEquals("(La/B;)J", MemberShimTableTest.descriptorFor(shim, Opcodes.GETFIELD));
        assertEquals("(La/B;J)V", MemberShimTableTest.descriptorFor(shim, Opcodes.PUTFIELD));
        assertEquals("()J", MemberShimTableTest.descriptorFor(shim, Opcodes.GETSTATIC));
        assertEquals("(J)V", MemberShimTableTest.descriptorFor(shim, Opcodes.PUTSTATIC));
    }

    private static String descriptorFor(MemberShim shim, int opcode) {
        return ((MethodInsnNode) shim.action().replacement(shim, opcode).orElseThrow()).desc;
    }
}
