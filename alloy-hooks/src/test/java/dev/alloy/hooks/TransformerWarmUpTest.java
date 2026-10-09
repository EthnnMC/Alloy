package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Checks that the warm-up rehearsal really exercises the whole transformation path. */
class TransformerWarmUpTest {

    @Test
    void rehearsalAppliesOneHookPerStrategy() {
        HookReport rehearsal = TransformerWarmUp.run(new ClassPatcher(), new GameLoaderBridgePatch());

        assertTrue(rehearsal.isComplete(), rehearsal.toString());
        assertEquals(7, rehearsal.outcomes().size());
    }
}
