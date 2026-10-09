package dev.alloy.devtools;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Entry point of the developer tools: {@code java -jar alloy-devtools.jar setup-workspace [options]}. */
public final class Main {

    private static final int EXIT_OK = 0;
    private static final int EXIT_USAGE = 2;
    private static final int EXIT_FAILURE = 1;

    private static final String USAGE = """
            Alloy developer tools

            Commands:
              setup-workspace   Generate the renamed Minecraft and Forge jars the alloy-forge module compiles against.
                  --lunar-mappings FILE   Lunar's mapping jar (default: ~/.lunarclient/offline/multiver/lunar-platform-mappings-v1_8.jar)
                  --vanilla-jar FILE      Minecraft 1.8.9 client jar (default: %APPDATA%/.minecraft/versions/1.8.9/1.8.9.jar)
                  --forge-universal FILE  Forge universal jar (default: downloaded once into ~/.alloy/libraries)
                  --out DIR               Workspace directory (default: workspace)
            """;

    private Main() {
    }

    /**
     * Runs the requested command.
     *
     * @param arguments command name followed by its options
     */
    public static void main(String[] arguments) {
        System.exit(Main.run(Arrays.asList(arguments)));
    }

    /**
     * Runs a command and returns its exit code (separate from {@link #main} so it is testable).
     *
     * @param arguments command name followed by its options
     * @return 0 on success, 2 for a usage error, 1 for a failure
     */
    static int run(List<String> arguments) {
        if (arguments.isEmpty()) {
            System.out.print(Main.USAGE);
            return Main.EXIT_USAGE;
        }
        String command = arguments.get(0);
        try {
            Options options = Options.parse(arguments.subList(1, arguments.size()));
            if ("setup-workspace".equals(command)) {
                new WorkspaceSetup(System.out).run(options);
                return Main.EXIT_OK;
            }
            System.err.println("Unknown command: " + command);
            System.out.print(Main.USAGE);
            return Main.EXIT_USAGE;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return Main.EXIT_USAGE;
        } catch (IOException e) {
            System.err.println("Failed: " + e.getMessage());
            return Main.EXIT_FAILURE;
        }
    }
}
