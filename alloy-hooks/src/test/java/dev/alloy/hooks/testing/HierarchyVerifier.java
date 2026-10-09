package dev.alloy.hooks.testing;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

/**
 * ASM type verifier that answers hierarchy questions ("does A extend B?") from a
 * {@link ClassHierarchy}, so it loads no class.
 *
 * <p>The stock {@link SimpleVerifier} loads classes with {@code Class.forName}: every method
 * doing so is overridden here, and {@link #getClass(Type)} throws to prove it is never reached.</p>
 */
public final class HierarchyVerifier extends SimpleVerifier {

    private static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");

    private final ClassHierarchy hierarchy;

    /** Creates the verifier over the given hierarchy. */
    public HierarchyVerifier(ClassHierarchy hierarchy) {
        super(Opcodes.ASM9, null, null, null, false);
        this.hierarchy = hierarchy;
    }

    /**
     * Tells whether the JVM verifier would accept a value of type {@code source} in a slot of
     * type {@code target} (objects or arrays).
     */
    public boolean accepts(Type target, Type source) {
        return this.isAssignableFrom(target, source);
    }

    @Override
    protected boolean isInterface(Type type) {
        return type.getSort() == Type.OBJECT && this.hierarchy.isInterface(type.getInternalName());
    }

    @Override
    protected Type getSuperClass(Type type) {
        if (type.getSort() == Type.ARRAY) {
            return HierarchyVerifier.OBJECT_TYPE;
        }
        return this.hierarchy.superNameOf(type.getInternalName()).map(Type::getObjectType).orElse(null);
    }

    @Override
    protected boolean isAssignableFrom(Type target, Type source) {
        if (target.equals(source)) {
            return true;
        }
        if (source.getSort() == Type.ARRAY) {
            return this.acceptsArray(target, source);
        }
        return target.getSort() == Type.OBJECT
                && this.hierarchy.isAssignable(target.getInternalName(), source.getInternalName());
    }

    @Override
    protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
        Type expectedType = expected.getType();
        Type type = value.getType();
        if (type == null || expectedType == null) {
            return false;
        }
        if (expectedType.getSort() != Type.OBJECT && expectedType.getSort() != Type.ARRAY) {
            return type.equals(expectedType);
        }
        if (type.equals(BasicInterpreter.NULL_TYPE)) {
            return true;
        }
        boolean isReference = type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
        return isReference && this.isAssignableFrom(expectedType, type);
    }

    @Override
    protected Class<?> getClass(Type type) {
        throw new AssertionError("The verifier must never load a class: " + type);
    }

    /** An array fits {@code Object}, interfaces, and arrays of the same shape with a compatible element type. */
    private boolean acceptsArray(Type target, Type array) {
        if (target.getSort() != Type.ARRAY) {
            return HierarchyVerifier.OBJECT_TYPE.equals(target) || this.isInterface(target);
        }
        Type targetElement = target.getElementType();
        Type element = array.getElementType();
        if (target.getDimensions() == array.getDimensions()) {
            return targetElement.equals(element)
                    || (targetElement.getSort() == Type.OBJECT && element.getSort() == Type.OBJECT
                            && this.isAssignableFrom(targetElement, element));
        }
        // Object[] accepts a String[][]: each String[] is an object.
        return target.getDimensions() < array.getDimensions()
                && (HierarchyVerifier.OBJECT_TYPE.equals(targetElement) || this.isInterface(targetElement));
    }
}
