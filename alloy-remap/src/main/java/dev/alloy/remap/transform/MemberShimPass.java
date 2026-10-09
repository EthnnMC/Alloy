package dev.alloy.remap.transform;

import dev.alloy.remap.MemberKind;
import dev.alloy.remap.MemberShim;
import dev.alloy.remap.MemberShimTable;
import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.MemberKey;

import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Replaces, in mod code, accesses to members that Forge adds to Minecraft.
 *
 * <p>For each method call or field access, it finds a {@link MemberShimTable} row with the same
 * name and descriptor, checks that the class named by the instruction is the row's class <b>or one
 * of its subclasses</b>, then asks the row's action for the replacement. The subclass test is
 * essential: bytecode names the class the programmer used, e.g. {@code MyScreen.drawHoveringText(...)}
 * rather than {@code GuiScreen.drawHoveringText(...)}.</p>
 */
public final class MemberShimPass implements ClassPass {

    private final MemberShimTable shims;
    private final ClassHierarchy hierarchy;

    /**
     * @param shims     the members to replace
     * @param hierarchy inheritance of the mod, Forge and Minecraft classes, in game names
     */
    public MemberShimPass(MemberShimTable shims, ClassHierarchy hierarchy) {
        this.shims = Objects.requireNonNull(shims, "shims");
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        if (this.shims.isEmpty()) {
            return Verdict.KEEP;
        }
        for (MethodNode method : classNode.methods) {
            // toArray(): iterate over a copy, since the instruction list is modified on the way.
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                this.replacementFor(instruction)
                        .ifPresent(replacement -> method.instructions.set(instruction, replacement));
            }
        }
        return Verdict.KEEP;
    }

    private Optional<AbstractInsnNode> replacementFor(AbstractInsnNode instruction) {
        if (instruction instanceof MethodInsnNode call) {
            return this.replacementFor(MemberKind.METHOD, call.owner, call.name, call.desc, call.getOpcode());
        }
        if (instruction instanceof FieldInsnNode access) {
            return this.replacementFor(MemberKind.FIELD, access.owner, access.name, access.desc, access.getOpcode());
        }
        return Optional.empty();
    }

    private Optional<AbstractInsnNode> replacementFor(
            MemberKind kind, String owner, String name, String descriptor, int opcode) {
        for (MemberShim shim : this.shims.candidates(kind, name, descriptor)) {
            if (this.reaches(owner, shim)) {
                return shim.action().replacement(shim, opcode);
            }
        }
        return Optional.empty();
    }

    /**
     * Tells whether an access written on class {@code accessOwner} reaches the row's member.
     * Walking up the super-classes, if a class declaring an identical member comes first, that
     * member is the target (it really exists, in the mod) and nothing must be replaced.
     */
    private boolean reaches(String accessOwner, MemberShim shim) {
        if (accessOwner.equals(shim.owner())) {
            // Most frequent case, handled without consulting the (costly) inheritance.
            return true;
        }
        MemberKey member = new MemberKey(shim.name(), shim.descriptor());
        for (String className : this.hierarchy.superclassChain(accessOwner)) {
            if (className.equals(shim.owner())) {
                return true;
            }
            if (this.declares(className, shim.kind(), member)) {
                return false;
            }
        }
        // The row's class may also be a Minecraft interface.
        return this.hierarchy.allInterfaces(accessOwner).contains(shim.owner());
    }

    private boolean declares(String className, MemberKind kind, MemberKey member) {
        Optional<ClassHeader> header = this.hierarchy.find(className);
        if (header.isEmpty()) {
            return false;
        }
        return kind == MemberKind.METHOD ? header.get().declaresMethod(member) : header.get().declaresField(member);
    }
}
