package dev.alloy.remap.fetch;

import dev.alloy.remap.io.AtomicFiles;
import dev.alloy.remap.io.Sha256;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * A file to download whose SHA-256 is known in advance. Alloy cannot redistribute Forge, so it
 * fetches the official jar; a file without the expected digest (truncated, compromised server, wrong
 * version) is rejected and never reaches its destination. The digest, not the encrypted connection,
 * guarantees the content. The download goes to a temporary file renamed only after verification
 * ({@link AtomicFiles}). Immutable.
 */
public final class VerifiedDownload {

    private static final String HTTPS_SCHEME = "https";
    private static final int SHA256_HEX_LENGTH = 64;
    private static final String HEX_DIGITS = "[0-9a-f]+";
    private static final int HTTP_OK = 200;
    private static final String USER_AGENT = "Alloy (Java " + Runtime.version().feature() + ")";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    /** Time allowed to receive the whole file (a few MB). */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final URI source;
    private final String expectedSha256;

    /**
     * Describes an HTTPS download.
     *
     * @param source         {@code https://} URL of the file
     * @param expectedSha256 expected SHA-256, 64 hexadecimal digits (case-insensitive)
     * @throws IllegalArgumentException if the URL is not HTTPS or the digest is malformed
     */
    public VerifiedDownload(URI source, String expectedSha256) {
        this(source, expectedSha256, true);
    }

    /**
     * Full constructor. Tests use it to target a local HTTP server, which has no certificate.
     *
     * @param httpsOnly {@code true} to refuse any non-HTTPS URL
     */
    VerifiedDownload(URI source, String expectedSha256, boolean httpsOnly) {
        this.source = Objects.requireNonNull(source, "source");
        this.expectedSha256 = Objects.requireNonNull(expectedSha256, "expectedSha256").toLowerCase(Locale.ROOT);
        if (httpsOnly && !VerifiedDownload.HTTPS_SCHEME.equalsIgnoreCase(source.getScheme())) {
            throw new IllegalArgumentException("Only https downloads are allowed: " + source);
        }
        if (this.expectedSha256.length() != VerifiedDownload.SHA256_HEX_LENGTH
                || !this.expectedSha256.matches(VerifiedDownload.HEX_DIGITS)) {
            throw new IllegalArgumentException("Not a SHA-256 in hexadecimal: '" + expectedSha256 + "'");
        }
    }

    /**
     * Tells whether an existing file is the expected one.
     *
     * @return {@code true} if it exists and has the expected digest
     * @throws IOException if the file exists but cannot be read
     */
    public boolean matches(Path file) throws IOException {
        return Files.isRegularFile(file) && this.expectedSha256.equals(Sha256.ofFile(file));
    }

    /**
     * Returns the expected file, downloading it only if it is missing or altered.
     *
     * @return {@code target}
     * @throws DownloadException if the download fails or the received file is wrong
     * @throws IOException       if the disk refuses reading or writing
     */
    public Path ensure(Path target) throws IOException {
        return this.matches(target) ? target : this.fetchTo(target);
    }

    /**
     * Downloads the file, verifies its digest, then puts it in place.
     *
     * @param target final location; replaced if it exists, left intact on failure
     * @return {@code target}
     * @throws DownloadException if the download fails or the received file is wrong
     * @throws IOException       if the disk refuses writing
     */
    public Path fetchTo(Path target) throws IOException {
        AtomicFiles.replace(target, temporary -> {
            this.download(temporary);
            String actualSha256 = Sha256.ofFile(temporary);
            if (!this.expectedSha256.equals(actualSha256)) {
                throw new DownloadException("Checksum mismatch for " + this.source + ": expected SHA-256 "
                        + this.expectedSha256 + " but received " + actualSha256 + " (" + Files.size(temporary)
                        + " bytes); the file was discarded");
            }
        });
        return target;
    }

    private void download(Path destination) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(VerifiedDownload.CONNECT_TIMEOUT)
                .build();
        HttpRequest request = HttpRequest.newBuilder(this.source)
                .timeout(VerifiedDownload.REQUEST_TIMEOUT)
                .header("User-Agent", VerifiedDownload.USER_AGENT)
                .GET()
                .build();
        HttpResponse<Path> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        } catch (InterruptedException e) {
            // Restore the interrupt flag so the caller can react too.
            Thread.currentThread().interrupt();
            throw new DownloadException("Download of " + this.source + " was interrupted", e);
        } catch (IOException e) {
            throw new DownloadException("Cannot download " + this.source + ": " + e, e);
        }
        if (response.statusCode() != VerifiedDownload.HTTP_OK) {
            throw new DownloadException(
                    "Cannot download " + this.source + ": the server answered HTTP " + response.statusCode());
        }
    }
}
