package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

class ClassPipelineTest {

    private static ClassNode anyClass() {
        ClassNode classNode = new ClassNode();
        classNode.name = "example/Any";
        return classNode;
    }

    @Test
    void appliesPassesInOrder() {
        List<String> calls = new ArrayList<>();
        ClassPass first = classNode -> {
            calls.add("first");
            return Verdict.KEEP;
        };
        ClassPass second = classNode -> {
            calls.add("second");
            return Verdict.KEEP;
        };

        Verdict verdict = new ClassPipeline(List.of(first, second)).apply(ClassPipelineTest.anyClass());

        assertEquals(Verdict.KEEP, verdict);
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void stopsAtTheFirstPassThatDropsTheClass() {
        List<String> calls = new ArrayList<>();
        ClassPass dropper = classNode -> Verdict.DROP;
        ClassPass neverReached = classNode -> {
            calls.add("reached");
            return Verdict.KEEP;
        };

        Verdict verdict = new ClassPipeline(List.of(dropper, neverReached)).apply(ClassPipelineTest.anyClass());

        assertEquals(Verdict.DROP, verdict);
        assertEquals(List.of(), calls);
    }

    @Test
    void emptyChainKeepsTheClass() {
        assertEquals(Verdict.KEEP, new ClassPipeline(List.of()).apply(ClassPipelineTest.anyClass()));
    }
}
