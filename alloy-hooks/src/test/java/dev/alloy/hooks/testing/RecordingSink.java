package dev.alloy.hooks.testing;

import dev.alloy.bridge.GameEventSink;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Fake Forge runtime: records every hook it receives in {@link GameTrace} and answers what the
 * test set up. A dynamic proxy of {@link GameEventSink}; without a set-up answer a method returns
 * the neutral value of its type ({@code false}, {@code 0} or {@code null}).
 */
public final class RecordingSink {

    /** Set-up answers, by {@link GameEventSink} method name. */
    private final Map<String, Function<Object[], Object>> answers = new HashMap<>();

    /** Sets a fixed answer for a {@link GameEventSink} method; returns this object for chaining. */
    public RecordingSink answer(String sinkMethod, Object value) {
        return this.answerWith(sinkMethod, arguments -> value);
    }

    /** Sets an answer computed from the received arguments; returns this object for chaining. */
    public RecordingSink answerWith(String sinkMethod, Function<Object[], Object> answer) {
        this.answers.put(sinkMethod, answer);
        return this;
    }

    /** Returns the {@link GameEventSink} proxy of this object, to install in {@code GameHooks}. */
    public GameEventSink asSink() {
        InvocationHandler handler = (proxy, method, arguments) -> this.handle(method, arguments);
        return (GameEventSink) Proxy.newProxyInstance(
                GameEventSink.class.getClassLoader(), new Class<?>[] {GameEventSink.class}, handler);
    }

    private Object handle(Method method, Object[] arguments) {
        Object[] received = arguments == null ? new Object[0] : arguments;
        GameTrace.record(method.getName() + RecordingSink.render(received));
        Function<Object[], Object> answer = this.answers.get(method.getName());
        if (answer != null) {
            return answer.apply(received);
        }
        if (method.getReturnType() == boolean.class) {
            return Boolean.FALSE;
        }
        if (method.getReturnType() == float.class) {
            return 0.0F;
        }
        return null;
    }

    /** Renders the arguments stably: the value for simple types, the class name otherwise. */
    private static String render(Object[] arguments) {
        StringJoiner text = new StringJoiner(", ", "(", ")");
        for (Object argument : arguments) {
            if (argument == null || argument instanceof String || argument instanceof Number
                    || argument instanceof Boolean) {
                text.add(String.valueOf(argument));
            } else if (argument instanceof Object[] array) {
                text.add(Arrays.toString(array));
            } else {
                text.add(argument.getClass().getSimpleName());
            }
        }
        return text.toString();
    }
}
