package dev.alloy.remap.mapping;

import dev.alloy.remap.SrgNameTable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The mappings jar shipped with Lunar Client ({@code lunar-platform-mappings-v1_8.jar}), read from
 * the user's machine so Alloy embeds no Lunar or Mojang data. It provides:
 * <ul>
 *   <li>{@code lunar/searge2lunar_<version>.kin}: SRG names to game names, for mods;</li>
 *   <li>{@code lunar/lunar_named_b5_<version>.kin}: obfuscated names to game names, for Forge and
 *       Minecraft itself.</li>
 * </ul>
 * Tables are read on first request and kept; methods are {@code synchronized}.
 */
public final class LunarMappingsJar {

    private static final String SRG_ENTRY = "lunar/searge2lunar_%s.kin";
    private static final String NOTCH_ENTRY = "lunar/lunar_named_b5_%s.kin";

    private final Path jar;
    private final String minecraftVersion;

    /** {@code null} until requested. */
    private SrgNameTable srgNames;

    /** {@code null} until requested. */
    private NotchMappings notchMappings;

    /**
     * Points to the mappings jar without reading it.
     *
     * @param minecraftVersion Minecraft version, e.g. {@code "1.8.9"}
     */
    public LunarMappingsJar(Path jar, String minecraftVersion) {
        this.jar = Objects.requireNonNull(jar, "jar");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    }

    /**
     * Returns the "SRG name to game name" table, read on first call.
     *
     * @throws IOException if the jar is unreadable or the expected file is missing or invalid
     */
    public synchronized SrgNameTable srgNames() throws IOException {
        if (this.srgNames == null) {
            SrgTableBuilder builder = new SrgTableBuilder();
            this.read(String.format(LunarMappingsJar.SRG_ENTRY, this.minecraftVersion), builder);
            this.srgNames = builder.build();
        }
        return this.srgNames;
    }

    /**
     * Returns the "obfuscated name to game name" table, read on first call.
     *
     * @throws IOException if the jar is unreadable or the expected file is missing or invalid
     */
    public synchronized NotchMappings notchMappings() throws IOException {
        if (this.notchMappings == null) {
            NotchMappingsBuilder builder = new NotchMappingsBuilder();
            this.read(String.format(LunarMappingsJar.NOTCH_ENTRY, this.minecraftVersion), builder);
            this.notchMappings = builder.build();
        }
        return this.notchMappings;
    }

    private void read(String entryName, KinRecordHandler handler) throws IOException {
        try (ZipFile zip = new ZipFile(this.jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new MappingFormatException("Mapping file '" + entryName + "' not found in " + this.jar);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                new KinReader(handler).read(input);
            } catch (MappingFormatException e) {
                throw new MappingFormatException("Cannot use '" + entryName + "' of " + this.jar + ": " + e.getMessage(), e);
            }
        }
    }
}
