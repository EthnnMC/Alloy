package dev.alloy.remap.transform;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.MemberKey;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reports accesses to Minecraft members that exist nowhere in the game: members Forge adds to
 * Minecraft and that no shim replaced. Run it after {@link MemberShimPass}; the class is left
 * unchanged. Each member is reported once per jar.
 */
public final class MissingMemberPass implements ClassPass {

    private static final String OBJECT = "java/lang/Object";
    private static final char ARRAY_PREFIX = '[';

    private final ClassHierarchy hierarchy;
    private final Consumer<String> warnings;
    private final Set<String> reported = new HashSet<>();

    /**
     * @param hierarchy inheritance of the mod, Forge and Minecraft classes, in game names
     * @param warnings  receives one message per missing member
     */
    public MissingMemberPass(ClassHierarchy hierarchy, Consumer<String> warnings) {
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    this.check(call.owner, new MemberKey(call.name, call.desc), true);
                } else if (instruction instanceof FieldInsnNode access) {
                    this.check(access.owner, new MemberKey(access.name, access.desc), false);
                }
            }
        }
        return Verdict.KEEP;
    }

    private void check(String owner, MemberKey member, boolean method) {
        if (owner.charAt(0) == MissingMemberPass.ARRAY_PREFIX || !this.isMissing(owner, member, method)) {
            return;
        }
        String description = owner + "." + member.name() + (method ? "" : " ") + member.descriptor();
        if (this.reported.add(description)) {
            this.warnings.accept("Uses " + description + ", a member Forge adds to Minecraft that Alloy"
                    + " does not replace yet: the code calling it will fail");
        }
    }

    /**
     * Tells whether the member is certainly absent: the access goes through a Minecraft class and
     * no class or interface up the hierarchy declares it. An unknown ancestor (a library class)
     * means "cannot tell", which is not reported.
     */
    private boolean isMissing(String owner, MemberKey member, boolean method) {
        boolean throughMinecraft = false;
        for (String className : this.hierarchy.superclassChain(owner)) {
            if (className.equals(MissingMemberPass.OBJECT)) {
                break;
            }
            Optional<ClassHeader> header = this.hierarchy.find(className);
            if (header.isEmpty() || MissingMemberPass.declares(header.get(), member, method)) {
                return false;
            }
            throughMinecraft |= className.startsWith(ForgeNames.MINECRAFT_PACKAGE);
        }
        if (!throughMinecraft) {
            return false;
        }
        for (String interfaceName : this.hierarchy.allInterfaces(owner)) {
            Optional<ClassHeader> header = this.hierarchy.find(interfaceName);
            if (header.isEmpty() || MissingMemberPass.declares(header.get(), member, method)) {
                return false;
            }
        }
        // Reached java/lang/Object: only its own methods can still match.
        return !(method && MissingMemberPass.isObjectMethod(member));
    }

    private static boolean declares(ClassHeader header, MemberKey member, boolean method) {
        return method ? header.declaresMethod(member) : header.declaresField(member);
    }

    private static boolean isObjectMethod(MemberKey member) {
        return switch (member.name()) {
            case "getClass", "hashCode", "equals", "toString", "notify", "notifyAll", "wait", "clone", "finalize" -> true;
            default -> false;
        };
    }
}
