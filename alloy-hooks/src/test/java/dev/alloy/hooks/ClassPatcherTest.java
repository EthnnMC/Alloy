package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.bridge.GameHooks;
import dev.alloy.bridge.GuiOpenDecision;
import dev.alloy.hooks.inject.Anchor;
import dev.alloy.hooks.inject.AnchorCall;
import dev.alloy.hooks.inject.AnchorPosition;
import dev.alloy.hooks.inject.HeadCall;
import dev.alloy.hooks.inject.HeadCancel;
import dev.alloy.hooks.inject.HeadCancelThenReplaceParameter;
import dev.alloy.hooks.inject.HeadReplaceParameter;
import dev.alloy.hooks.inject.HookArgument;
import dev.alloy.hooks.inject.HookCall;
import dev.alloy.hooks.inject.InvokeAnchor;
import dev.alloy.hooks.inject.Occurrence;
import dev.alloy.hooks.inject.RedirectInvoke;
import dev.alloy.hooks.inject.ReturnCall;
import dev.alloy.hooks.inject.ReturnFilter;
import dev.alloy.hooks.testing.ByteClassLoader;
import dev.alloy.hooks.testing.BytecodeChecks;
import dev.alloy.hooks.testing.ClassHierarchy;
import dev.alloy.hooks.testing.FakeGame;
import dev.alloy.hooks.testing.GameTrace;
import dev.alloy.hooks.testing.MethodDump;
import dev.alloy.hooks.testing.RecordingSink;
import dev.alloy.hooks.testing.SyntheticClass;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Tests each injection strategy on the fake game of {@link FakeGame}: the patched bytecode is
 * defined in a test loader then <b>executed</b>, for the two class file versions found in the
 * real game (50 = Java 6, 61 = Java 17).
 */
class ClassPatcherTest {

    private static final String GAME = FakeGame.NAME;
    private static final String TRACE = SyntheticClass.internalNameOf(GameTrace.class);
    private static final String VOID = "()V";
    private static final String OBJECT_TO_VOID = "(Ljava/lang/Object;)V";
    private static final String OBJECT_TO_BOOLEAN = "(Ljava/lang/Object;)Z";

    private final ClassPatcher patcher = new ClassPatcher();
    private RecordingSink sink;

    @BeforeEach
    void installRecordingSink() {
        GameTrace.reset();
        this.sink = new RecordingSink();
        GameHooks.install(this.sink.asSink());
    }

    // ------------------------------------------------------------------ head and return calls

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCallRunsBeforeTheBodyAndReturnCallAfterIt(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "tick");

