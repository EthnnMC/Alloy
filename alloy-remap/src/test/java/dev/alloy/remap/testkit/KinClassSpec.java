package dev.alloy.remap.testkit;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: describes one class to write into a synthetic {@code .kin} file (see {@link KinFixture}).
 */
public final class KinClassSpec {

    private final String sourceName;
    private final String targetName;
    private final List<KinClassSpec> innerClasses = new ArrayList<>();
    private final List<String[]> fields = new ArrayList<>();
    private final List<String[]> methods = new ArrayList<>();

    private KinClassSpec(String sourceName, String targetName) {
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    /**
     * Describes a class.
     *
     * @param sourceName source name: fully qualified for a top-level class, simple name for an inner class
     * @param targetName target name, same convention
     */
    public static KinClassSpec type(String sourceName, String targetName) {
        return new KinClassSpec(sourceName, targetName);
    }

    public KinClassSpec field(String name, String descriptor, String targetName) {
        this.fields.add(new String[] {name, descriptor, targetName});
        return this;
    }

    public KinClassSpec method(String name, String descriptor, String targetName) {
        this.methods.add(new String[] {name, descriptor, targetName});
        return this;
    }

    public KinClassSpec inner(KinClassSpec innerClass) {
        this.innerClasses.add(innerClass);
        return this;
    }

    void writeTo(DataOutputStream output) throws IOException {
        output.writeUTF(this.sourceName);
        output.writeUTF(this.targetName);
        output.writeInt(this.innerClasses.size());
        for (KinClassSpec innerClass : this.innerClasses) {
            innerClass.writeTo(output);
        }
        KinClassSpec.writeMembers(output, this.fields);
        KinClassSpec.writeMembers(output, this.methods);
    }

    private static void writeMembers(DataOutputStream output, List<String[]> members) throws IOException {
        output.writeInt(members.size());
        for (String[] member : members) {
            for (String part : member) {
                output.writeUTF(part);
            }
        }
    }
}
