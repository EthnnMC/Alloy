package dev.alloy.remap.testkit;

/**
 * Test helper: Java sources of a miniature Forge, limited to the few classes the passes recognize by
 * name. Only the names, signatures and minimal behavior the tests need are reproduced.
 */
public final class ForgeStubs {

    /** Listener list; here it only keeps its parent list. */
    public static final String LISTENER_LIST = """
            package net.minecraftforge.fml.common.eventhandler;
            public class ListenerList {
                private final ListenerList parent;
                public ListenerList() { this.parent = null; }
                public ListenerList(ListenerList parent) { this.parent = parent; }
                public ListenerList getParent() { return this.parent; }
            }
            """;

    /** Base event class as Forge writes it: no own listener list, not cancelable. */
    public static final String EVENT = """
            package net.minecraftforge.fml.common.eventhandler;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            public class Event {
                @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
                public @interface HasResult { }
                private static ListenerList listeners = new ListenerList();
                private boolean canceled;
                public Event() { this.setup(); }
                public boolean isCancelable() { return false; }
                public boolean hasResult() { return false; }
                public boolean isCanceled() { return this.canceled; }
                public void setCanceled(boolean cancel) {
                    if (!this.isCancelable()) { throw new IllegalArgumentException("not cancelable"); }
                    this.canceled = cancel;
                }
                protected void setup() { }
                public ListenerList getListenerList() { return Event.listeners; }
            }
            """;

    public static final String CANCELABLE = """
            package net.minecraftforge.fml.common.eventhandler;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            public @interface Cancelable { }
            """;

    public static final String SUBSCRIBE_EVENT = """
            package net.minecraftforge.fml.common.eventhandler;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
            public @interface SubscribeEvent { }
            """;

    public static final String SIDE = """
            package net.minecraftforge.fml.relauncher;
            public enum Side { CLIENT, SERVER }
            """;

    public static final String SIDE_ONLY = """
            package net.minecraftforge.fml.relauncher;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface SideOnly { Side value(); }
            """;

    public static final String MOD = """
            package net.minecraftforge.fml.common;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Mod { String modid(); }
            """;

    private ForgeStubs() {
        // Constants class.
    }
}
