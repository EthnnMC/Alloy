package dev.alloy.agent.log;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.LogLevel;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Alloy's log: {@code logs/latest.log}, recreated at each launch, plus an {@code [Alloy] ...}
 * line on standard output for important messages (Lunar copies that output into its own logs).
 * Shared by the agent and, through {@link BridgeLogger}, the runtime loaded in the game.
 * {@link #log} is {@code synchronized} because classes load on several threads.
 */
public final class AlloyLog implements BridgeLogger {

    private static final String FILE_NAME = "latest.log";
    private static final String CONSOLE_PREFIX = "[Alloy] ";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final PrintWriter file;
    private final PrintStream console;
    private final boolean debugEnabled;

    private AlloyLog(PrintWriter file, PrintStream console, boolean debugEnabled) {
        this.file = file;
        this.console = Objects.requireNonNull(console, "console");
        this.debugEnabled = debugEnabled;
    }

    /**
     * Opens the log in the given folder. If the file cannot be created, logging continues on
     * standard output only rather than blocking startup.
     *
     * @param logsDirectory log folder
     * @param debugEnabled  {@code true} to keep debug messages too
     */
    public static AlloyLog open(Path logsDirectory, boolean debugEnabled) {
        PrintStream console = System.out;
        try {
            Files.createDirectories(logsDirectory);
            PrintWriter writer = new PrintWriter(
                    Files.newBufferedWriter(logsDirectory.resolve(AlloyLog.FILE_NAME), StandardCharsets.UTF_8), true);
            return new AlloyLog(writer, console, debugEnabled);
        } catch (IOException e) {
            console.println(AlloyLog.CONSOLE_PREFIX + "Cannot create the log file in " + logsDirectory + ": " + e);
            return new AlloyLog(null, console, debugEnabled);
        }
    }

    /**
     * Creates a log without a file that only writes to the given stream (tests).
     *
     * @param console output stream
     */
    public static AlloyLog toConsole(PrintStream console) {
        return new AlloyLog(null, console, true);
    }

    @Override
    public synchronized void log(LogLevel level, String message, Throwable error) {
        if (level == LogLevel.DEBUG && !this.debugEnabled) {
            return;
        }
        String line = "[" + LocalTime.now().format(AlloyLog.TIME_FORMAT) + "] [" + level + "] " + message;
        if (this.file != null) {
            this.file.println(line);
            if (error != null) {
                // The full stack trace goes to the file only.
                error.printStackTrace(this.file);
            }
            this.file.flush();
        }
        if (level != LogLevel.DEBUG) {
            this.console.println(AlloyLog.CONSOLE_PREFIX + message + (error == null ? "" : " (" + error + ")"));
        }
    }
}
