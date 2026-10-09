package dev.alloy.remap;

import java.util.Locale;
import java.util.Objects;

/**
 * A row of the {@link MemberShimTable}: a member that Forge adds to a Minecraft class, and how to
 * do without it. Forge mods are compiled against a Forge-patched Minecraft, so such members do not
 * exist in the game run by Lunar; renaming does not help, the instruction itself must be replaced.
 *
 * @param kind       method or field
 * @param owner      internal name of the Minecraft class Forge adds the member to
 * @param name       member name
 * @param descriptor method descriptor ({@code (I)V}) or field type ({@code I})
 * @param action     what replaces the access
 */
public record MemberShim(MemberKind kind, String owner, String name, String descriptor, ShimAction action) {

    private static final char METHOD_DESCRIPTOR_START = '(';
    private static final char SPECIAL_NAME_START = '<';

    /**
     * @throws IllegalArgumentException if the descriptor does not fit the member kind, or the member
     *                                  is a constructor (a {@code new} cannot be replaced
     *                                  instruction by instruction)
     */
    public MemberShim {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(action, "action");
        if (owner.isEmpty() || name.isEmpty() || descriptor.isEmpty()) {
            throw new IllegalArgumentException("owner, name and descriptor must not be empty");
        }
        if (name.charAt(0) == MemberShim.SPECIAL_NAME_START) {
            throw new IllegalArgumentException("constructors and static initializers cannot be shimmed: " + name);
        }
        boolean methodDescriptor = descriptor.charAt(0) == MemberShim.METHOD_DESCRIPTOR_START;
        if (methodDescriptor != (kind == MemberKind.METHOD)) {
            throw new IllegalArgumentException(
                    "descriptor '" + descriptor + "' does not fit a " + kind.name().toLowerCase(Locale.ROOT));
        }
    }
}
