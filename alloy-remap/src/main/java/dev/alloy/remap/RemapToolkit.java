package dev.alloy.remap;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHeaderSource;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.hierarchy.LayeredHeaderSource;
import dev.alloy.remap.io.Sha256;
import dev.alloy.remap.jar.LoaderRequirements;
import dev.alloy.remap.jar.OverlayFilter;
import dev.alloy.remap.jar.SourceJar;
import dev.alloy.remap.jar.WorkingJar;
import dev.alloy.remap.mapping.LunarMappingsJar;
import dev.alloy.remap.mapping.NotchRemapper;
import dev.alloy.remap.mapping.SrgRemapper;
import dev.alloy.remap.mixin.MixinIndex;
import dev.alloy.remap.transform.AccessWideningPass;
import dev.alloy.remap.transform.ClassPass;
import dev.alloy.remap.transform.ClassPipeline;
import dev.alloy.remap.transform.EventClassPass;
import dev.alloy.remap.transform.MemberShimPass;
import dev.alloy.remap.transform.MissingMemberPass;
import dev.alloy.remap.transform.ModClassCollector;
import dev.alloy.remap.transform.SidePass;
import dev.alloy.remap.transform.SrgStringPass;
import dev.alloy.remap.transform.SubscriberPass;
import dev.alloy.remap.transform.UnfiredEventPass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Facade of the module: prepares the jars Alloy loads or compiles against.
 *
 * <p>Three namespaces are involved: <b>obfuscated</b> ("notch", Mojang's Minecraft jar and Forge's
 * universal jar, {@code ave.a()}), <b>SRG</b> (compiled Forge mods,
 * {@code Minecraft.func_71410_x()}) and <b>game names</b> (what Lunar really runs,
 * {@code Minecraft.getMinecraft()}). Everything is brought to game names once, when filling the
 * cache; nothing is renamed while the game runs.</p>
 *
 * <p>Methods are {@code synchronized} (one job at a time); tables are read on first request and kept.</p>
 */
public final class RemapToolkit {

    /**
     * Version of the rewriting logic. Increase it whenever a change in this module alters the
     * produced jars: it is part of {@link #fingerprint()}, so old cached jars are rebuilt.
     */
    public static final int TRANSFORM_VERSION = 4;

    /** Hexadecimal digits kept for the fingerprint: enough for a cache key. */
    private static final int FINGERPRINT_LENGTH = 32;

    private final Path lunarMappingsJar;
    private final String minecraftVersion;
    private final Path vanillaJar;
    private final LunarMappingsJar mappings;

    /** Minecraft class headers, obfuscated names; {@code null} until read. */
    private HeaderTable vanillaNotchHeaders;

    /** The same headers translated to game names; {@code null} until computed. */
    private HeaderTable vanillaNamedHeaders;

    /** Forge class headers, game names; {@code null} until Forge is known. */
    private HeaderTable forgeHeaders;

    private List<String> lastForgeWarnings = List.of();

    /** Fingerprint of the input data; {@code null} until computed. */
    private String fingerprint;

    private RemapToolkit(Path lunarMappingsJar, String minecraftVersion, Path vanillaJar) {
        this.lunarMappingsJar = Objects.requireNonNull(lunarMappingsJar, "lunarMappingsJar");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.vanillaJar = Objects.requireNonNull(vanillaJar, "vanillaJar");
        this.mappings = new LunarMappingsJar(lunarMappingsJar, minecraftVersion);
    }

    /**
     * Creates the toolkit for a Lunar Client installation. Nothing is read here: files are opened
     * on first need.
     *
     * @param lunarMappingsJar Lunar's mappings jar ({@code lunar-platform-mappings-v1_8.jar})
     * @param minecraftVersion Minecraft version, e.g. {@code "1.8.9"}
     * @param vanillaJar       Mojang's Minecraft client jar (obfuscated names)
     */
    public static RemapToolkit forLunar(Path lunarMappingsJar, String minecraftVersion, Path vanillaJar) {
        return new RemapToolkit(lunarMappingsJar, minecraftVersion, vanillaJar);
    }

