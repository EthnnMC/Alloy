package dev.alloy.remap.fetch;

import java.io.IOException;

/**
 * A download that failed: unreachable server, error response, or a received file different from
 * the expected one. The message names the URL and the reason; the destination file is never
 * created or modified in that case.
 */
public class DownloadException extends IOException {

    public DownloadException(String message) {
        super(message);
    }

    public DownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
