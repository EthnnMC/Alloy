package dev.alloy.forge.env;

import java.lang.reflect.Field;

/**
 * Writes by reflection to private fields of Forge classes. Alloy reuses the real Forge classes
 * but skips Forge's startup (tied to its launcher), so the few fields that startup would have
 * filled are set by hand; this is the only place that bypasses encapsulation.
 */
final class Fields {

    private Fields() {
    }

    /**
     * Writes an instance field.
     *
     * @param owner  class declaring the field
     * @param target object to modify
     * @param name   field name
     * @param value  value to write
     * @throws IllegalStateException if the field does not exist: unexpected Forge version
     */
    static void set(Class<?> owner, Object target, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + owner.getName() + "." + name, e);
        }
    }

    /**
     * Writes a static field.
     *
     * @param owner class declaring the field
     * @param name  field name
     * @param value value to write
     * @throws IllegalStateException if the field does not exist
     */
    static void setStatic(Class<?> owner, String name, Object value) {
        Fields.set(owner, null, name, value);
    }
}