        assertEquals(List.of("onClientTickStart()", "tick", "onClientTickEnd()"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void returnCallOnEveryReturnCoversAnEarlyExit(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "update", true);

        assertEquals(List.of("onPlayerTickStart(Game)", "early", "onPlayerTickEnd(Game)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void returnCallOnLastReturnIgnoresAnEarlyExit(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "init", true);

        assertEquals(List.of("onGuiInitPre(Game)", "early"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void returnCallOnLastReturnFiresOnNormalCompletion(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "init", false);

        assertEquals(List.of("onGuiInitPre(Game)", "late", "onGuiInitPost(Game)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void returnCallIsAllowedInAConstructor(int classVersion) throws Exception {
        Class<?> gameClass = this.definePatchedGame(classVersion);

        gameClass.getConstructor(Object.class).newInstance("world");

        assertEquals(List.of("constructed", "onWorldLoad(Game)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCallPassesAParameterOfAStaticMethod(int classVersion) throws Exception {
        Class<?> gameClass = this.definePatchedGame(classVersion);
        List<String> received = new ArrayList<>();
        GameHooks.setGameStartListener(arguments -> received.addAll(Arrays.asList(arguments)));

        gameClass.getMethod("main", String[].class).invoke(null, (Object) new String[] {"--version", "1.8.9"});

        assertEquals(List.of("--version", "1.8.9"), received);
    }

    // ------------------------------------------------------------------ head cancellation

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelSkipsTheBodyWhenTheHookAnswersTrue(int classVersion) throws Exception {
        this.sink.answer("onChatReceived", true);
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "chat", "packet");

        assertEquals(List.of("onChatReceived(packet)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelRunsTheBodyWhenTheHookAnswersFalse(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "chat", "packet");

        assertEquals(List.of("onChatReceived(packet)", "chat"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelReturnsFalseFromABooleanMethod(int classVersion) throws Exception {
        this.sink.answer("onEntityJoinWorld", true);
        Object game = this.newPatchedGame(classVersion);

        Object spawned = ClassPatcherTest.call(game, "spawn", "zombie");

        assertEquals(false, spawned);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelKeepsTheReturnValueWhenNotCancelled(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        Object spawned = ClassPatcherTest.call(game, "spawn", "zombie");

        assertEquals(true, spawned);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelWorksInAStaticMethod(int classVersion) throws Exception {
        this.sink.answer("onClientCommand", true);
        Class<?> gameClass = this.definePatchedGame(classVersion);

        // No this: parameters start at local variable 0, and so does the resume frame.
        gameClass.getMethod("command", String.class, boolean.class).invoke(null, "/help", true);

        assertEquals(List.of("onClientCommand(/help, true)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Z", "C", "B", "S", "I", "J", "F", "D", "Ljava/lang/String;", "[I"})
    void headCancelReturnsTheNeutralValueOfEveryReturnType(String returnDescriptor) throws Exception {
        this.sink.answer("onOverlayElementPre", true);
        Type returnType = Type.getType(returnDescriptor);
        byte[] original = new SyntheticClass("fake/game/Values", Opcodes.V17)
                .method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()" + returnDescriptor,
                        code -> ClassPatcherTest.returnNonNeutralValue(code, returnType))
                .toByteArray();
        Hook hook = Hook.inMethod("values", "fake/game/Values", "value", "()" + returnDescriptor,
                new HeadCancel(HookCall.to("overlayElementPre", "(Ljava/lang/String;)Z", HookArgument.constant("ANY"))));
        Class<?> values = new ByteClassLoader().define(this.patch(original, List.of(hook)));

        Object returned = values.getMethod("value").invoke(null);

        assertEquals(ClassPatcherTest.neutralValueOf(returnDescriptor), returned);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelPassesAConstant(int classVersion) throws Exception {
        this.sink.answer("onOverlayElementPre", true);
        Object game = this.newPatchedGame(classVersion);

        Object shown = ClassPatcherTest.call(game, "crosshair");

        assertEquals(false, shown);
        assertEquals(List.of("onOverlayElementPre(CROSSHAIRS)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void hookReceivesWideParametersFromTheRightSlots(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "render", "steve", 1.5D, 2.5D, 3.5D, 90.0F, 0.25F);

        assertEquals(
                List.of("onRenderPlayerPre(Game, steve, 1.5, 2.5, 3.5, 0.25)", "render",
                        "onRenderPlayerPost(Game, steve, 1.5, 2.5, 3.5, 0.25)"),
                GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelWorksWhenTheBodyStartsWithALoop(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "spin", 3.0F);

        // The hook fires only once: loop iterations do not go through it again.
        assertEquals(List.of("onOverlayPre(3.0)", "spun", "onOverlayPost()"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headCancelBeforeALoopCanStillCancel(int classVersion) throws Exception {
        this.sink.answer("onOverlayPre", true);
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "spin", 3.0F);

        assertEquals(List.of("onOverlayPre(3.0)"), GameTrace.events());
    }

    // ------------------------------------------------------------------ parameter replacement

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void guiOpenProtocolReplacesTheParameter(int classVersion) throws Exception {
        this.sink.answer("onGuiOpen", GuiOpenDecision.show("replacement"));
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "open", "requested");

        assertEquals("replacement", ClassPatcherTest.field(game, "shown"));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void guiOpenProtocolCancelsTheMethod(int classVersion) throws Exception {
        this.sink.answer("onGuiOpen", GuiOpenDecision.cancel());
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "open", "requested");

        assertEquals(List.of("onGuiOpen(requested)"), GameTrace.events());
        assertNull(ClassPatcherTest.field(game, "shown"));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headReplaceParameterStoresTheHookResult(int classVersion) throws Exception {
        this.sink.answer("onAutoCompleteResponse", new String[] {"alice", "bob", "/help"});
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "complete", (Object) new String[] {"alice", "bob"});

        String[] stored = (String[]) ClassPatcherTest.field(game, "completions");
        assertArrayEquals(new String[] {"alice", "bob", "/help"}, stored);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headReplaceParameterCastsTheHookResult(int classVersion) throws Exception {
        this.sink.answer("onPlaySound", "quieter");
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "play", "loud");

        assertEquals("quieter", ClassPatcherTest.field(game, "played"));
        assertEquals(List.of("onPlaySound(Game, loud)", "play"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void headReplaceParameterCancelsOnNull(int classVersion) throws Exception {
        this.sink.answer("onPlaySound", null);
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "play", "loud");

        assertEquals(List.of("onPlaySound(Game, loud)"), GameTrace.events());
        assertNull(ClassPatcherTest.field(game, "played"));
    }

    // ------------------------------------------------------------------ return value filtering

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void returnFilterReplacesAPrimitiveResult(int classVersion) throws Exception {
        this.sink.answer("onFovUpdate", 0.25F);
        Object game = this.newPatchedGame(classVersion);

        Object fov = ClassPatcherTest.call(game, "fov");

        assertEquals(0.25F, fov);
        assertEquals(List.of("onFovUpdate(1.0, Game)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void returnFilterReplacesAnObjectResult(int classVersion) throws Exception {
        List<String> replacement = List.of("Diamond Sword", "added by a mod");
        this.sink.answer("onItemTooltip", replacement);
        Object game = this.newPatchedGame(classVersion);

        Object tooltip = ClassPatcherTest.call(game, "tooltip", "player", true);

        assertSame(replacement, tooltip);
        assertEquals(List.of("onItemTooltip(ArrayList, Game, player, true)"), GameTrace.events());
    }

    // ------------------------------------------------------------------ anchors

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void anchorCallsSurroundAnInvokeAndPrecedeANew(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "start");

        assertEquals(
                List.of("before", "onModsConstruct()", "refresh", "onModsPreInit()",
                        "between", "onModsInit()", "after"),
                GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void anchorCallBeforeAConstantPassesAParameter(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "world", 2, 0.5F, 123L);

        assertEquals(List.of("sky", "onRenderWorldLast(0.5)", "hand"), GameTrace.events());
    }

    // ------------------------------------------------------------------ redirections

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void redirectReplacesAStaticCallInALoopHead(int classVersion) throws Exception {
        AtomicInteger remaining = new AtomicInteger(2);
        this.sink.answerWith("nextMouseEvent", arguments -> remaining.getAndDecrement() > 0);
        Object game = this.newPatchedGame(classVersion);

        Object polled = ClassPatcherTest.call(game, "poll");

        assertEquals(2, polled);
        // "next" is absent: the original call was replaced, not duplicated.
        assertEquals(List.of("nextMouseEvent()", "nextMouseEvent()", "nextMouseEvent()"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void redirectCanPassThisToTheHook(int classVersion) throws Exception {
        AtomicInteger remaining = new AtomicInteger(1);
        this.sink.answerWith("nextGuiMouseEvent", arguments -> remaining.getAndDecrement() > 0);
        Object game = this.newPatchedGame(classVersion);

        Object polled = ClassPatcherTest.call(game, "pollGui");

        assertEquals(1, polled);
        assertEquals(List.of("nextGuiMouseEvent(Game)", "nextGuiMouseEvent(Game)"), GameTrace.events());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void redirectFindsACallMovedIntoASyntheticMethod(int classVersion) throws Exception {
        Object game = this.newPatchedGame(classVersion);

        ClassPatcherTest.call(game, "frame", 10, 20, 0.5F);

        // The receiver of the replaced call becomes the first argument of the hook.
        assertEquals(List.of("drawScreen(Game, 10, 20, 0.5)"), GameTrace.events());
    }

    // ------------------------------------------------------------------ report and bytecode verification

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void everyHookOfTheTestCatalogIsApplied(int classVersion) {
        HookReport report = ClassPatcherTest.newReport(ClassPatcherTest.hooks());

        this.patcher.patch(FakeGame.bytes(classVersion), ClassPatcherTest.hooks(), report);

        assertTrue(report.isComplete(), report.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void patchedClassPassesTheBytecodeChecks(int classVersion) {
        byte[] patched = this.patch(FakeGame.bytes(classVersion), ClassPatcherTest.hooks());
        ClassHierarchy hierarchy = new ClassHierarchy().withClass(patched).withSystemResources();

        assertEquals(List.of(), BytecodeChecks.problems(patched, hierarchy));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 61})
    void methodsWithoutHookAreLeftExactlyAsTheyWere(int classVersion) {
        byte[] original = FakeGame.bytes(classVersion);
        // A single hook: all the other methods must come out of the rewrite identical.
        byte[] patched = this.patch(original, List.of(ClassPatcherTest.hooks().get(0)));

        Map<String, String> before = MethodDump.ofMethods(original);
        Map<String, String> after = MethodDump.ofMethods(patched);
        before.remove("tick()V");
        after.remove("tick()V");

        assertEquals(before, after);
        assertEquals(MethodDump.ofFields(original), MethodDump.ofFields(patched));
    }

    // ------------------------------------------------------------------ missing anchors and refusals

    @Test
    void hookOnAMissingMethodIsSkipped() {
        List<Hook> hooks = List.of(
                Hook.inMethod("missing.method", ClassPatcherTest.GAME, "noSuchMethod", ClassPatcherTest.VOID,
                        new HeadCall(HookCall.to("clientTickStart", ClassPatcherTest.VOID))));
        HookReport report = ClassPatcherTest.newReport(hooks);

        Optional<byte[]> patched = this.patcher.patch(FakeGame.bytes(61), hooks, report);

        assertTrue(patched.isEmpty());
        assertEquals(HookStatus.SKIPPED, report.outcomeOf("missing.method").status());
    }

    @Test
    void hookWithAMissingAnchorIsSkipped() {
        List<Hook> hooks = List.of(ClassPatcherTest.tickHookAnchoredOn("noSuchCall"));
        HookReport report = ClassPatcherTest.newReport(hooks);

        Optional<byte[]> patched = this.patcher.patch(FakeGame.bytes(61), hooks, report);

        assertTrue(patched.isEmpty());
        assertEquals(HookStatus.SKIPPED, report.outcomeOf("tick.anchored").status());
    }

    @Test
    void hookIsAppliedEntirelyOrNotAtAll() throws Exception {
        // The hook's first injection is possible, the second finds no anchor.
        List<Hook> hooks = List.of(ClassPatcherTest.tickHookAnchoredOn("noSuchCall"), ClassPatcherTest.hooks().get(1));
        Object game = this.newGame(this.patch(FakeGame.bytes(61), hooks));

        ClassPatcherTest.call(game, "tick");

        assertEquals(List.of("tick"), GameTrace.events());
    }

    @Test
    void otherHooksAreAppliedWhenOneIsSkipped() throws Exception {
        List<Hook> hooks = List.of(ClassPatcherTest.tickHookAnchoredOn("noSuchCall"), ClassPatcherTest.hooks().get(1));
        Object game = this.newGame(this.patch(FakeGame.bytes(61), hooks));

        ClassPatcherTest.call(game, "update", false);

        assertEquals(List.of("onPlayerTickStart(Game)", "late", "onPlayerTickEnd(Game)"), GameTrace.events());
    }

    @Test
    void headInjectionIsRefusedInAConstructor() {
        Hook hook = Hook.inMethod("bad.constructor", ClassPatcherTest.GAME, "<init>", ClassPatcherTest.OBJECT_TO_VOID,
                new HeadCall(HookCall.to("clientTickStart", ClassPatcherTest.VOID)));

        assertEquals(HookStatus.FAILED, this.statusAfterPatching(hook));
    }

    @Test
    void thisIsRefusedInAStaticMethod() {
        Hook hook = Hook.inMethod("bad.this", ClassPatcherTest.GAME, "main", "([Ljava/lang/String;)V",
                new HeadCall(HookCall.to("joinGame", ClassPatcherTest.OBJECT_TO_VOID, HookArgument.self())));

        assertEquals(HookStatus.FAILED, this.statusAfterPatching(hook));
    }

    @Test
    void hookThatCannotReceiveTheArgumentsIsRefused() {
        // chat(Object) has an object parameter, but overlayPre expects a float.
        Hook hook = Hook.inMethod("bad.types", ClassPatcherTest.GAME, "chat", ClassPatcherTest.OBJECT_TO_VOID,
                new HeadCancel(HookCall.to("overlayPre", "(F)Z", HookArgument.parameter(1))));

        assertEquals(HookStatus.FAILED, this.statusAfterPatching(hook));
    }

    @Test
    void redirectionWithADifferentStackEffectIsRefused() {
        // GameTrace.next() returns a boolean, clientTickStart() returns nothing.
        Hook hook = Hook.inMethod("bad.redirect", ClassPatcherTest.GAME, "poll", "()I",
                new RedirectInvoke(Anchor.invoke(ClassPatcherTest.TRACE, "next", "()Z"),
                        HookCall.to("clientTickStart", ClassPatcherTest.VOID), false));

        assertEquals(HookStatus.FAILED, this.statusAfterPatching(hook));
    }

    @Test
    void codeIsNotInsertedBeforeANewDesignatedByAFrame() {
        Hook hook = Hook.inMethod("bad.new", ClassPatcherTest.GAME, "risky", "(Z)V",
                new AnchorCall(AnchorPosition.BEFORE, Anchor.newInstance("java/lang/StringBuilder"), Occurrence.FIRST,
                        HookCall.to("modsInit", ClassPatcherTest.VOID)));

        assertEquals(HookStatus.FAILED, this.statusAfterPatching(hook));
    }

    @Test
    void codeCanBeInsertedAfterANewDesignatedByAFrame() throws Exception {
        Hook hook = Hook.inMethod("after.new", ClassPatcherTest.GAME, "risky", "(Z)V",
                new AnchorCall(AnchorPosition.AFTER, Anchor.newInstance("java/lang/StringBuilder"), Occurrence.FIRST,
                        HookCall.to("modsInit", ClassPatcherTest.VOID)));
        Object game = this.newGame(this.patch(FakeGame.bytes(61), List.of(hook)));

        ClassPatcherTest.call(game, "risky", true);

        assertEquals(List.of("onModsInit()"), GameTrace.events());
    }

    @Test
    void untouchedClassIsNotRewritten() {
        List<Hook> hooks = List.of(Hook.inAnyMethod("nowhere", ClassPatcherTest.GAME,
                new RedirectInvoke(Anchor.invoke("no/such/Owner", "call", ClassPatcherTest.VOID),
                        HookCall.to("clientTickStart", ClassPatcherTest.VOID), false)));

        assertFalse(this.patcher.patch(FakeGame.bytes(61), hooks, ClassPatcherTest.newReport(hooks)).isPresent());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The test catalog: one entry per strategy, each wired to a real {@code GameHooks} method of
     * a suitable shape.
     */
    private static List<Hook> hooks() {
        HookArgument self = HookArgument.self();
        HookArgument first = HookArgument.parameter(1);
        HookArgument second = HookArgument.parameter(2);
        InvokeAnchor refresh = Anchor.invoke(ClassPatcherTest.GAME, "refresh", ClassPatcherTest.VOID);
        InvokeAnchor next = Anchor.invoke(ClassPatcherTest.TRACE, "next", "()Z");
        String playerPre = "(Ljava/lang/Object;Ljava/lang/Object;DDDF)Z";
        String playerPost = "(Ljava/lang/Object;Ljava/lang/Object;DDDF)V";
        HookArgument[] playerArguments = {
            self, first, second, HookArgument.parameter(3), HookArgument.parameter(4), HookArgument.parameter(6)
        };
        // Short names so that each table entry fits on few lines.
        String game = ClassPatcherTest.GAME;
        String noArgument = ClassPatcherTest.VOID;
        String objectToVoid = ClassPatcherTest.OBJECT_TO_VOID;
        String objectToBoolean = ClassPatcherTest.OBJECT_TO_BOOLEAN;
        String twoObjectsToObject = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
        return List.of(
                Hook.inMethod("tick", game, "tick", noArgument,
                        new HeadCall(HookCall.to("clientTickStart", noArgument)),
                        new ReturnCall(HookCall.to("clientTickEnd", noArgument), Occurrence.EVERY)),
                Hook.inMethod("update", game, "update", "(Z)V",
                        new HeadCall(HookCall.to("playerTickStart", objectToVoid, self)),
                        new ReturnCall(HookCall.to("playerTickEnd", objectToVoid, self), Occurrence.EVERY)),
                Hook.inMethod("init", game, "init", "(Z)V",
                        new HeadCall(HookCall.to("guiInitPre", objectToVoid, self)),
                        new ReturnCall(HookCall.to("guiInitPost", objectToVoid, self), Occurrence.LAST)),
                Hook.inMethod("constructor", game, "<init>", objectToVoid,
                        new ReturnCall(HookCall.to("worldLoad", objectToVoid, self), Occurrence.EVERY)),
                Hook.inMethod("main", game, "main", "([Ljava/lang/String;)V",
                        new HeadCall(HookCall.to("gameMain", "([Ljava/lang/String;)V", first))),
                Hook.inMethod("chat", game, "chat", objectToVoid,
                        new HeadCancel(HookCall.to("chatReceived", objectToBoolean, first))),
                Hook.inMethod("command", game, "command", "(Ljava/lang/String;Z)V",
                        new HeadCancel(HookCall.to("clientCommand", "(Ljava/lang/String;Z)Z", first, second))),
                Hook.inMethod("spawn", game, "spawn", objectToBoolean,
                        new HeadCancel(HookCall.to(
                                "entityJoinWorld", "(Ljava/lang/Object;Ljava/lang/Object;)Z", self, first))),
                Hook.inMethod("crosshair", game, "crosshair", "()Z",
                        new HeadCancel(HookCall.to("overlayElementPre", "(Ljava/lang/String;)Z",
                                HookArgument.constant("CROSSHAIRS")))),
                Hook.inMethod("render", game, "render", "(Ljava/lang/Object;DDDFF)V",
                        new HeadCancel(HookCall.to("renderPlayerPre", playerPre, playerArguments)),
                        new ReturnCall(HookCall.to("renderPlayerPost", playerPost, playerArguments), Occurrence.EVERY)),
                Hook.inMethod("spin", game, "spin", "(F)V",
                        new HeadCancel(HookCall.to("overlayPre", "(F)Z", first)),
                        new ReturnCall(HookCall.to("overlayPost", noArgument), Occurrence.LAST)),
                Hook.inMethod("open", game, "open", "(Ljava/lang/String;)V",
                        new HeadCancelThenReplaceParameter(1,
                                HookCall.to("guiOpen", objectToBoolean, first),
                                HookCall.to("guiOpenResult", "()Ljava/lang/Object;"))),
                Hook.inMethod("complete", game, "complete", "([Ljava/lang/String;)V",
                        new HeadReplaceParameter(1,
                                HookCall.to("autoCompleteResponse", "([Ljava/lang/String;)[Ljava/lang/String;", first),
                                false)),
                Hook.inMethod("play", game, "play", "(Ljava/lang/CharSequence;)V",
                        new HeadReplaceParameter(1, HookCall.to("playSound", twoObjectsToObject, self, first), true)),
                Hook.inMethod("fov", game, "fov", "()F",
                        new ReturnFilter(HookCall.to("fovUpdate", "(FLjava/lang/Object;)F", self))),
                Hook.inMethod("tooltip", game, "tooltip", "(Ljava/lang/Object;Z)Ljava/util/List;",
                        new ReturnFilter(HookCall.to("itemTooltip",
                                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)Ljava/lang/Object;",
                                self, first, second))),
                Hook.inMethod("start.construct", game, "start", noArgument,
                        new AnchorCall(AnchorPosition.BEFORE, refresh, Occurrence.FIRST,
                                HookCall.to("modsConstruct", noArgument))),
                Hook.inMethod("start.preinit", game, "start", noArgument,
                        new AnchorCall(AnchorPosition.AFTER, refresh, Occurrence.FIRST,
                                HookCall.to("modsPreInit", noArgument))),
                Hook.inMethod("start.init", game, "start", noArgument,
                        new AnchorCall(AnchorPosition.BEFORE, Anchor.newInstance("java/lang/StringBuilder"),
                                Occurrence.FIRST, HookCall.to("modsInit", noArgument))),
                Hook.inMethod("world", game, "world", "(IFJ)V",
                        new AnchorCall(AnchorPosition.BEFORE, Anchor.constant("hand"), Occurrence.FIRST,
                                HookCall.to("renderWorldLast", "(F)V", second))),
                Hook.inMethod("poll", game, "poll", "()I",
                        new RedirectInvoke(next, HookCall.to("mouseNext", "()Z"), false)),
                Hook.inMethod("poll.gui", game, "pollGui", "()I",
                        new RedirectInvoke(next, HookCall.to("guiMouseNext", objectToBoolean), true)),
                Hook.inAnyMethod("draw", game,
                        new RedirectInvoke(Anchor.invoke(game, "draw", "(IIF)V"),
                                HookCall.to("drawScreen", "(Ljava/lang/Object;IIF)V"), false)));
    }

    /** A hook whose second injection targets a call that does not exist. */
    private static Hook tickHookAnchoredOn(String missingMethod) {
        return Hook.inMethod("tick.anchored", ClassPatcherTest.GAME, "tick", ClassPatcherTest.VOID,
                new HeadCall(HookCall.to("clientTickStart", ClassPatcherTest.VOID)),
                new AnchorCall(AnchorPosition.BEFORE,
                        Anchor.invoke(ClassPatcherTest.GAME, missingMethod, ClassPatcherTest.VOID), Occurrence.FIRST,
                        HookCall.to("clientTickEnd", ClassPatcherTest.VOID)));
    }

    private static HookReport newReport(List<Hook> hooks) {
        return new HookReport(hooks.stream().map(Hook::id).toList());
    }

    private HookStatus statusAfterPatching(Hook hook) {
        HookReport report = ClassPatcherTest.newReport(List.of(hook));
        this.patcher.patch(FakeGame.bytes(61), List.of(hook), report);
        return report.outcomeOf(hook.id()).status();
    }

    private byte[] patch(byte[] original, List<Hook> hooks) {
        return this.patcher.patch(original, hooks, ClassPatcherTest.newReport(hooks)).orElse(original);
    }

    private Class<?> definePatchedGame(int classVersion) {
        return new ByteClassLoader().define(this.patch(FakeGame.bytes(classVersion), ClassPatcherTest.hooks()));
    }

    private Object newPatchedGame(int classVersion) throws ReflectiveOperationException {
        return this.definePatchedGame(classVersion).getConstructor().newInstance();
    }

    private Object newGame(byte[] classBytes) throws ReflectiveOperationException {
        return new ByteClassLoader().define(classBytes).getConstructor().newInstance();
    }

    /** Calls by reflection the fake game's public method of this name (they are all distinct). */
    private static Object call(Object game, String methodName, Object... arguments)
            throws ReflectiveOperationException {
        for (Method method : game.getClass().getMethods()) {
            if (method.getName().equals(methodName)) {
                return method.invoke(game, arguments);
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static Object field(Object game, String fieldName) throws ReflectiveOperationException {
        return game.getClass().getField(fieldName).get(game);
    }

    /** Writes a body returning a value different from the type's neutral value: 1, "value" or an array. */
    private static void returnNonNeutralValue(MethodVisitor code, Type returnType) {
        switch (returnType.getSort()) {
            case Type.LONG -> code.visitInsn(Opcodes.LCONST_1);
            case Type.FLOAT -> code.visitInsn(Opcodes.FCONST_1);
            case Type.DOUBLE -> code.visitInsn(Opcodes.DCONST_1);
            case Type.OBJECT -> code.visitLdcInsn("value");
            case Type.ARRAY -> {
                code.visitInsn(Opcodes.ICONST_1);
                code.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            }
            default -> code.visitInsn(Opcodes.ICONST_1);
        }
        code.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
    }

    /** Returns the neutral value of a type as reflection returns it: false, zero or {@code null}. */
    private static Object neutralValueOf(String descriptor) {
        return switch (descriptor) {
            case "Z" -> false;
            case "C" -> '\0';
            case "B" -> (byte) 0;
            case "S" -> (short) 0;
            case "I" -> 0;
            case "J" -> 0L;
            case "F" -> 0.0F;
            case "D" -> 0.0D;
            default -> null;
        };
    }
}
