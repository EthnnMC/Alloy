package dev.alloy.remap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable table of the members Forge adds to Minecraft and Alloy replaces in mod bytecode. It is
 * read from a TSV text file (tab-separated columns), supplied by Alloy's Forge runtime together
 * with the replacement methods:
 * <pre>
 * # kind  owner                 name     descriptor                    action        target1   target2
 * method  net/.../NetworkManager  channel  ()Lio/netty/channel/Channel;  getfield      channel   Lio/netty/channel/Channel;
 * method  net/.../GuiScreen       drawHoveringText  (Ljava/util/List;II...)V  invokestatic  dev/.../ForgeMemberShims  drawHoveringText
 * field   net/.../MovingObjectPosition  subHit  I                      invokestatic  dev/.../ForgeMemberShims  subHit
 * </pre>
 * (In the real file class names are written in full, without {@code ...}.)
 * <ul>
 *   <li>{@code kind}: {@code method} or {@code field};</li>
 *   <li>{@code owner}, {@code name}, {@code descriptor}: the member added by Forge, in game names;</li>
 *   <li>{@code getfield <field> <type>}: see {@link ReadFieldAction};</li>
 *   <li>{@code invokestatic <class> <method>}: see {@link CallStaticAction}.</li>
 * </ul>
 * Each row has exactly seven columns. Blank lines and lines starting with {@code #} are ignored.
 */
public final class MemberShimTable {

    private static final String COMMENT_PREFIX = "#";
    private static final String COLUMN_SEPARATOR = "\t";

    private static final int KIND_COLUMN = 0;
    private static final int OWNER_COLUMN = 1;
    private static final int NAME_COLUMN = 2;
    private static final int DESCRIPTOR_COLUMN = 3;
    private static final int ACTION_COLUMN = 4;
    private static final int FIRST_TARGET_COLUMN = 5;
    private static final int SECOND_TARGET_COLUMN = 6;
    private static final int COLUMN_COUNT = 7;

    private static final String NO_ARGUMENTS = "()";

    private static final MemberShimTable EMPTY = new MemberShimTable(List.of());

    private final List<MemberShim> shims;

    /** The same rows, indexed by member for direct lookup from an instruction. */
    private final Map<MemberId, List<MemberShim>> byMember;

    /**
     * Lookup key: what an instruction says about the member, except the class (which needs an
     * inheritance check, done by the rewriting pass).
     */
    private record MemberId(MemberKind kind, String name, String descriptor) {
    }

    private MemberShimTable(List<MemberShim> shims) {
        this.shims = List.copyOf(shims);
        Map<MemberId, List<MemberShim>> index = new HashMap<>();
        for (MemberShim shim : this.shims) {
            MemberId id = new MemberId(shim.kind(), shim.name(), shim.descriptor());
            index.computeIfAbsent(id, key -> new ArrayList<>()).add(shim);
        }
        // Lists are frozen: candidates() can return them without exposing private state.
        index.replaceAll((id, sameMember) -> List.copyOf(sameMember));
        this.byMember = index;
    }

    /**
     * Returns the empty table: no member is replaced.
     */
    public static MemberShimTable empty() {
        return MemberShimTable.EMPTY;
    }

    /**
     * Builds a table from already built rows.
     *
     * @throws IllegalArgumentException if two rows describe the same member of the same class
     */
    public static MemberShimTable of(List<MemberShim> shims) {
        MemberShimTable table = new MemberShimTable(shims);
        for (List<MemberShim> sameMember : table.byMember.values()) {
            long distinctOwners = sameMember.stream().map(MemberShim::owner).distinct().count();
            if (distinctOwners != sameMember.size()) {
                MemberShim first = sameMember.get(0);
                throw new IllegalArgumentException("Member shim declared twice: " + first.owner() + "."
                        + first.name() + " " + first.descriptor());
            }
        }
        return table;
    }

    /**
     * Parses a table in the TSV format described in the class Javadoc.
     *
     * @throws IllegalArgumentException if a line is malformed; the message gives its number
     */
    public static MemberShimTable parse(String text) {
        List<MemberShim> shims = new ArrayList<>();
        int lineNumber = 0;
        for (String line : text.lines().toList()) {
            lineNumber++;
            if (line.isBlank() || line.stripLeading().startsWith(MemberShimTable.COMMENT_PREFIX)) {
                continue;
            }
            try {
                shims.add(MemberShimTable.parseLine(line));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid member shim at line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        return MemberShimTable.of(shims);
    }

    /**
     * Reads a UTF-8 TSV table from a stream (e.g. a jar resource); the stream is not closed.
     *
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if a line is malformed
     */
    public static MemberShimTable read(InputStream input) throws IOException {
        return MemberShimTable.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Reads a UTF-8 TSV table from a file.
     *
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if a line is malformed
     */
    public static MemberShimTable read(Path file) throws IOException {
        return MemberShimTable.parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static MemberShim parseLine(String line) {
        String[] columns = line.split(MemberShimTable.COLUMN_SEPARATOR, -1);
        if (columns.length != MemberShimTable.COLUMN_COUNT) {
            throw new IllegalArgumentException(
                    "expected " + MemberShimTable.COLUMN_COUNT + " tab-separated columns, found " + columns.length);
        }
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i].strip();
        }
        MemberKind kind = switch (columns[MemberShimTable.KIND_COLUMN]) {
            case "method" -> MemberKind.METHOD;
            case "field" -> MemberKind.FIELD;
            default -> throw new IllegalArgumentException(
                    "unknown kind '" + columns[MemberShimTable.KIND_COLUMN] + "' (expected method or field)");
        };
        String descriptor = columns[MemberShimTable.DESCRIPTOR_COLUMN];
        String firstTarget = columns[MemberShimTable.FIRST_TARGET_COLUMN];
        String secondTarget = columns[MemberShimTable.SECOND_TARGET_COLUMN];
        if (firstTarget.isEmpty() || secondTarget.isEmpty()) {
            throw new IllegalArgumentException("the action needs two targets");
        }
        ShimAction action = switch (columns[MemberShimTable.ACTION_COLUMN]) {
            case "getfield" -> MemberShimTable.readField(kind, descriptor, firstTarget, secondTarget);
            case "invokestatic" -> new CallStaticAction(firstTarget, secondTarget);
            default -> throw new IllegalArgumentException("unknown action '" + columns[MemberShimTable.ACTION_COLUMN]
                    + "' (expected getfield or invokestatic)");
        };
        return new MemberShim(kind, columns[MemberShimTable.OWNER_COLUMN], columns[MemberShimTable.NAME_COLUMN],
                descriptor, action);
    }

    private static ShimAction readField(MemberKind kind, String descriptor, String fieldName, String fieldType) {
        if (kind != MemberKind.METHOD || !descriptor.startsWith(MemberShimTable.NO_ARGUMENTS)) {
            throw new IllegalArgumentException("getfield can only replace a method without arguments");
        }
        return new ReadFieldAction(fieldName, fieldType);
    }

    /**
     * Returns the rows that may concern an access to a member with this name and descriptor, for
     * any class. The caller must check that the class named by the instruction is the row's class
     * or a subclass of it.
     *
     * @return the candidate rows, often none
     */
    public List<MemberShim> candidates(MemberKind kind, String name, String descriptor) {
        return this.byMember.getOrDefault(new MemberId(kind, name, descriptor), List.of());
    }

    /**
     * Returns all rows, as an unmodifiable list in file order.
     */
    public List<MemberShim> shims() {
        return this.shims;
    }

    /**
     * Tells whether the table replaces nothing (no rows).
     */
    public boolean isEmpty() {
        return this.shims.isEmpty();
    }
}
