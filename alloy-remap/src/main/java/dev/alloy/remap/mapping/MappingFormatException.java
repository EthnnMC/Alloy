package dev.alloy.remap.mapping;

import java.io.IOException;

/**
 * A mappings file that cannot be read or contradicts itself. Failing loudly beats renaming
 * halfway: a badly translated name would only surface in-game as a {@code NoSuchMethodError}.
 */
public class MappingFormatException extends IOException {

    public MappingFormatException(String message) {
        super(message);
    }

    public MappingFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
