package dev.alloy.bridge;

/**
 * Logger shared by the agent and the runtime loaded into the game.
 *
 * <p>The agent provides the implementation ({@code ~/.alloy/logs/latest.log}); the Forge runtime
 * receives it through {@link RuntimeContext}.</p>
 */
public interface BridgeLogger {

    /**
     * Writes a message.
     *
     * @param error the associated exception, or {@code null}
     */
    void log(LogLevel level, String message, Throwable error);

    /** Shortcut for a {@link LogLevel#DEBUG} message. */
    default void debug(String message) {
        this.log(LogLevel.DEBUG, message, null);
    }

    /** Shortcut for an {@link LogLevel#INFO} message. */
    default void info(String message) {
        this.log(LogLevel.INFO, message, null);
    }

    /** Shortcut for a {@link LogLevel#WARN} message. */
    default void warn(String message) {
        this.log(LogLevel.WARN, message, null);
    }

    /** Shortcut for an {@link LogLevel#ERROR} message. */
    default void error(String message, Throwable error) {
        this.log(LogLevel.ERROR, message, error);
    }
}
