package dev.alloy.agent.forge;

import dev.alloy.remap.MemberShimTable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * The extracted jar of Alloy's Forge runtime (module {@code alloy-forge}) and what the agent
 * needs to know about it.
 *
 * @param jar             the jar extracted into the cache
 * @param contentHash     hash of its content; part of the cache keys, since a different runtime changes the jars to produce
 * @param overlaidClasses internal names of the Forge classes this runtime replaces with its own
 * @param shims           replacements for the members Forge adds to Minecraft
 */
public record RuntimeJar(Path jar, String contentHash, Set<String> overlaidClasses, MemberShimTable shims) {

    /** Rejects {@code null} and copies the set. */
    public RuntimeJar {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(shims, "shims");
        overlaidClasses = Set.copyOf(overlaidClasses);
    }
}
