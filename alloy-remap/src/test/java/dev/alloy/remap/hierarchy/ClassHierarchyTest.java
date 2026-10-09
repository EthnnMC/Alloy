package dev.alloy.remap.hierarchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.SourceCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

class ClassHierarchyTest {

    private static Map<String, byte[]> compiled;
    private static ClassHierarchy hierarchy;

    @BeforeAll
    static void compile() {
        ClassHierarchyTest.compiled = SourceCompiler.compile(8,
                """
                package demo;
                public interface Named { String name(); }
                """,
                """
                package demo;
                public interface Titled extends Named { String title(); }
                """,
                """
                package demo;
                public class Base implements Titled {
                    protected int size;
                    public String name() { return "base"; }
                    public String title() { return "title"; }
                }
                """,
                """
                package demo;
                public final class Leaf extends Base implements Runnable {
                    public void run() { }
                }
                """);
        List<ClassHeader> headers = new ArrayList<>();
        ClassHierarchyTest.compiled.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        ClassHierarchyTest.hierarchy = new ClassHierarchy(HeaderTable.of(headers));
    }

    @Test
    void headerListsNameParentsAndDeclaredMembers() {
        ClassHeader base = ClassHeader.read(ClassHierarchyTest.compiled.get("demo/Base"));

        assertEquals("demo/Base", base.name());
        assertEquals("java/lang/Object", base.superName());
        assertEquals(List.of("demo/Titled"), base.interfaces());
        assertEquals(Set.of(new MemberKey("size", "I")), base.fields());
        assertTrue(base.declaresMethod(new MemberKey("name", "()Ljava/lang/String;")));
        assertTrue(base.declaresMethod(new MemberKey("<init>", "()V")));
        assertFalse(base.declaresMethod(new MemberKey("run", "()V")));
    }

    @Test
    void headerKeepsAccessFlags() {
        ClassHeader leaf = ClassHeader.read(ClassHierarchyTest.compiled.get("demo/Leaf"));

        assertTrue((leaf.access() & Opcodes.ACC_FINAL) != 0);
        assertTrue((leaf.access() & Opcodes.ACC_PUBLIC) != 0);
    }

    @Test
    void unreadableBytesAreRejected() {
        assertThrows(RuntimeException.class, () -> ClassHeader.read(new byte[] {1, 2, 3}));
    }

    @Test
    void superclassChainStartsAtTheClassAndEndsAtTheFirstUnknownOne() {
        assertEquals(
                List.of("demo/Leaf", "demo/Base", "java/lang/Object"),
                ClassHierarchyTest.hierarchy.superclassChain("demo/Leaf"));
        assertEquals(List.of("not/Known"), ClassHierarchyTest.hierarchy.superclassChain("not/Known"));
    }

    @Test
    void allInterfacesIncludesInheritedAndParentInterfaces() {
        assertEquals(
                Set.of("java/lang/Runnable", "demo/Titled", "demo/Named"),
                ClassHierarchyTest.hierarchy.allInterfaces("demo/Leaf"));
        assertEquals(Set.of("demo/Named"), ClassHierarchyTest.hierarchy.allInterfaces("demo/Titled"));
    }

    @Test
    void subclassTestFollowsClassesAndInterfaces() {
        assertTrue(ClassHierarchyTest.hierarchy.isSameOrSubclassOf("demo/Leaf", "demo/Leaf"));
        assertTrue(ClassHierarchyTest.hierarchy.isSameOrSubclassOf("demo/Leaf", "demo/Base"));
        assertTrue(ClassHierarchyTest.hierarchy.isSameOrSubclassOf("demo/Leaf", "demo/Named"));
        assertFalse(ClassHierarchyTest.hierarchy.isSameOrSubclassOf("demo/Base", "demo/Leaf"));
    }

    @Test
    void cyclicHeadersDoNotLoopForever() {
        ClassHeader first = new ClassHeader("x/A", "x/B", List.of("x/I"), 0, Set.of(), Set.of());
        ClassHeader second = new ClassHeader("x/B", "x/A", List.of(), 0, Set.of(), Set.of());
        ClassHeader selfExtending = new ClassHeader("x/I", "java/lang/Object", List.of("x/I"), 0, Set.of(), Set.of());
        ClassHierarchy cyclic = new ClassHierarchy(HeaderTable.of(List.of(first, second, selfExtending)));

        assertEquals(List.of("x/A", "x/B"), cyclic.superclassChain("x/A"));
        assertEquals(Set.of("x/I"), cyclic.allInterfaces("x/A"));
    }

    @Test
    void layeredSourceAnswersFromTheFirstLayerThatKnowsTheClass() {
        ClassHeader original = new ClassHeader("x/A", "x/First", List.of(), 0, Set.of(), Set.of());
        ClassHeader shadowed = new ClassHeader("x/A", "x/Second", List.of(), 0, Set.of(), Set.of());
        ClassHeader other = new ClassHeader("x/B", "x/Second", List.of(), 0, Set.of(), Set.of());
        ClassHeaderSource layered = new LayeredHeaderSource(
                List.of(HeaderTable.of(List.of(original)), HeaderTable.of(List.of(shadowed, other))));

        assertEquals(Optional.of(original), layered.find("x/A"));
        assertEquals(Optional.of(other), layered.find("x/B"));
        assertEquals(Optional.empty(), layered.find("x/C"));
    }
}
