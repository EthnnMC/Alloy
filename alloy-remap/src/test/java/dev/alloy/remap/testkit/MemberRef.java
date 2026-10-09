package dev.alloy.remap.testkit;

/**
 * Test helper: a field or method reference found in a class's code.
 *
 * @param field      {@code true} for a field, {@code false} for a method
 * @param owner      class named by the instruction (internal name)
 * @param name       member name
 * @param descriptor member descriptor
 */
public record MemberRef(boolean field, String owner, String name, String descriptor) {

    @Override
    public String toString() {
        return (this.field ? "field " : "method ") + this.owner + "." + this.name + " " + this.descriptor;
    }
}
