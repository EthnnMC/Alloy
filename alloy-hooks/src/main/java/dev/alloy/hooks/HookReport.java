package dev.alloy.hooks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-hook result of the installation: what was injected, or why nothing was. The transformer
 * logs nothing while modifying a class (see {@link GameClassTransformer}); it records here and
 * the agent reads or logs the report later. Classes load on several threads, hence the
 * {@link ConcurrentHashMap}.
 */
public final class HookReport {

    /** Hook ids, in catalog order. */
    private final List<String> hookIds;

    /** Latest known result of each hook. */
    private final Map<String, HookOutcome> outcomes = new ConcurrentHashMap<>();

    /**
     * Creates a report where every hook is still pending.
     *
     * @param hookIds hook ids, in catalog order
     */
    public HookReport(List<String> hookIds) {
        this.hookIds = List.copyOf(hookIds);
        for (String hookId : this.hookIds) {
            this.outcomes.put(hookId, HookOutcome.pending());
        }
    }

    /**
     * Records a hook's result; a class can be retransformed, so the latest result replaces the
     * previous one.
     *
     * @throws IllegalArgumentException if the id is not in the catalog
     */
    void record(String hookId, HookOutcome outcome) {
        this.requireKnown(hookId);
        this.outcomes.put(hookId, outcome);
    }

    /**
     * Returns a hook's latest known result.
     *
     * @throws IllegalArgumentException if the id is not in the catalog
     */
    public HookOutcome outcomeOf(String hookId) {
        this.requireKnown(hookId);
        return this.outcomes.get(hookId);
    }

    /** Returns an unmodifiable copy of all results, in catalog order. */
    public Map<String, HookOutcome> outcomes() {
        Map<String, HookOutcome> snapshot = new LinkedHashMap<>();
        for (String hookId : this.hookIds) {
            snapshot.put(hookId, this.outcomes.get(hookId));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /** Lists the ids of the hooks in the given status, in catalog order. */
    public List<String> hookIdsWith(HookStatus status) {
        return this.hookIds.stream().filter(hookId -> this.outcomes.get(hookId).status() == status).toList();
    }

    /** Returns whether every hook is {@link HookStatus#APPLIED}. */
    public boolean isComplete() {
        return this.hookIdsWith(HookStatus.APPLIED).size() == this.hookIds.size();
    }

    /** One-line summary for the log, for example {@code 43 applied, 1 skipped, 0 failed, 1 pending}. */
    public String summary() {
        return this.hookIdsWith(HookStatus.APPLIED).size() + " applied, "
                + this.hookIdsWith(HookStatus.SKIPPED).size() + " skipped, "
                + this.hookIdsWith(HookStatus.FAILED).size() + " failed, "
                + this.hookIdsWith(HookStatus.PENDING).size() + " pending";
    }

    /** The summary, then one line per hook. */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("Hooks: ").append(this.summary());
        this.outcomes().forEach((hookId, outcome) -> text
                .append(System.lineSeparator())
                .append("  [").append(outcome.status()).append("] ")
                .append(hookId).append(": ").append(outcome.detail()));
        return text.toString();
    }

    private void requireKnown(String hookId) {
        if (!this.outcomes.containsKey(hookId)) {
            throw new IllegalArgumentException("Unknown hook id: " + hookId);
        }
    }
}
