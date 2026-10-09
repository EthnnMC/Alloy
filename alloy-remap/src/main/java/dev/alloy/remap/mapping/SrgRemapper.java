package dev.alloy.remap.mapping;

import dev.alloy.remap.SrgNameTable;

import java.util.Objects;

import org.objectweb.asm.commons.Remapper;

/**
 * Remapper for Forge mods: SRG names to game names, by member name alone. Only <b>member</b> names
 * change; class names and descriptors are identical in a Forge mod and in the game run by Lunar.
 * The owner class is deliberately ignored (see {@link SrgNameTable}): a mod often accesses an
 * inherited field through a subclass.
 */
public final class SrgRemapper extends Remapper {

    private final SrgNameTable names;

    public SrgRemapper(SrgNameTable names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return this.names.runtimeName(name);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return this.names.runtimeName(name);
    }

    /**
     * For a lambda, the {@code invokedynamic} name is the implemented interface's method name,
     * which is an SRG name if the interface comes from Minecraft.
     */
    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        return this.names.runtimeName(name);
    }
}
