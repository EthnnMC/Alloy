package dev.alloy.remap.transform;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.tree.ClassNode;

/**
 * Collects the {@code @Mod} classes (the mods' main classes) without modifying anything, so the
 * jar need not be opened a second time.
 */
public final class ModClassCollector implements ClassPass {

    private final List<String> modClassNames = new ArrayList<>();

    public ModClassCollector() {
        // Nothing to prepare: the list fills up as classes are examined.
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        if (Annotations.isPresent(classNode.visibleAnnotations, ForgeNames.MOD_ANNOTATION)) {
            this.modClassNames.add(classNode.name.replace('/', '.'));
        }
        return Verdict.KEEP;
    }

    /**
     * Returns the {@code @Mod} classes found so far.
     *
     * @return their dotted names ({@code com.example.ExampleMod}), in visiting order
     */
    public List<String> modClassNames() {
        return List.copyOf(this.modClassNames);
    }
}
