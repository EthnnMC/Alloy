package dev.alloy.remap;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable "SRG name to in-game name" table for Minecraft fields and methods. Each SRG name
 * ({@code func_71410_x}) is unique in the whole game, so <b>the name alone</b> is enough to
 * translate it, without the owning class. A name absent from the table is not an error: the member
 * really has that name in the game.
 */
public final class SrgNameTable {

    private final Map<String, String> runtimeNames;

    private SrgNameTable(Map<String, String> runtimeNames) {
        this.runtimeNames = Map.copyOf(runtimeNames);
    }

    /**
     * Builds a table from "SRG name to game name" pairs; the table keeps a copy.
     */
    public static SrgNameTable of(Map<String, String> runtimeNames) {
        return new SrgNameTable(runtimeNames);
    }

    /**
     * Translates a member name.
     *
     * @param name name read from the mod (SRG or not)
     * @return the game name if {@code name} is a known SRG name, else {@code name} unchanged
     */
    public String runtimeName(String name) {
        return this.runtimeNames.getOrDefault(name, name);
    }

    /**
     * Looks up the translation of an SRG name; empty if unknown.
     */
    public Optional<String> find(String srgName) {
        return Optional.ofNullable(this.runtimeNames.get(srgName));
    }

    /**
     * Returns the number of known SRG names.
     */
    public int size() {
        return this.runtimeNames.size();
    }

    /**
     * Returns the whole table as an unmodifiable "SRG name to game name" view.
     */
    public Map<String, String> asMap() {
        return this.runtimeNames;
    }
}
