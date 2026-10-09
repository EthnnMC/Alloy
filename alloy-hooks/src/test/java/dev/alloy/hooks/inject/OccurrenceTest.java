package dev.alloy.hooks.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the choice of occurrences among several injection points. */
class OccurrenceTest {

    private static final List<String> CANDIDATES = List.of("a", "b", "c");

    @Test
    void firstKeepsOnlyTheFirstCandidate() {
        assertEquals(List.of("a"), Occurrence.FIRST.select(OccurrenceTest.CANDIDATES));
    }

    @Test
    void lastKeepsOnlyTheLastCandidate() {
        assertEquals(List.of("c"), Occurrence.LAST.select(OccurrenceTest.CANDIDATES));
    }

    @Test
    void everyKeepsAllCandidatesInOrder() {
        assertEquals(List.of("a", "b", "c"), Occurrence.EVERY.select(OccurrenceTest.CANDIDATES));
    }

    @Test
    void nothingIsSelectedWhenThereIsNoCandidate() {
        assertEquals(List.of(), Occurrence.LAST.select(List.of()));
    }
}
