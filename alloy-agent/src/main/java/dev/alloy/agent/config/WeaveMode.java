package dev.alloy.agent.config;

import java.util.Locale;

/** How Alloy treats Weave (setting {@code weave.enabled}). */
public enum WeaveMode {

    /** Chain Weave only if it is installed and at least one Weave mod exists for this version. */
    AUTO,

    /** Chain Weave whenever it is installed. */
    ALWAYS,

    /** Never chain Weave. */
    NEVER;

    /**
     * Parses the setting value. {@code true} and {@code false} are accepted besides
     * {@code auto}; any other value gives {@link #AUTO}.
     *
     * @param text the value written in {@code alloy.properties}
     */
    public static WeaveMode parse(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "always" -> WeaveMode.ALWAYS;
            case "false", "never" -> WeaveMode.NEVER;
            default -> WeaveMode.AUTO;
        };
    }
}