    /**
     * Prepares Forge: translates the universal jar (obfuscated names) to game names and applies the
     * transformations real Forge does at load time (client side, events, listeners). The Forge
     * classes found are remembered, as {@link #prepareMod} needs them to recognize mod events.
     *
     * @param universalJar    Forge's universal jar, as downloaded
     * @param outputJar       the jar to produce (replaced if it exists)
     * @param overlaidClasses internal names of the classes Alloy replaces with its own; removed from
     *                        the result together with their inner classes
     * @return {@code outputJar}
     * @throws IOException if an input file is unreadable or writing fails
     */
    public synchronized Path prepareForge(Path universalJar, Path outputJar, Set<String> overlaidClasses)
            throws IOException {
        List<String> warnings = new ArrayList<>();
        SourceJar source = SourceJar.read(universalJar, warnings::add);
        // Forge's jar first, then Minecraft: together they make up the whole obfuscated world.
        ClassHierarchy notchWorld = new ClassHierarchy(
                new LayeredHeaderSource(List.of(source.headers(), this.vanillaNotchHeaders())));
        WorkingJar work = source.remap(new NotchRemapper(this.mappings.notchMappings(), notchWorld), warnings::add);

        this.forgeHeaders = work.headers();
        work.removeClasses(new OverlayFilter(overlaidClasses));
        work.apply(new ClassPipeline(List.of(
                new SidePass(warnings::add),
                new EventClassPass(new ClassHierarchy(this.forgeHeaders), warnings::add),
                new SubscriberPass(warnings::add))));
        work.writeTo(outputJar);
        this.lastForgeWarnings = List.copyOf(warnings);
        return outputJar;
    }

    /**
     * Declares an <b>already prepared</b> Forge jar (e.g. from the cache), so {@link #prepareMod}
     * knows the Forge classes without redoing {@link #prepareForge}.
     *
     * @param preparedForgeJar a jar produced by {@link #prepareForge}
     * @throws IOException if the jar is unreadable
     */
    public synchronized void useForgeJar(Path preparedForgeJar) throws IOException {
        this.forgeHeaders = HeaderTable.ofJar(preparedForgeJar);
    }

    /**
     * Returns the anomalies of the last {@link #prepareForge} call; empty for the official Forge jar.
     */
    public synchronized List<String> lastForgeWarnings() {
        return this.lastForgeWarnings;
    }

    /**
     * Prepares a Forge mod: translates its SRG names to game names (also in reflection strings),
     * applies the transformations Forge does at load time, translates what its mixins keep as text
     * (their configuration files and targets are listed in the jar entry {@link MixinIndex#JAR_ENTRY}),
     * and replaces accesses to members Forge adds to Minecraft.
     *
     * <p>Recognizing mod events requires knowing the Forge classes: call {@link #prepareForge} or
     * {@link #useForgeJar} first, otherwise a warning is added for each doubtful mod class.</p>
     *
     * @param modJar    the mod jar as dropped by the user (never modified)
     * @param outputJar the jar to produce (replaced if it exists)
     * @param shims     the table of Forge-added members to replace
     * @return the produced jar, its {@code @Mod} classes and the warnings
     * @throws IOException if an input file is unreadable or writing fails
     */
    public PreparedModJar prepareMod(Path modJar, Path outputJar, MemberShimTable shims) throws IOException {
        return this.prepareMod(modJar, outputJar, shims, Set.of());
    }

    /**
     * Same as {@link #prepareMod(Path, Path, MemberShimTable)}, and also warns about each Forge
     * event the mod listens to that Alloy never publishes.
     *
     * @param firedEvents internal names of the Forge event classes Alloy publishes; empty to skip the check
     */
    public synchronized PreparedModJar prepareMod(
            Path modJar, Path outputJar, MemberShimTable shims, Set<String> firedEvents) throws IOException {
        List<String> warnings = new ArrayList<>();
        SrgNameTable names = this.mappings.srgNames();
        SourceJar source = SourceJar.read(modJar, warnings::add);
        LoaderRequirements.report(source.resources(), warnings::add);
        WorkingJar work = source.remap(new SrgRemapper(names), warnings::add);

        HeaderTable modHeaders = work.headers();
        HeaderTable forge = this.forgeHeaders == null ? HeaderTable.empty() : this.forgeHeaders;
        ClassHierarchy modAndForge = new ClassHierarchy(new LayeredHeaderSource(List.of(modHeaders, forge)));
        // Minecraft classes are only read if a pass actually asks about them.
        ClassHeaderSource minecraft = this::namedVanillaHeader;
        ClassHierarchy everything = new ClassHierarchy(new LayeredHeaderSource(List.of(modHeaders, forge, minecraft)));

        ModClassCollector modClasses = new ModClassCollector();
        MixinIndex mixins = new MixinIndex(names);
        List<ClassPass> passes = new ArrayList<>(List.of(
                new SrgStringPass(names),
                new SidePass(warnings::add),
                mixins,
                new EventClassPass(modAndForge, warnings::add),
                new SubscriberPass(warnings::add),
                new MemberShimPass(shims, everything),
                new MissingMemberPass(everything, warnings::add),
                modClasses));
        if (!firedEvents.isEmpty()) {
            passes.add(new UnfiredEventPass(firedEvents, modAndForge, warnings::add));
        }
        ClassPipeline pipeline = new ClassPipeline(passes);
        try {
            work.apply(pipeline);
        } catch (UncheckedIOException e) {
            // Thrown by namedVanillaHeader: give the caller back the original checked exception.
            throw e.getCause();
        }
        for (Map.Entry<String, byte[]> resource : List.copyOf(work.resources().entrySet())) {
            if (MixinIndex.isReferenceMap(resource.getKey())) {
                work.putResource(resource.getKey(), mixins.translateNames(resource.getValue()));
            }
        }
        MixinIndex.Contents mixinContents =
                new MixinIndex.Contents(MixinIndex.configNames(source.resources()), mixins.targets());
        if (!mixinContents.isEmpty()) {
            work.putResource(MixinIndex.JAR_ENTRY, mixinContents.format().getBytes(StandardCharsets.UTF_8));
        }
        work.writeTo(outputJar);
        return new PreparedModJar(outputJar, modClasses.modClassNames(), warnings);
    }

