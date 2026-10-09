package dev.alloy.bridge;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What the agent gives the Mixin host to start it. Only JDK and bridge types, since the host
 * lives in another class loader.
 *
 * @param configs      names of the mixin configuration files of the mods, in loading order
 * @param modLoader    the class loader holding the mods (mixin plugins are loaded from it)
 * @param classBytes   class file of a class by internal name, as it exists at run time;
 *                     {@code null} if unknown
 * @param resources    a resource of the mod jars by name; {@code null} if absent
 * @param loadedEarly  tells whether a class (dotted name) was already loaded before the host
 *                     started, which means mixins can no longer be applied to it
 * @param logger       Alloy's log
 */
public record MixinSetup(
        List<String> configs,
        ClassLoader modLoader,
        Function<String, byte[]> classBytes,
        Function<String, InputStream> resources,
        Predicate<String> loadedEarly,
        BridgeLogger logger) {

    public MixinSetup {
        configs = List.copyOf(configs);
        Objects.requireNonNull(modLoader, "modLoader");
        Objects.requireNonNull(classBytes, "classBytes");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(loadedEarly, "loadedEarly");
        Objects.requireNonNull(logger, "logger");
    }
}
