package dev.alloy.forge.loader;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.PreparedMod;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ContainerType;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.objectweb.asm.Type;

/**
 * Reads the mod jars to produce the mod containers and the annotation table. Like Forge, it
 * reads {@code .class} files without loading them (loading a mod class this early would pull in
 * Minecraft classes), using Forge's own parsers to get the same data, notably the ASM table.
 */
public final class ModScanner {

    private static final String CLASS_SUFFIX = ".class";
    private static final String METADATA_ENTRY = "mcmod.info";
    private static final String MOD_ANNOTATION = Type.getDescriptor(Mod.class);
    private static final String DEFAULT_VERSION = "1.0";

    private final BridgeLogger logger;

    /**
     * Creates the scanner.
     *
     * @param logger Alloy log
     */
    public ModScanner(BridgeLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Reads all the mods prepared by the agent.
     *
     * @param mods  the rewritten jars and their {@code @Mod} classes
     * @param table the annotation table to fill (the one mods will receive)
     * @return the containers, in jar order
     */
    public List<AlloyModContainer> scan(List<PreparedMod> mods, ASMDataTable table) {
        List<AlloyModContainer> containers = new ArrayList<>();
        for (PreparedMod mod : mods) {
            try {
                containers.addAll(this.scanJar(mod, table));
            } catch (IOException | RuntimeException e) {
                this.logger.error("Cannot read mod " + mod.originalJar().getFileName() + ": it is skipped", e);
            }
        }
        return containers;
    }

    private List<AlloyModContainer> scanJar(PreparedMod mod, ASMDataTable table) throws IOException {
        File source = mod.loadableJar().toFile();
        ModCandidate candidate = new ModCandidate(source, source, ContainerType.JAR);
        List<AlloyModContainer> containers = new ArrayList<>();
        TreeSet<String> packages = new TreeSet<>();
        List<ASMModParser> modClasses = new ArrayList<>();

        try (JarFile jar = new JarFile(source)) {
            MetadataCollection metadata = ModScanner.readMetadata(jar, source.getName());
            for (JarEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(ModScanner.CLASS_SUFFIX)) {
                    continue;
                }
                ASMModParser parser = this.parse(jar, entry);
                if (parser == null) {
                    continue;
                }
                parser.sendToTable(table, candidate);
                String className = parser.getASMType().getClassName();
                packages.add(ModRegistry.packageOf(className));
                if (mod.modClassNames().contains(className)) {
                    modClasses.add(parser);
                }
            }
            // Packages are only known after all classes are seen: containers are created last.
            for (ASMModParser parser : modClasses) {
                containers.add(ModScanner.newContainer(parser, metadata, source, List.copyOf(packages)));
            }
        }
        containers.forEach(table::addContainer);
        return containers;
    }

    /** Parses a class; returns {@code null} (and logs it) if unreadable, as Forge does. */
    private ASMModParser parse(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return new ASMModParser(input);
        } catch (LoaderException e) {
            this.logger.warn("Unreadable class " + entry.getName() + " in " + jar.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static MetadataCollection readMetadata(JarFile jar, String sourceName) throws IOException {
        JarEntry entry = jar.getJarEntry(ModScanner.METADATA_ENTRY);
        if (entry == null) {
            return MetadataCollection.from(null, sourceName);
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return MetadataCollection.from(input, sourceName);
        }
    }

    /** Builds a container from the {@code @Mod} annotation read in the class. */
    private static AlloyModContainer newContainer(
            ASMModParser parser, MetadataCollection metadataCollection, File source, List<String> packages) {
        Map<String, Object> descriptor = ModScanner.modAnnotationValues(parser);
        String modId = String.valueOf(descriptor.get("modid"));
        // Returns the mcmod.info metadata, or builds some from the annotation.
        ModMetadata metadata = metadataCollection.getMetadataForId(modId, descriptor);
        metadata.modId = modId;
        if (metadata.name == null || metadata.name.isEmpty()) {
            metadata.name = modId;
        }
        Object annotationVersion = descriptor.get("version");
        if (annotationVersion instanceof String version && !version.isEmpty()) {
            metadata.version = version;
        } else if (metadata.version == null || metadata.version.isEmpty()) {
            metadata.version = ModScanner.DEFAULT_VERSION;
        }
        return new AlloyModContainer(metadata, parser.getASMType().getClassName(), source, descriptor, packages);
    }

    private static Map<String, Object> modAnnotationValues(ASMModParser parser) {
        for (ModAnnotation annotation : parser.getAnnotations()) {
            if (ModScanner.MOD_ANNOTATION.equals(annotation.getASMType().getDescriptor())) {
                return annotation.getValues();
            }
        }
        throw new IllegalStateException("No @Mod annotation on " + parser.getASMType().getClassName());
    }
}
