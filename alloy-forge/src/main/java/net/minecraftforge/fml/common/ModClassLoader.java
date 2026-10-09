package net.minecraftforge.fml.common;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.common.asm.transformers.ModAPITransformer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

/**
 * Replaces Forge's class of the same name (see {@link Loader}). Here it is a thin wrapper of
 * Alloy's mod class loader; the other methods exist only because Forge code references them, and
 * do nothing since all jars are already in place.
 */
public class ModClassLoader extends URLClassLoader {

    /**
     * Wraps the mod class loader.
     *
     * @param parent Alloy's mod class loader
     */
    public ModClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    /** No-op: the mod jars are already in Alloy's loader. */
    public void addFile(File modFile) {
    }

    /** Returns an empty array: Alloy does not look for mods on the class path. */
    public File[] getParentSources() {
        return new File[0];
    }

    /** Returns an empty list. */
    public List<String> getDefaultLibraries() {
        return List.of();
    }

    /** Always {@code false}. */
    public boolean isDefaultLibrary(File file) {
        return false;
    }

    /** No-op: there is no negative class cache to clear. */
    public void clearNegativeCacheFor(Set<String> classList) {
    }

    /** Not supported: Alloy ignores {@code @Optional} annotations; always throws. */
    public ModAPITransformer addModAPITransformer(ASMDataTable dataTable) {
        throw new UnsupportedOperationException("Alloy: ModClassLoader.addModAPITransformer is not supported");
    }

    /** Always {@code false}. */
    public boolean containsSource(File source) {
        return false;
    }
}
