package dev.alloy.remap.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 digests of files, text and bytes, as lowercase hexadecimal. Used to verify downloads and
 * to build cache keys.
 */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    private static final int BUFFER_SIZE = 64 * 1024;

    private Sha256() {
        // Utility class: no instances.
    }

    /**
     * Computes the digest of a file's content.
     *
     * @param file the file to read
     * @return 64 lowercase hexadecimal digits
     * @throws IOException if the file cannot be read
     */
    public static String ofFile(Path file) throws IOException {
        MessageDigest digest = Sha256.newDigest();
        byte[] buffer = new byte[Sha256.BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            int count = input.read(buffer);
            while (count >= 0) {
                digest.update(buffer, 0, count);
                count = input.read(buffer);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Computes the digest of a text, encoded as UTF-8.
     *
     * @return 64 lowercase hexadecimal digits
     */
    public static String ofText(String text) {
        return Sha256.ofBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the digest of a byte array.
     *
     * @return 64 lowercase hexadecimal digits
     */
    public static String ofBytes(byte[] content) {
        return HexFormat.of().formatHex(Sha256.newDigest().digest(content));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(Sha256.ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // Every JVM must provide SHA-256: this means the JVM is broken.
            throw new IllegalStateException("This JVM does not provide SHA-256", e);
        }
    }
}
