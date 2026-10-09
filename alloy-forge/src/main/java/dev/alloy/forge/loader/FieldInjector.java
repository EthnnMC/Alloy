package dev.alloy.forge.loader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

/**
 * Fills the fields mods mark with {@code @Mod.Instance} and {@code @Mod.Metadata}, following
 * {@code FMLModContainer}'s rules: the annotation may target another mod by id, static fields are
 * filled anywhere, and an instance field only if it belongs to the mod's main class.
 */
public final class FieldInjector {

    private static final String TARGET_MOD_KEY = "value";

    private final ModRegistry registry;
    private final ASMDataTable table;
    private final ClassLoader loader;

    /**
     * Creates the injector.
     *
     * @param registry mod registry (to find a mod by id)
     * @param table    annotation table read from the jars
     * @param loader   the mod class loader
     */
    public FieldInjector(ModRegistry registry, ASMDataTable table, ClassLoader loader) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.table = Objects.requireNonNull(table, "table");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Fills all the annotated fields of a mod.
     *
     * @param mod the mod, already constructed
     * @throws ReflectiveOperationException if a class or field listed by the table is missing
     */
    public void inject(AlloyModContainer mod) throws ReflectiveOperationException {
        this.inject(mod, Mod.Instance.class.getName(), ModContainer::getMod);
        this.inject(mod, Mod.Metadata.class.getName(), ModContainer::getMetadata);
    }

    private void inject(AlloyModContainer mod, String annotationName, Function<ModContainer, Object> valueOf)
            throws ReflectiveOperationException {
        for (ASMDataTable.ASMData annotated : this.table.getAnnotationsFor(mod).get(annotationName)) {
            ModContainer target = this.targetOf(mod, annotated.getAnnotationInfo());
            Class<?> declaringClass = Class.forName(annotated.getClassName(), true, this.loader);
            Field field = declaringClass.getDeclaredField(annotated.getObjectName());
            field.setAccessible(true);
            Object value = target == null ? null : valueOf.apply(target);
            if (Modifier.isStatic(field.getModifiers())) {
                field.set(null, value);
            } else if (declaringClass.isInstance(mod.getMod())) {
                field.set(mod.getMod(), value);
            }
        }
    }

    /** The mod whose object is injected: the one named by the annotation, otherwise the mod itself. */
    private ModContainer targetOf(AlloyModContainer mod, Map<String, Object> annotationValues) {
        Object requested = annotationValues.get(FieldInjector.TARGET_MOD_KEY);
        if (!(requested instanceof String modId) || modId.isEmpty() || modId.equals(mod.getModId())) {
            return mod;
        }
        return this.registry.find(modId).orElse(null);
    }
}
