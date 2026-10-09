package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the hook outcome report. */
class HookReportTest {

    private final HookReport report = new HookReport(List.of("first", "second", "third"));

    @Test
    void everyHookStartsPending() {
        assertEquals(List.of("first", "second", "third"), this.report.hookIdsWith(HookStatus.PENDING));
    }

    @Test
    void recordedOutcomeReplacesThePreviousOne() {
        this.report.record("second", HookOutcome.skipped("anchor not found"));
        this.report.record("second", HookOutcome.applied("HEAD_CALL x1"));

        assertEquals(HookOutcome.applied("HEAD_CALL x1"), this.report.outcomeOf("second"));
    }

    @Test
    void outcomesKeepTheCatalogOrder() {
        this.report.record("third", HookOutcome.applied("done"));

        assertEquals(List.of("first", "second", "third"), List.copyOf(this.report.outcomes().keySet()));
    }

    @Test
    void reportIsCompleteOnlyWhenEveryHookIsApplied() {
        this.report.record("first", HookOutcome.applied("done"));
        this.report.record("second", HookOutcome.applied("done"));
        assertFalse(this.report.isComplete());

        this.report.record("third", HookOutcome.applied("done"));
        assertTrue(this.report.isComplete());
    }

    @Test
    void summaryCountsEachStatus() {
        this.report.record("first", HookOutcome.applied("done"));
        this.report.record("second", HookOutcome.failed("refused"));

        assertEquals("1 applied, 0 skipped, 1 failed, 1 pending", this.report.summary());
    }

    @Test
    void textListsEveryHookWithItsReason() {
        this.report.record("second", HookOutcome.skipped("anchor not found"));

        assertTrue(this.report.toString().contains("[SKIPPED] second: anchor not found"));
    }

    @Test
    void unknownHookIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> this.report.record("unknown", HookOutcome.applied("done")));
    }
}
