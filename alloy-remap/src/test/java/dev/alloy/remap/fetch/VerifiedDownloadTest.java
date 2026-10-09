package dev.alloy.remap.fetch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.io.Sha256;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Downloads target a small local HTTP server started for each test, so no Internet access is needed.
 */
class VerifiedDownloadTest {

    private static final byte[] CONTENT = "pretend this is the Forge universal jar".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_SHA256 = Sha256.ofText("pretend this is the Forge universal jar");
    private static final String OTHER_SHA256 = Sha256.ofText("something else");

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/forge.jar", exchange -> {
            this.requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, VerifiedDownloadTest.CONTENT.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(VerifiedDownloadTest.CONTENT);
            }
        });
        this.server.createContext("/moved.jar", exchange -> {
            exchange.getResponseHeaders().add("Location", "/forge.jar");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        this.server.createContext("/missing.jar", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        this.server.start();
    }

    @AfterEach
    void stopServer() {
        this.server.stop(0);
    }

    private VerifiedDownload download(String path, String sha256) {
        URI uri = URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + path);
        return new VerifiedDownload(uri, sha256, false);
    }

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void savesTheFileWhenItsChecksumMatches(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("libraries/forge.jar");

        Path result = this.download("/forge.jar", VerifiedDownloadTest.CONTENT_SHA256).fetchTo(target);

        assertEquals(target, result);
        assertArrayEquals(VerifiedDownloadTest.CONTENT, Files.readAllBytes(target));
        assertEquals(List.of("forge.jar"), VerifiedDownloadTest.namesIn(target.getParent()));
    }

    @Test
    void discardsAFileWithTheWrongChecksum(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("forge.jar");
        VerifiedDownload download = this.download("/forge.jar", VerifiedDownloadTest.OTHER_SHA256);

        DownloadException error = assertThrows(DownloadException.class, () -> download.fetchTo(target));

        assertTrue(error.getMessage().contains(VerifiedDownloadTest.OTHER_SHA256), error.getMessage());
        assertTrue(error.getMessage().contains(VerifiedDownloadTest.CONTENT_SHA256), error.getMessage());
        assertEquals(List.of(), VerifiedDownloadTest.namesIn(directory));
    }

    @Test
    void keepsAnExistingFileWhenTheDownloadIsRejected(@TempDir Path directory) throws IOException {
        Path target = Files.writeString(directory.resolve("forge.jar"), "previous version");
        VerifiedDownload download = this.download("/forge.jar", VerifiedDownloadTest.OTHER_SHA256);

        assertThrows(DownloadException.class, () -> download.fetchTo(target));

        assertEquals("previous version", Files.readString(target));
    }

    @Test
    void reportsTheHttpStatusOfAFailedRequest(@TempDir Path directory) {
        VerifiedDownload download = this.download("/missing.jar", VerifiedDownloadTest.CONTENT_SHA256);

        DownloadException error =
                assertThrows(DownloadException.class, () -> download.fetchTo(directory.resolve("forge.jar")));

        assertTrue(error.getMessage().contains("HTTP 404"), error.getMessage());
        assertFalse(Files.exists(directory.resolve("forge.jar")));
    }

    @Test
    void reportsAnUnreachableServer(@TempDir Path directory) {
        VerifiedDownload download = this.download("/forge.jar", VerifiedDownloadTest.CONTENT_SHA256);
        this.server.stop(0);

        DownloadException error =
                assertThrows(DownloadException.class, () -> download.fetchTo(directory.resolve("forge.jar")));

        assertTrue(error.getMessage().contains("/forge.jar"), error.getMessage());
    }

    @Test
    void followsRedirections(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("forge.jar");

        this.download("/moved.jar", VerifiedDownloadTest.CONTENT_SHA256).fetchTo(target);

        assertArrayEquals(VerifiedDownloadTest.CONTENT, Files.readAllBytes(target));
    }

    @Test
    void ensureDoesNotDownloadAFileThatIsAlreadyCorrect(@TempDir Path directory) throws IOException {
        Path target = Files.write(directory.resolve("forge.jar"), VerifiedDownloadTest.CONTENT);

        this.download("/forge.jar", VerifiedDownloadTest.CONTENT_SHA256).ensure(target);

        assertEquals(0, this.requestCount.get());
    }

    @Test
    void ensureReplacesAFileThatIsNotTheExpectedOne(@TempDir Path directory) throws IOException {
        Path target = Files.writeString(directory.resolve("forge.jar"), "corrupted");

        this.download("/forge.jar", VerifiedDownloadTest.CONTENT_SHA256).ensure(target);

        assertArrayEquals(VerifiedDownloadTest.CONTENT, Files.readAllBytes(target));
        assertEquals(1, this.requestCount.get());
    }

    @Test
    void checksumComparisonIgnoresCase(@TempDir Path directory) throws IOException {
        Path target = Files.write(directory.resolve("forge.jar"), VerifiedDownloadTest.CONTENT);
        String upperCase = VerifiedDownloadTest.CONTENT_SHA256.toUpperCase(Locale.ROOT);

        assertTrue(this.download("/forge.jar", upperCase).matches(target));
        assertFalse(this.download("/forge.jar", upperCase).matches(directory.resolve("absent.jar")));
    }

    @Test
    void publicConstructorRefusesPlainHttp() {
        URI plainHttp = URI.create("http://maven.minecraftforge.net/forge.jar");

        assertThrows(IllegalArgumentException.class,
                () -> new VerifiedDownload(plainHttp, VerifiedDownloadTest.CONTENT_SHA256));
    }

    @Test
    void refusesAMalformedChecksum() {
        URI https = URI.create("https://maven.minecraftforge.net/forge.jar");

        assertThrows(IllegalArgumentException.class, () -> new VerifiedDownload(https, "1234"));
        assertThrows(IllegalArgumentException.class,
                () -> new VerifiedDownload(https, "z".repeat(64)));
    }
}
