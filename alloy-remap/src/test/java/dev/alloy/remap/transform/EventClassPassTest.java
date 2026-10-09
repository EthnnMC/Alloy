package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ForgeStubs;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.tree.ClassNode;

/**
 * Events of a fake mod are completed by the pass, then loaded and used for real against a
 * miniature Forge ({@link ForgeStubs}).
 */
class EventClassPassTest {

    private static final String EVENT = "net.minecraftforge.fml.common.eventhandler.Event";

    /** Compiled classes, before any transformation. */
    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        EventClassPassTest.compiled = SourceCompiler.compile(8,
                ForgeStubs.LISTENER_LIST,
                ForgeStubs.EVENT,
                ForgeStubs.CANCELABLE,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.Cancelable;
                import net.minecraftforge.fml.common.eventhandler.Event;
                @Cancelable
                public class OpenEvent extends Event {
                    public final String screen;
                    public OpenEvent(String screen) { this.screen = screen; }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.Event;
                @Event.HasResult
                public class ChildEvent extends OpenEvent {
                    public ChildEvent() { super("child"); }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.Cancelable;
                @Cancelable
                public class StubbornEvent extends OpenEvent {
                    public StubbornEvent() { super("stubborn"); }
                    @Override public boolean isCancelable() { return false; }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.Event;
                import net.minecraftforge.fml.common.eventhandler.ListenerList;
                public class SelfManagedEvent extends Event {
                    private static final ListenerList OWN = new ListenerList();
                    @Override protected void setup() { }
                    @Override public ListenerList getListenerList() { return SelfManagedEvent.OWN; }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.Event;
                public class HalfManagedEvent extends Event {
                    @Override protected void setup() { }
                }
                """,
                """
                package example.mod;
                public class NotAnEvent {
                    public void setup() { }
                }
                """,
                """
                package net.minecraft.fake;
                public class VanillaLookalike extends net.minecraftforge.fml.common.eventhandler.Event {
                }
                """);
    }

    private static ClassHierarchy hierarchyOf(Map<String, byte[]> classFiles) {
        List<ClassHeader> headers = new ArrayList<>();
        classFiles.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        return new ClassHierarchy(HeaderTable.of(headers));
    }

    /** Applies the pass to all classes, after setting them to the wanted class file version. */
    private static Map<String, byte[]> transformed(int classVersion, List<String> warnings) {
        Map<String, byte[]> versioned = new LinkedHashMap<>();
        EventClassPassTest.compiled.forEach((name, classFile) ->
                versioned.put(name, ClassBytes.write(ClassBytes.readAsVersion(classFile, classVersion))));
        EventClassPass pass = new EventClassPass(EventClassPassTest.hierarchyOf(versioned), warnings::add);
        return ClassBytes.applyToAll(versioned, pass);
    }

    private static ClassLoader loaderFor(int classVersion) {
        return new InMemoryClassLoader(EventClassPassTest.transformed(classVersion, new ArrayList<>()));
    }

    private static Object listenerListOf(Object event) throws ReflectiveOperationException {
        return event.getClass().getMethod("getListenerList").invoke(event);
    }

    private static Object parentOf(Object listenerList) throws ReflectiveOperationException {
        return listenerList.getClass().getMethod("getParent").invoke(listenerList);
    }

    /**
     * Versions 49 (Java 5, no frames), 50 (Java 6, like Forge), 52 (Java 8, like mods) and 61 (Java 17):
     * the completed class must pass the JVM verifier in every case.
     */
    @ParameterizedTest
    @ValueSource(ints = {49, 50, 52, 61})
    void eventSubclassGetsItsOwnListenerListChainedToItsParent(int classVersion) throws ReflectiveOperationException {
        ClassLoader loader = EventClassPassTest.loaderFor(classVersion);
        Object base = loader.loadClass(EventClassPassTest.EVENT).getConstructor().newInstance();
        Object open = loader.loadClass("example.mod.OpenEvent").getConstructor().newInstance();
        Object child = loader.loadClass("example.mod.ChildEvent").getConstructor().newInstance();

        Object baseList = EventClassPassTest.listenerListOf(base);
        Object openList = EventClassPassTest.listenerListOf(open);
        Object childList = EventClassPassTest.listenerListOf(child);

        assertNotSame(baseList, openList);
        assertNotSame(openList, childList);
        assertSame(baseList, EventClassPassTest.parentOf(openList));
        assertSame(openList, EventClassPassTest.parentOf(childList));
    }

    @Test
    void listenerListIsCreatedOnlyOncePerClass() throws ReflectiveOperationException {
        ClassLoader loader = EventClassPassTest.loaderFor(52);
        Class<?> openEvent = loader.loadClass("example.mod.OpenEvent");

        Object first = EventClassPassTest.listenerListOf(openEvent.getConstructor().newInstance());
        Object second = EventClassPassTest.listenerListOf(openEvent.getConstructor(String.class).newInstance("inventory"));

        assertSame(first, second);
    }

    @Test
    void missingNoArgConstructorIsAddedAsPublic() throws ReflectiveOperationException {
        Class<?> openEvent = EventClassPassTest.loaderFor(52).loadClass("example.mod.OpenEvent");

        assertTrue(Modifier.isPublic(openEvent.getConstructor().getModifiers()));
        assertEquals(2, openEvent.getDeclaredConstructors().length);
    }

    @Test
    void existingNoArgConstructorIsKept() throws ReflectiveOperationException {
        Class<?> childEvent = EventClassPassTest.loaderFor(52).loadClass("example.mod.ChildEvent");

        Object child = childEvent.getConstructor().newInstance();

        assertEquals(1, childEvent.getDeclaredConstructors().length);
        assertEquals("child", childEvent.getField("screen").get(child));
    }

    @Test
    void cancelableAnnotationMakesTheEventCancelable() throws ReflectiveOperationException {
        ClassLoader loader = EventClassPassTest.loaderFor(52);
        Object open = loader.loadClass("example.mod.OpenEvent").getConstructor().newInstance();

        open.getClass().getMethod("setCanceled", boolean.class).invoke(open, true);

        assertEquals(true, open.getClass().getMethod("isCanceled").invoke(open));
        assertEquals(false, open.getClass().getMethod("hasResult").invoke(open));
    }

    @Test
    void hasResultAnnotationMakesHasResultTrue() throws ReflectiveOperationException {
        Object child = EventClassPassTest.loaderFor(52).loadClass("example.mod.ChildEvent").getConstructor().newInstance();

        assertEquals(true, child.getClass().getMethod("hasResult").invoke(child));
        // Inherited from OpenEvent, which carries @Cancelable.
        assertEquals(true, child.getClass().getMethod("isCancelable").invoke(child));
    }

    @Test
    void methodWrittenByTheAuthorIsNeverReplaced() throws ReflectiveOperationException {
        Object stubborn =
                EventClassPassTest.loaderFor(52).loadClass("example.mod.StubbornEvent").getConstructor().newInstance();

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> stubborn.getClass().getMethod("setCanceled", boolean.class).invoke(stubborn, true));

        assertEquals(IllegalArgumentException.class, error.getCause().getClass());
    }

    @Test
    void classThatManagesItsOwnListIsLeftAlone() {
        Map<String, byte[]> transformed = EventClassPassTest.transformed(52, new ArrayList<>());

        ClassNode selfManaged = ClassBytes.read(transformed.get("example/mod/SelfManagedEvent"));

        assertEquals(List.of("OWN"), selfManaged.fields.stream().map(field -> field.name).toList());
    }

    @Test
    void setupWithoutListGetterIsReportedAndLeftUnchanged() {
        List<String> warnings = new ArrayList<>();

        Map<String, byte[]> transformed = EventClassPassTest.transformed(52, warnings);

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("example/mod/HalfManagedEvent"), warnings.get(0));
        assertTrue(ClassBytes.read(transformed.get("example/mod/HalfManagedEvent")).fields.isEmpty());
    }

    @Test
    void classesOutsideTheEventHierarchyAreNotModified() {
        Map<String, byte[]> transformed = EventClassPassTest.transformed(52, new ArrayList<>());

        for (String untouched : List.of("example/mod/NotAnEvent", "net/minecraftforge/fml/common/eventhandler/Event",
                "net/minecraft/fake/VanillaLookalike")) {
            // Reference: the same class merely re-read and rewritten by ASM, without the pass.
            byte[] expected = ClassBytes.write(ClassBytes.read(EventClassPassTest.compiled.get(untouched)));
            assertArrayEquals(expected, transformed.get(untouched), untouched);
        }
    }

    @Test
    void applyingThePassTwiceChangesNothingMore() {
        Map<String, byte[]> once = EventClassPassTest.transformed(52, new ArrayList<>());
        EventClassPass pass = new EventClassPass(EventClassPassTest.hierarchyOf(once), message -> { });

        Map<String, byte[]> twice = ClassBytes.applyToAll(once, pass);

        assertArrayEquals(once.get("example/mod/OpenEvent"), twice.get("example/mod/OpenEvent"));
        assertArrayEquals(once.get("example/mod/ChildEvent"), twice.get("example/mod/ChildEvent"));
    }

    @Test
    void unknownForgeAncestorIsReportedInsteadOfGuessed() {
        // Only the mod classes are known: the case where Forge was not declared.
        Map<String, byte[]> modOnly = new LinkedHashMap<>(EventClassPassTest.compiled);
        modOnly.keySet().removeIf(name -> !name.startsWith("example/"));
        List<String> warnings = new ArrayList<>();
        // Give ChildEvent a Forge ancestor nobody knows: impossible to tell whether it is an event.
        ClassNode child = ClassBytes.read(modOnly.get("example/mod/ChildEvent"));
        child.superName = "net/minecraftforge/event/SomeEvent";
        EventClassPass pass = new EventClassPass(EventClassPassTest.hierarchyOf(modOnly), warnings::add);

        pass.apply(child);

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("net/minecraftforge/event/SomeEvent"), warnings.get(0));
        assertFalse(child.fields.stream().anyMatch(field -> field.name.equals("LISTENER_LIST")));
    }

    @Test
    void directSubclassOfEventIsRecognisedEvenWhenForgeIsUnknown() {
        Map<String, byte[]> modOnly = new LinkedHashMap<>(EventClassPassTest.compiled);
        modOnly.keySet().removeIf(name -> !name.startsWith("example/"));
        ClassNode open = ClassBytes.read(modOnly.get("example/mod/OpenEvent"));
        EventClassPass pass = new EventClassPass(EventClassPassTest.hierarchyOf(modOnly), message -> { });

        pass.apply(open);

        assertTrue(open.fields.stream().anyMatch(field -> field.name.equals("LISTENER_LIST")));
    }
}
