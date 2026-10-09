package dev.alloy.hooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The catalog of hooks, as data: everything Alloy injects into Minecraft classes. Keeping the
 * table (what and where) apart from the mechanism (how) lets an anchor be fixed after a Lunar
 * update without touching bytecode code. Immutable and thread-safe.
 */
public final class HookCatalog {

    /** All hooks, in table order. */
    private final List<Hook> hooks;

    /** The same hooks keyed by target class internal name: the lookup made on every class load. */
    private final Map<String, List<Hook>> hooksByClass;

    private HookCatalog(List<Hook> hooks) {
        this.hooks = List.copyOf(hooks);
        HookCatalog.requireUniqueIds(this.hooks);
        Map<String, List<Hook>> grouped = new LinkedHashMap<>();
        for (Hook hook : this.hooks) {
            grouped.computeIfAbsent(hook.className(), className -> new ArrayList<>()).add(hook);
        }
        grouped.replaceAll((className, classHooks) -> List.copyOf(classHooks));
        this.hooksByClass = Collections.unmodifiableMap(grouped);
    }

    /** Creates the default catalog: the hooks needed by the Forge 1.8.9 events, checked against real Lunar classes. */
    public static HookCatalog forgeDefaults() {
        return new HookCatalog(ForgeHookTable.hooks());
    }

    /**
     * Creates a catalog from any list (tests, experiments).
     *
     * @throws IllegalArgumentException if two hooks share an id
     */
    public static HookCatalog of(List<Hook> hooks) {
        return new HookCatalog(hooks);
    }

    /** Returns all hooks as an unmodifiable list, in table order. */
    public List<Hook> hooks() {
        return this.hooks;
    }

    /** Returns all hook ids as an unmodifiable list, in table order. */
    public List<String> hookIds() {
        return this.hooks.stream().map(Hook::id).toList();
    }

    /**
     * Returns the hooks to install in a class; empty if the class is not targeted.
     *
     * @param internalClassName class internal name (with slashes)
     */
    public List<Hook> hooksFor(String internalClassName) {
        return this.hooksByClass.getOrDefault(internalClassName, List.of());
    }

    /** Returns the internal names of the classes targeted by at least one hook. */
    public Set<String> classNames() {
        return this.hooksByClass.keySet();
    }

    private static void requireUniqueIds(List<Hook> hooks) {
        Set<String> seen = new HashSet<>();
        for (Hook hook : hooks) {
            if (!seen.add(hook.id())) {
                throw new IllegalArgumentException("Duplicate hook id: " + hook.id());
            }
        }
    }
}
