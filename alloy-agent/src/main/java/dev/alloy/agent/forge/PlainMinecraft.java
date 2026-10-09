package dev.alloy.agent.forge;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.remap.RemapToolkit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minecraft's classes with the game's names but without what Lunar and OptiFine add to them:
 * Mojang's jar, renamed and kept in the cache. It answers when Lunar's own class cache does not
 * have a class (see {@code BakedClasses}); what a class inherits and what vanilla declares is
 * right, only Lunar's additions are missing. Built the first time a class is asked for.
 */
public final class PlainMinecraft {

    private static final String CACHE_GROUP = "minecraft";
    private static final String MINECRAFT_PACKAGE = "net/minecraft/";
    private static final String CLASS_SUFFIX = ".class";

    private final RemapToolkit toolkit;
    private final JarCache cache;
    private final Path vanillaJar;
    private final BridgeLogger logger;

    /** The renamed jar; {@code null} until first needed, or if it could not be built. */
    private ZipFile jar;
    private boolean attempted;

    /**
     * @param toolkit    the renaming toolkit of this launch
     * @param cache      Alloy's jar cache
     * @param vanillaJar Mojang's Minecraft client jar
     * @param logger     Alloy's log
     */
    public PlainMinecraft(RemapToolkit toolkit, JarCache cache, Path vanillaJar, BridgeLogger logger) {
        this.toolkit = Objects.requireNonNull(toolkit, "toolkit");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.vanillaJar = Objects.requireNonNull(vanillaJar, "vanillaJar");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Reads a Minecraft class.
     *
     * @param internalName internal class name, in game names
     * @return the class file, or {@code null} if it is not a Minecraft class
     */
    public synchronized byte[] read(String internalName) {
        if (!internalName.startsWith(PlainMinecraft.MINECRAFT_PACKAGE)) {
            return null;
        }
        ZipFile renamed = this.renamedJar();
        if (renamed == null) {
            return null;
        }
        ZipEntry entry = renamed.getEntry(internalName + PlainMinecraft.CLASS_SUFFIX);
        if (entry == null) {
            return null;
        }
        try (InputStream input = renamed.getInputStream(entry)) {
            return input.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private ZipFile renamedJar() {
        if (!this.attempted) {
            this.attempted = true;
            try {
                Path renamed = this.cache.locationFor(PlainMinecraft.CACHE_GROUP, this.vanillaJar);
                if (!Files.isRegularFile(renamed)) {
                    this.logger.info("Renaming Minecraft for the mixins (once)...");
                    Files.createDirectories(renamed.getParent());
                    this.toolkit.prepareVanilla(renamed);
                }
                this.jar = new ZipFile(renamed.toFile());
            } catch (IOException | RuntimeException e) {
                this.logger.error("Cannot rename Minecraft for the mixins", e);
            }
        }
        return this.jar;
    }
}
