package dev.alloy.hooks.inject;

import java.util.List;

/** Which of several candidate injection sites of a method to keep, in instruction order. */
public enum Occurrence {

    /** Only the first. */
    FIRST,

    /** Only the last: for a {@code RETURN}, the normal exit at the end of the method. */
    LAST,

    /** All of them. */
    EVERY;

    /** Selects the wanted candidates; returns an unmodifiable list, empty if there are none. */
    public <T> List<T> select(List<T> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return switch (this) {
            case FIRST -> List.of(candidates.get(0));
            case LAST -> List.of(candidates.get(candidates.size() - 1));
            case EVERY -> List.copyOf(candidates);
        };
    }
}
