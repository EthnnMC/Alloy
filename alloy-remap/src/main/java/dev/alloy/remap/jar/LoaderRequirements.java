package dev.alloy.remap.jar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Reports what a mod jar asks of the launcher that Alloy does not provide: a tweaker or a coremod,
 * both declared in the jar manifest. Such a mod is still loaded, without its class patches. Mixins
 * are handled one by one elsewhere, so Mixin's own tweaker is not reported.
 */
public final class LoaderRequirements {

    private static final String TWEAK_CLASS = "TweakClass";
    private static final String CORE_PLUGIN = "FMLCorePlugin";
    private static final String MIXIN_TWEAKER = "org.spongepowered.asm.launch.MixinTweaker";

    private LoaderRequirements() {
    }

    /**
     * Examines the manifest of a mod jar.
     *
     * @param resources the non-class entries of the jar, as returned by {@link SourceJar#resources()}
     * @param warnings  receives one message per unsupported requirement
     * @throws IOException if the manifest is unreadable
     */
    public static void report(Map<String, byte[]> resources, Consumer<String> warnings) throws IOException {
        byte[] manifestBytes = resources.get(JarFile.MANIFEST_NAME);
        if (manifestBytes == null) {
            return;
        }
        Attributes attributes = new Manifest(new ByteArrayInputStream(manifestBytes)).getMainAttributes();
        String ending = ", which Alloy does not support yet: the mod is loaded without the changes it makes"
                + " to Minecraft classes and may not work";
        String corePlugin = attributes.getValue(LoaderRequirements.CORE_PLUGIN);
        if (corePlugin != null) {
            warnings.accept("Is a coremod (" + corePlugin + ")" + ending);
        }
        String tweakClass = attributes.getValue(LoaderRequirements.TWEAK_CLASS);
        if (tweakClass != null && !tweakClass.equals(LoaderRequirements.MIXIN_TWEAKER)) {
            warnings.accept("Needs the tweaker " + tweakClass + ending);
        }
    }
}
