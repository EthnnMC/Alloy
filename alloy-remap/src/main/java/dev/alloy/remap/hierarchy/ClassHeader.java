package dev.alloy.remap.hierarchy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A class's "header": what is needed to reason about inheritance, without its code. It is read
 * from the {@code .class} bytes, never by loading the class. All names are internal names.
 *
 * @param name       class name
 * @param superName  super-class name; {@code null} only for {@code java/lang/Object}
 * @param interfaces directly implemented (or extended) interfaces
 * @param access     class access flags
 * @param fields     fields declared by the class itself
 * @param methods    methods declared by the class itself, constructors included
 */
public record ClassHeader(
        String name,
        String superName,
        List<String> interfaces,
        int access,
        Set<MemberKey> fields,
        Set<MemberKey> methods) {

    public ClassHeader {
        Objects.requireNonNull(name, "name");
        interfaces = List.copyOf(interfaces);
        fields = Set.copyOf(fields);
        methods = Set.copyOf(methods);
    }

    /**
     * Reads the header from {@code .class} bytes, skipping method code.
     *
     * @throws RuntimeException if the bytes are not a readable class file (ASM reports this with
     *                          several different unchecked exceptions)
     */
    public static ClassHeader read(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        Set<MemberKey> fields = new HashSet<>();
        Set<MemberKey> methods = new HashSet<>();
        ClassVisitor collector = new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.add(new MemberKey(name, descriptor));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.add(new MemberKey(name, descriptor));
                return null;
            }
        };
        reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassHeader(
                reader.getClassName(),
                reader.getSuperName(),
                List.of(reader.getInterfaces()),
                reader.getAccess(),
                fields,
                methods);
    }

    /**
     * Builds the header of a class already read as an ASM tree.
     */
    public static ClassHeader of(ClassNode classNode) {
        Set<MemberKey> fields = new HashSet<>();
        for (FieldNode field : classNode.fields) {
            fields.add(new MemberKey(field.name, field.desc));
        }
        Set<MemberKey> methods = new HashSet<>();
        for (MethodNode method : classNode.methods) {
            methods.add(new MemberKey(method.name, method.desc));
        }
        return new ClassHeader(
                classNode.name, classNode.superName, classNode.interfaces, classNode.access, fields, methods);
    }

    /**
     * Translates the header into another namespace (class, parents, members, descriptor types).
     *
     * @return a new header; this one is unchanged
     */
    public ClassHeader mapped(Remapper remapper) {
        Set<MemberKey> mappedFields = new HashSet<>();
        for (MemberKey field : this.fields) {
            mappedFields.add(new MemberKey(
                    remapper.mapFieldName(this.name, field.name(), field.descriptor()),
                    remapper.mapDesc(field.descriptor())));
        }
        Set<MemberKey> mappedMethods = new HashSet<>();
        for (MemberKey method : this.methods) {
            mappedMethods.add(new MemberKey(
                    remapper.mapMethodName(this.name, method.name(), method.descriptor()),
                    remapper.mapMethodDesc(method.descriptor())));
        }
        return new ClassHeader(
                remapper.mapType(this.name),
                this.superName == null ? null : remapper.mapType(this.superName),
                this.interfaces.stream().map(remapper::mapType).toList(),
                this.access,
                mappedFields,
                mappedMethods);
    }

    /**
     * Tells whether the class itself declares this field (not inherited).
     */
    public boolean declaresField(MemberKey key) {
        return this.fields.contains(key);
    }

    /**
     * Tells whether the class itself declares this method (not inherited).
     */
    public boolean declaresMethod(MemberKey key) {
        return this.methods.contains(key);
    }
}
