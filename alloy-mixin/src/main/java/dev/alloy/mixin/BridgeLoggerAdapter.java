package dev.alloy.mixin;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.LogLevel;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;

/** Sends Mixin's log to Alloy's; Mixin's {@code {}} placeholders are filled in here. */
final class BridgeLoggerAdapter extends LoggerAdapterAbstract {

    private static final String PLACEHOLDER = "{}";

    private final BridgeLogger logger;

    BridgeLoggerAdapter(String id, BridgeLogger logger) {
        super(id);
        this.logger = logger;
    }

    @Override
    public String getType() {
        return "Alloy";
    }

    @Override
    public void catching(Level level, Throwable error) {
        this.logger.log(BridgeLoggerAdapter.levelOf(level), "Mixin: caught " + error, error);
    }

    @Override
    public void log(Level level, String message, Object... arguments) {
        Throwable error = arguments.length > 0 && arguments[arguments.length - 1] instanceof Throwable last ? last : null;
        this.logger.log(BridgeLoggerAdapter.levelOf(level), "Mixin: " + BridgeLoggerAdapter.format(message, arguments), error);
    }

    @Override
    public void log(Level level, String message, Throwable error) {
        this.logger.log(BridgeLoggerAdapter.levelOf(level), "Mixin: " + message, error);
    }

    @Override
    public <T extends Throwable> T throwing(T error) {
        this.logger.log(LogLevel.WARN, "Mixin: throwing " + error, error);
        return error;
    }

    private static LogLevel levelOf(Level level) {
        return switch (level) {
            case FATAL, ERROR -> LogLevel.ERROR;
            case WARN -> LogLevel.WARN;
            case INFO -> LogLevel.INFO;
            case DEBUG, TRACE -> LogLevel.DEBUG;
        };
    }

    private static String format(String message, Object[] arguments) {
        StringBuilder text = new StringBuilder();
        int from = 0;
        for (Object argument : arguments) {
            int at = message.indexOf(BridgeLoggerAdapter.PLACEHOLDER, from);
            if (at < 0) {
                break;
            }
            text.append(message, from, at).append(argument);
            from = at + BridgeLoggerAdapter.PLACEHOLDER.length();
        }
        return text.append(message.substring(from)).toString();
    }
}
