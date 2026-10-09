package dev.alloy.devtools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Command arguments split into {@code --name value} options and positional arguments (those not
 * starting with {@code --}). Immutable.
 */
public final class Options {

    private static final String OPTION_PREFIX = "--";

    private final Map<String, String> named;
    private final List<String> positional;

    private Options(Map<String, String> named, List<String> positional) {
        this.named = Map.copyOf(named);
        this.positional = List.copyOf(positional);
    }

    /**
     * Splits a list of arguments.
     *
     * @param arguments command arguments, without the command name
     * @throws IllegalArgumentException if an option is not followed by its value
     */
    public static Options parse(List<String> arguments) {
        Map<String, String> named = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!argument.startsWith(Options.OPTION_PREFIX)) {
                positional.add(argument);
                continue;
            }
            if (i + 1 >= arguments.size()) {
                throw new IllegalArgumentException("Option " + argument + " needs a value");
            }
            named.put(argument.substring(Options.OPTION_PREFIX.length()), arguments.get(i + 1));
            i++;
        }
        return new Options(named, positional);
    }

    /**
     * Reads an option that names a file or folder.
     *
     * @param name option name, without the dashes
     * @return the path, or empty if the option is absent
     */
    public Optional<Path> path(String name) {
        return Optional.ofNullable(this.named.get(name)).map(Path::of);
    }

    /** Returns the positional arguments, in order, as an unmodifiable list. */
    public List<String> positional() {
        return this.positional;
    }
}