    /**
     * Builds the Minecraft jar to compile against: game names, everything public and non-final as in
     * the game run by Lunar. Used for compilation and tests only, never loaded in the game.
     *
     * @param outputJar the jar to produce (replaced if it exists)
     * @return {@code outputJar}
     * @throws IOException if an input file is unreadable or writing fails
     */
    public synchronized Path prepareVanilla(Path outputJar) throws IOException {
        List<String> warnings = new ArrayList<>();
        SourceJar source = SourceJar.read(this.vanillaJar, warnings::add);
        ClassHierarchy notchWorld = new ClassHierarchy(source.headers());
        WorkingJar work = source.remap(new NotchRemapper(this.mappings.notchMappings(), notchWorld), warnings::add);
        if (!warnings.isEmpty()) {
            // Mojang's jar is a known input: any anomaly means a damaged file.
            throw new IOException("The Minecraft jar " + this.vanillaJar + " is damaged: " + warnings);
        }
        work.apply(new AccessWideningPass());
        work.writeTo(outputJar);
        return outputJar;
    }

    /**
     * Returns the "SRG name to game name" table, read from Lunar's jar on first call.
     *
     * @throws UncheckedIOException if the mappings jar is unreadable or invalid
     */
    public SrgNameTable srgNames() {
        try {
            return this.mappings.srgNames();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the SRG names from " + this.lunarMappingsJar, e);
        }
    }

    /**
     * Returns a stable fingerprint of everything the prepared output depends on, except the
     * processed jar itself: mappings jar content, Minecraft jar content, Minecraft version and
     * {@link #TRANSFORM_VERSION}. It must be part of every cache key, so that when Lunar updates its
     * tables the jars are rebuilt.
     *
     * @return 32 hexadecimal digits
     * @throws UncheckedIOException if one of the two jars is unreadable
     */
    public synchronized String fingerprint() {
        if (this.fingerprint == null) {
            try {
                String inputs = "alloy-remap transform " + RemapToolkit.TRANSFORM_VERSION
                        + "\nminecraft " + this.minecraftVersion
                        + "\nmappings " + Sha256.ofFile(this.lunarMappingsJar)
                        + "\nvanilla " + Sha256.ofFile(this.vanillaJar);
                this.fingerprint = Sha256.ofText(inputs).substring(0, RemapToolkit.FINGERPRINT_LENGTH);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot fingerprint the mapping inputs", e);
            }
        }
        return this.fingerprint;
    }

    private HeaderTable vanillaNotchHeaders() throws IOException {
        if (this.vanillaNotchHeaders == null) {
            this.vanillaNotchHeaders = HeaderTable.ofJar(this.vanillaJar);
        }
        return this.vanillaNotchHeaders;
    }

    private HeaderTable vanillaNamedHeaders() throws IOException {
        if (this.vanillaNamedHeaders == null) {
            HeaderTable notchHeaders = this.vanillaNotchHeaders();
            NotchRemapper remapper = new NotchRemapper(this.mappings.notchMappings(), new ClassHierarchy(notchHeaders));
            this.vanillaNamedHeaders = notchHeaders.mapped(remapper);
        }
        return this.vanillaNamedHeaders;
    }

    /**
     * Looks up a Minecraft class header by game name. A lazy {@link ClassHeaderSource}: the
     * Minecraft jar is read on the first call.
     *
     * @throws UncheckedIOException if the Minecraft jar or the tables are unreadable (a functional
     *                              interface method cannot throw a checked exception)
     */
    private Optional<ClassHeader> namedVanillaHeader(String internalName) {
        try {
            return this.vanillaNamedHeaders().find(internalName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
