package dev.alloy.hooks;

import dev.alloy.hooks.inject.InjectionException;
import dev.alloy.hooks.inject.InjectionStrategy;
import dev.alloy.hooks.inject.TargetMethod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Applies hooks to the bytecode of a game class: reads it as an ASM tree, modifies it in memory
 * and rewrites it, without ever loading the class. It reads with {@code EXPAND_FRAMES} (all
 * frames in the same full format as the ones added) and writes with {@code COMPUTE_MAXS} only,
 * because computing frames would make ASM load game classes to learn their hierarchy. Stateless.
 */
final class ClassPatcher {

    /**
     * Installs the hooks in a class and records each result. Each hook is applied entirely or not
     * at all. On an unexpected error the whole class is abandoned, all its hooks are marked failed
     * and the error is rethrown for the caller to log.
     *
     * @param hooks the hooks targeting this class
     * @return the modified bytecode, or empty if no hook could be applied
     */
    Optional<byte[]> patch(byte[] classBytes, List<Hook> hooks, HookReport report) {
        Map<String, HookOutcome> outcomes = new LinkedHashMap<>();
        try {
            Optional<byte[]> patched = this.patchAndCollect(classBytes, hooks, outcomes);
            for (Map.Entry<String, HookOutcome> entry : outcomes.entrySet()) {
                report.record(entry.getKey(), entry.getValue());
            }
            return patched;
        } catch (RuntimeException error) {
            // The tree may be half modified: nothing done on this class is kept.
            for (Hook hook : hooks) {
                report.record(hook.id(), HookOutcome.failed("unexpected error: " + error));
            }
            throw error;
        }
    }

    private Optional<byte[]> patchAndCollect(byte[] classBytes, List<Hook> hooks, Map<String, HookOutcome> outcomes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean modified = false;
        for (Hook hook : hooks) {
            HookOutcome outcome = this.apply(classNode, hook);
            outcomes.put(hook.id(), outcome);
            modified |= outcome.status() == HookStatus.APPLIED;
        }
        if (!modified) {
            return Optional.empty();
        }
        // Passing the reader to the writer copies the original constant pool as is.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return Optional.of(writer.toByteArray());
    }

    /** Applies a hook: locates every site of every injection first, modifying the bytecode only if none is missing. */
    private HookOutcome apply(ClassNode classNode, Hook hook) {
        List<TargetMethod> methods = ClassPatcher.targetMethods(classNode, hook);
        if (methods.isEmpty()) {
            return HookOutcome.skipped("method " + hook.method() + " not found in " + classNode.name);
        }
        List<PlannedInjection> plan = new ArrayList<>();
        for (InjectionStrategy injection : hook.injections()) {
            List<PlannedInjection> planned;
            try {
                planned = ClassPatcher.locate(injection, methods);
            } catch (InjectionException refusal) {
                return HookOutcome.failed(injection.describe() + ": " + refusal.getMessage());
            }
            if (planned.isEmpty()) {
                return HookOutcome.skipped(injection.describe() + ": no injection point in " + classNode.name
                        + " (" + hook.method() + ")");
            }
            plan.addAll(planned);
        }
        // Everything is located and checked: the bytecode is only modified from here on.
        StringJoiner done = new StringJoiner("; ");
        for (PlannedInjection planned : plan) {
            planned.apply();
            done.add(planned.describe());
        }
        return HookOutcome.applied(done.toString());
    }

    /** Finds a strategy's injection sites in each target method. */
    private static List<PlannedInjection> locate(InjectionStrategy injection, List<TargetMethod> methods)
            throws InjectionException {
        List<PlannedInjection> planned = new ArrayList<>();
        for (TargetMethod method : methods) {
            List<AbstractInsnNode> sites = injection.locate(method);
            if (!sites.isEmpty()) {
                planned.add(new PlannedInjection(injection, method, sites));
            }
        }
        return planned;
    }

    private static List<TargetMethod> targetMethods(ClassNode classNode, Hook hook) {
        List<TargetMethod> methods = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            if (hook.method().matches(method.name, method.desc)) {
                methods.add(new TargetMethod(classNode.name, classNode.version, method));
            }
        }
        return methods;
    }
}
