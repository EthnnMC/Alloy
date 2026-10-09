package dev.alloy.remap.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sha256Test {

    /** Official SHA-256 test vector for the text "abc" (FIPS 180-2). */
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** Hash of empty input. */
    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void hashesTextAsLowerCaseHexadecimal() {
        assertEquals(Sha256Test.ABC, Sha256.ofText("abc"));
    }

    @Test
    void hashesFileContent(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("abc.txt"), "abc");

        assertEquals(Sha256Test.ABC, Sha256.ofFile(file));
    }

    @Test
    void hashesEmptyFile(@TempDir Path directory) throws IOException {
        Path file = Files.createFile(directory.resolve("empty.bin"));

        assertEquals(Sha256Test.EMPTY, Sha256.ofFile(file));
    }

    @Test
    void hashesFileLargerThanTheReadBuffer(@TempDir Path directory) throws IOException, NoSuchAlgorithmException {
        byte[] content = new byte[200_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31);
        }
        Path file = Files.write(directory.resolve("large.bin"), content);
        // Reference: the same digest computed in one block by the JDK.
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        assertEquals(expected, Sha256.ofFile(file));
    }
}
