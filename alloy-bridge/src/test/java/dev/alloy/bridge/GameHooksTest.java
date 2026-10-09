package dev.alloy.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests {@link GameHooks} from the game's side: neutral values, delegation, no leaked exception. */
class GameHooksTest {

    @BeforeEach
    void resetBefore() {
        GameHooks.resetForTests();
    }

    @AfterEach
    void resetAfter() {
        GameHooks.resetForTests();
    }

    @Test
    void isInactiveUntilASinkIsInstalled() {
        assertFalse(GameHooks.isActive());

        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) -> null));

        assertTrue(GameHooks.isActive());
    }

    @Test
    void cancellableHookAnswersFalseWithoutSink() {
        assertFalse(GameHooks.chatReceived(new Object()));
    }

    @Test
    void filterHookReturnsItsInputWithoutSink() {
        assertEquals(0.7F, GameHooks.fovUpdate(0.7F, new Object()));
    }

    @Test
    void guiOpenKeepsTheRequestedScreenWithoutSink() {
        Object screen = new Object();

        boolean cancelled = GameHooks.guiOpen(screen);

        assertFalse(cancelled);
        assertSame(screen, GameHooks.guiOpenResult());
    }

    @Test
    void guiOpenReturnsTheScreenChosenBySink() {
        Object replacement = new Object();
        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) ->
                method.equals("onGuiOpen") ? GuiOpenDecision.show(replacement) : null));

        boolean cancelled = GameHooks.guiOpen(new Object());

        assertFalse(cancelled);
        assertSame(replacement, GameHooks.guiOpenResult());
    }

    @Test
    void guiOpenReportsCancellation() {
        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) ->
                method.equals("onGuiOpen") ? GuiOpenDecision.cancel() : null));

        assertTrue(GameHooks.guiOpen(new Object()));
        assertNull(GameHooks.guiOpenResult());
    }

    @Test
    void sinkFailureNeverReachesTheGame() {
        List<String> logged = new ArrayList<>();
        GameHooks.setLogger((level, message, error) -> logged.add(level + " " + message));
        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) -> {
            throw new IllegalStateException("boom");
        }));

        GameHooks.clientTickStart();

        assertEquals(List.of("ERROR Hook 'clientTickStart' failed"), logged);
    }

    @Test
    void repeatedFailuresAreLoggedAtMostThreeTimes() {
        List<String> logged = new ArrayList<>();
        GameHooks.setLogger((level, message, error) -> logged.add(message));
        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) -> {
            throw new IllegalStateException("boom");
        }));

        for (int i = 0; i < 10; i++) {
            GameHooks.renderTickStart();
        }

        assertEquals(3, logged.size());
    }

    @Test
    void autoCompleteKeepsServerCompletionsWhenSinkAnswersNull() {
        String[] fromServer = {"alice", "bob"};
        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) -> null));

        assertArrayEquals(fromServer, GameHooks.autoCompleteResponse(fromServer));
    }

    @Test
    void firedHooksAreRemembered() {
        GameHooks.install(GameHooksTest.sinkAnswering((method, arguments) -> null));

        GameHooks.worldUnload();

        assertTrue(GameHooks.firedHooks().contains("worldUnload"));
    }

    @Test
    void gameStartListenerReceivesAnEmptyArrayInsteadOfNull() {
        List<Integer> lengths = new ArrayList<>();
        GameHooks.setGameStartListener(arguments -> lengths.add(arguments.length));

        GameHooks.gameMain(null);

        assertEquals(List.of(0), lengths);
    }

    /** Answer of a fake sink: called method name and arguments to returned value. */
    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] arguments);
    }

    /**
     * Builds a fake sink with a dynamic proxy; for methods returning a primitive, {@code null}
     * becomes a neutral value ({@code false}, {@code 0}).
     */
    private static GameEventSink sinkAnswering(Answer answer) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            Object result = answer.answer(method.getName(), arguments);
            if (result != null || !method.getReturnType().isPrimitive()) {
                return result;
            }
            if (method.getReturnType() == boolean.class) {
                return Boolean.FALSE;
            }
            if (method.getReturnType() == float.class) {
                return 0.0F;
            }
            return null;
        };
        return (GameEventSink) Proxy.newProxyInstance(
                GameEventSink.class.getClassLoader(), new Class<?>[] {GameEventSink.class}, handler);
    }
}
