package dev.alloy.remap.jar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Removes from a jar what no longer makes sense once its classes are rewritten:
 * <ul>
 *   <li><b>The signature.</b> Modified classes no longer match their signed digests and the JVM
 *       would refuse to load them, so signature files and manifest digests are removed.</li>
 *   <li><b>The {@code Class-Path} attribute.</b> It points to sibling jars of the original; the
 *       rewritten copy lives elsewhere, so those paths lead nowhere and {@code javac} warns
 *       about each.</li>
 * </ul>
 */
public final class JarSanitizer {

    private static final String META_INF = "META-INF/";

    /** Signature file extensions (JAR format specification). */
    private static final String[] SIGNATURE_SUFFIXES = {".SF", ".DSA", ".RSA", ".EC"};

    /** Other signature file name form: {@code META-INF/SIG-xxx}. */
    private static final String SIGNATURE_PREFIX = "SIG-";

    /** Common part of the manifest digest attributes ({@code SHA-256-Digest}...). */
    private static final String DIGEST_MARKER = "-DIGEST";

    private JarSanitizer() {
        // Utility class: no instances.
    }

    /**
     * Tells whether a jar entry is a signature file.
     *
     * @return {@code true} for {@code META-INF/*.SF}, {@code *.DSA}, {@code *.RSA}, {@code *.EC}
     *         and {@code META-INF/SIG-*}, placed directly in {@code META-INF}
     */
    public static boolean isSignatureFile(String entryName) {
        String upperName = entryName.toUpperCase(Locale.ROOT);
        if (!upperName.startsWith(JarSanitizer.META_INF)) {
            return false;
        }
        String fileName = upperName.substring(JarSanitizer.META_INF.length());
        if (fileName.isEmpty() || fileName.indexOf('/') >= 0) {
            return false;
        }
        if (fileName.startsWith(JarSanitizer.SIGNATURE_PREFIX)) {
            return true;
        }
        for (String suffix : JarSanitizer.SIGNATURE_SUFFIXES) {
            if (fileName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes per-file digests and the {@code Class-Path} attribute from the manifest.
     *
     * @param manifestBytes content of {@code META-INF/MANIFEST.MF}
     * @return the cleaned manifest, or {@code manifestBytes} itself if there was nothing to remove
     * @throws IOException if the manifest is malformed
     */
    public static byte[] cleanManifest(byte[] manifestBytes) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
        boolean changed = manifest.getMainAttributes().remove(Attributes.Name.CLASS_PATH) != null;

        Iterator<Attributes> sections = manifest.getEntries().values().iterator();
        while (sections.hasNext()) {
            Attributes section = sections.next();
            if (section.keySet().removeIf(JarSanitizer::isDigestAttribute)) {
                changed = true;
                if (section.isEmpty()) {
                    // A section that only held digests is now pointless.
                    sections.remove();
                }
            }
        }
        if (!changed) {
            return manifestBytes;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        manifest.write(output);
        return output.toByteArray();
    }

    private static boolean isDigestAttribute(Object attributeName) {
        return attributeName.toString().toUpperCase(Locale.ROOT).contains(JarSanitizer.DIGEST_MARKER);
    }
}
