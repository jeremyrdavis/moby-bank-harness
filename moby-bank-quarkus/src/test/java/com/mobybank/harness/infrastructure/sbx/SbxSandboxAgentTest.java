package com.mobybank.harness.infrastructure.sbx;

import static com.mobybank.harness.infrastructure.sbx.FakeRunner.has;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.CatalogFile;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Folder;
import com.mobybank.harness.domain.FolderId;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.Session;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.domain.StepKind;
import com.mobybank.harness.infrastructure.sbx.FakeRunner.Response;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SbxSandboxAgentTest {

    private static final SessionId SESSION = SandboxRegistryTest.SESSION;
    private static final String NAME = "harness-0a1b2c3d4e5f-g0";
    private static final Instant T0 = Instant.parse("2026-09-30T12:00:00Z");

    private static final String INIT = "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"agent-1\"}";
    private static final String READ_STEP = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Read\","
            + "\"input\":{\"file_path\":\"/home/agent/workspace/files/a.pdf\"}}]},\"session_id\":\"agent-1\"}";
    private static final String CALC_STEP = "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Bash\","
            + "\"input\":{\"command\":\"python3 ratios.py\"}}]},\"session_id\":\"agent-1\"}";
    private static final String RESULT = "{\"type\":\"result\",\"is_error\":false,\"session_id\":\"agent-1\","
            + "\"result\":\"Leverage rose.\\n\\n| Metric | Value |\\n|---|---|\\n| Net leverage | 2.9x |\"}";

    private FakeRunner runner;
    private SandboxRegistry registry;
    private Map<String, byte[]> uploads;
    private Map<String, byte[]> onedrive;
    private SbxSandboxAgent agent;
    private final List<Step> streamed = new ArrayList<>();
    private final Map<String, String> copied = new HashMap<>();
    private final List<Path> stagedPaths = new ArrayList<>();

    @BeforeEach
    void setUp() {
        runner = new FakeRunner();
        registry = new SandboxRegistry();
        uploads = new HashMap<>();
        onedrive = new HashMap<>();
        UploadStore store = new UploadStore() {
            @Override
            public void store(SessionId sessionId, String fileName, byte[] content) {
                uploads.put(fileName, content);
            }

            @Override
            public Optional<byte[]> find(SessionId sessionId, String fileName) {
                return Optional.ofNullable(uploads.get(fileName));
            }
        };
        DocumentCatalog catalog = new DocumentCatalog() {
            @Override
            public List<Folder> folders() {
                return List.of();
            }

            @Override
            public List<CatalogFile> files(FolderId folderId) {
                return List.of();
            }

            @Override
            public Optional<byte[]> fetch(String fileName) {
                return Optional.ofNullable(onedrive.get(fileName));
            }
        };
        SbxSettings settings = new SbxSettings("claude", List.of("--dangerously-skip-permissions"),
                "/home/agent/workspace", Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(8), "stop");
        agent = new SbxSandboxAgent(new SbxCli(runner, "sbx", Duration.ofMinutes(2)), registry, store, catalog, settings);

        // Capture staged files while they still exist: sbx cp <local> <sandbox>:<remote>
        runner.on(command -> command.contains("cp"), command -> {
            Path local = Path.of(command.get(command.size() - 2));
            stagedPaths.add(local);
            try {
                copied.put(command.get(command.size() - 1), Files.readString(local));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return Response.ok();
        });
    }

    /** The agent run ({@code sbx exec <sandbox> claude -p <prompt> --output-format ...}), not create, cp or mkdir. */
    private static boolean isAgentRun(List<String> command) {
        return command.contains("exec") && command.contains("--output-format");
    }

    private void agentPrints(String... lines) {
        runner.on(SbxSandboxAgentTest::isAgentRun, Response.ok(lines));
    }

    private List<String> agentRunLines() {
        return runner.calls.stream().filter(c -> isAgentRun(c.command())).map(FakeRunner.Call::line).toList();
    }

    private static Session sessionAt(Location location) {
        return Session.start(SESSION, location, T0);
    }

    private static AgentTurn turn(Session session, String text, FileRef... files) {
        List<com.mobybank.harness.domain.Message> history = session.messages();
        var message = session.postUserMessage(text, List.of(files), T0);
        return new AgentTurn(session.id(), session.location(), history, message, session.filesShared());
    }

    private static List<String> execOf(FakeRunner runner) {
        return runner.calls.stream().map(FakeRunner.Call::command).filter(SbxSandboxAgentTest::isAgentRun).findFirst()
                .orElseThrow();
    }

    // --- a first turn ------------------------------------------------------------------------------------------

    @Test
    void aFirstTurnCreatesTheSandboxRunsTheAgentAndParsesTheAnswer() {
        agentPrints(INIT, READ_STEP, CALC_STEP, RESULT);

        AgentReply reply = agent.runTurn(turn(sessionAt(Location.LOCAL), "How did leverage change?"), streamed::add);

        assertEquals(List.of(new Step(StepKind.READ, "/home/agent/workspace/files/a.pdf"),
                new Step(StepKind.COMPUTE, "python3 ratios.py")), streamed);
        assertEquals(streamed, reply.steps());
        assertEquals(List.of("Leverage rose."), reply.paragraphs());
        assertEquals(List.of("Metric", "Value"), reply.table().orElseThrow().cols());

        List<String> lines = runner.lines();
        assertEquals("sbx ls -q", lines.get(0), "looks for an existing sandbox first");
        assertEquals("sbx create --name " + NAME + " claude", lines.get(1));
        assertTrue(lines.get(2).startsWith("sbx exec " + NAME + " claude -p "), lines.get(2));

        assertEquals(Optional.of(new Handle(Location.LOCAL, NAME, 0)), registry.current(SESSION));
        assertEquals(Optional.of("agent-1"), registry.agentSession(SESSION));
    }

    @Test
    void theAgentIsRunHeadlessWithAStreamingJsonOutputAndTheConfiguredArguments() {
        agentPrints(INIT, RESULT);
        agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add);

        List<String> exec = execOf(runner);
        assertEquals(List.of("sbx", "exec", NAME, "claude", "-p"), exec.subList(0, 5));
        assertEquals(List.of("--output-format", "stream-json", "--verbose", "--dangerously-skip-permissions"),
                exec.subList(6, exec.size()));
        assertTrue(exec.get(5).contains("Analyst request:\nhello"), exec.get(5));
        assertEquals(Duration.ofMinutes(10), runner.calls.get(runner.calls.size() - 1).timeout());
    }

    @Test
    void aCloudTurnUsesTheCloudFlagOnEveryCommandAndASandboxTtl() {
        agentPrints(INIT, RESULT);
        agent.runTurn(turn(sessionAt(Location.CLOUD), "hello"), streamed::add);

        assertEquals("sbx --cloud ls -q", runner.lines().get(0));
        assertEquals("sbx --cloud create --name " + NAME + " --ttl 8h --on-timeout stop claude", runner.lines().get(1));
        assertTrue(runner.lines().get(2).startsWith("sbx --cloud exec " + NAME + " claude"), runner.lines().get(2));
        assertEquals(Location.CLOUD, registry.current(SESSION).orElseThrow().location());
    }

    // --- later turns -------------------------------------------------------------------------------------------

    @Test
    void theNextTurnReusesTheSandboxAndResumesTheAgentConversation() {
        agentPrints(INIT, RESULT);
        Session session = sessionAt(Location.LOCAL);
        agent.runTurn(turn(session, "first"), streamed::add);
        session.recordAgentReply(new AgentReply(List.of(), List.of("Leverage rose."), Optional.empty()), T0);
        runner.calls.clear();

        agent.runTurn(turn(session, "second"), streamed::add);

        assertEquals(1, runner.calls.size(), "no ls and no create: the sandbox is known");
        List<String> exec = execOf(runner);
        assertEquals(List.of("--resume", "agent-1"), exec.subList(exec.size() - 2, exec.size()));
        assertFalse(exec.get(5).contains("Earlier in this conversation"), "the agent already has the history");
    }

    @Test
    void aSessionWithHistoryButNoAgentConversationSendsTheHistoryInThePrompt() {
        agentPrints(INIT, RESULT);
        Session session = sessionAt(Location.LOCAL);
        session.postUserMessage("Summarize the Q2 results", List.of(), T0);
        session.recordAgentReply(new AgentReply(List.of(), List.of("Revenue beat consensus."), Optional.empty()), T0);

        agent.runTurn(turn(session, "And leverage?"), streamed::add);

        String prompt = execOf(runner).get(5);
        assertTrue(prompt.contains("Earlier in this conversation:\nAnalyst: Summarize the Q2 results\nYou: Revenue beat consensus."),
                prompt);
        assertTrue(prompt.endsWith("Analyst request:\nAnd leverage?"), prompt);
        assertFalse(execOf(runner).contains("--resume"));
    }

    @Test
    void ifResumingFailsTheTurnIsRetriedOnceWithTheHistoryInsteadOfAResume() {
        Session session = sessionAt(Location.LOCAL);
        session.postUserMessage("earlier question", List.of(), T0);
        session.recordAgentReply(new AgentReply(List.of(), List.of("earlier answer"), Optional.empty()), T0);
        registry.set(SESSION, new Handle(Location.LOCAL, NAME, 0));
        registry.setAgentSession(SESSION, "stale-id");
        runner.on(command -> command.contains("--resume"), Response.fail(1, "No conversation found with session ID"));
        agentPrints(INIT, RESULT);

        AgentReply reply = agent.runTurn(turn(session, "next"), streamed::add);

        assertEquals(List.of("Leverage rose."), reply.paragraphs());
        List<List<String>> execs = runner.calls.stream().map(FakeRunner.Call::command).filter(SbxSandboxAgentTest::isAgentRun).toList();
        assertEquals(2, execs.size());
        assertTrue(execs.get(0).contains("--resume"));
        assertFalse(execs.get(1).contains("--resume"));
        assertTrue(execs.get(1).get(5).contains("Earlier in this conversation"));
        assertEquals(Optional.of("agent-1"), registry.agentSession(SESSION), "the new conversation id replaces the stale one");
    }

    @Test
    void aSandboxFromBeforeARestartIsFoundAgainByItsName() {
        runner.on(has("ls"), Response.ok("claude/harness-0a1b2c3d4e5f-g0", "claude/harness-0a1b2c3d4e5f-g2-ab12",
                "harness-ffffffffffff-g9", "unrelated"));
        agentPrints(INIT, RESULT);

        agent.runTurn(turn(sessionAt(Location.CLOUD), "hello"), streamed::add);

        assertTrue(runner.linesWith("create").isEmpty(), "reuses the newest generation instead of creating one");
        assertTrue(runner.lines().get(1).startsWith("sbx --cloud exec harness-0a1b2c3d4e5f-g2-ab12 claude"),
                "the agent prefix is dropped: " + runner.lines().get(1));
        assertEquals(2, registry.current(SESSION).orElseThrow().generation());
    }

    // --- files -------------------------------------------------------------------------------------------------

    @Test
    void attachedFilesAreCopiedIntoTheSandboxBeforeTheAgentRuns() {
        uploads.put("notes.docx", "uploaded text".getBytes(StandardCharsets.UTF_8));
        onedrive.put("Fathom_10-Q.pdf", "onedrive text".getBytes(StandardCharsets.UTF_8));
        agentPrints(INIT, RESULT);

        agent.runTurn(turn(sessionAt(Location.LOCAL), "Summarize",
                new FileRef("notes.docx", FileSource.UPLOAD), new FileRef("Fathom_10-Q.pdf", FileSource.ONEDRIVE)),
                streamed::add);

        List<String> lines = runner.lines();
        int mkdir = lines.indexOf("sbx exec " + NAME + " mkdir -p /home/agent/workspace/files");
        int exec = lines.indexOf(agentRunLines().get(0));
        assertTrue(mkdir > 0 && mkdir < exec, lines.toString());
        assertEquals("uploaded text", copied.get(NAME + ":/home/agent/workspace/files/notes.docx"));
        assertEquals("onedrive text", copied.get(NAME + ":/home/agent/workspace/files/Fathom_10-Q.pdf"));
        assertTrue(lines.indexOf(lines.stream().filter(l -> l.contains(" cp ")).findFirst().orElseThrow()) < exec);

        String prompt = execOf(runner).get(5);
        assertTrue(prompt.contains("Attached files (in /home/agent/workspace/files):\n- notes.docx\n- Fathom_10-Q.pdf"), prompt);
    }

    @Test
    void stagedCopiesAreDeletedAfterwards() {
        uploads.put("notes.docx", "x".getBytes(StandardCharsets.UTF_8));
        agentPrints(INIT, RESULT);

        agent.runTurn(turn(sessionAt(Location.LOCAL), "go", new FileRef("notes.docx", FileSource.UPLOAD)), streamed::add);

        assertEquals(1, stagedPaths.size());
        assertFalse(Files.exists(stagedPaths.get(0)));
        assertFalse(Files.exists(stagedPaths.get(0).getParent()));
    }

    @Test
    void anAttachedFileThatIsNoLongerAvailableFailsTheTurnWithItsName() {
        SandboxFailureException e = assertThrows(SandboxFailureException.class, () -> agent.runTurn(
                turn(sessionAt(Location.LOCAL), "go", new FileRef("gone.pdf", FileSource.UPLOAD)), streamed::add));
        assertTrue(e.getMessage().contains("gone.pdf"), e.getMessage());
        assertTrue(agentRunLines().isEmpty(), "the agent must not run without its files");
    }

    @Test
    void aFileNameWithAPathIsRefusedBeforeAnythingIsCopied() {
        onedrive.put("../escape.pdf", "x".getBytes(StandardCharsets.UTF_8));
        assertThrows(SandboxFailureException.class, () -> agent.runTurn(
                turn(sessionAt(Location.LOCAL), "go", new FileRef("../escape.pdf", FileSource.ONEDRIVE)), streamed::add));
        assertTrue(copied.isEmpty());
    }

    // --- failures ----------------------------------------------------------------------------------------------

    @Test
    void aFailedAgentRunWithNoAnswerFailsTheTurnWithWhatItSaid() {
        runner.on(has("ls"), Response.ok(NAME));
        runner.on(SbxSandboxAgentTest::isAgentRun, Response.fail(1, "Error: not authenticated; run sbx secret set anthropic"));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add));

        assertTrue(e.getMessage().contains(NAME), e.getMessage());
        assertTrue(e.getMessage().contains("not authenticated"), e.getMessage());
        assertTrue(registry.agentSession(SESSION).isEmpty());
        assertTrue(runner.linesWith("create").isEmpty(), "a sandbox that still exists is not replaced");
    }

    @Test
    void anErrorResultFromTheAgentFailsTheTurn() {
        runner.on(has("ls"), Response.ok(NAME));
        agentPrints(INIT, "{\"type\":\"result\",\"is_error\":true,\"result\":\"Credit balance is too low\"}");
        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add));
        assertTrue(e.getMessage().contains("Credit balance is too low"), e.getMessage());
    }

    @Test
    void anAnswerIsKeptEvenIfTheAgentExitsNonZeroAfterGivingIt() {
        runner.on(SbxSandboxAgentTest::isAgentRun, new Response(1, List.of(INIT, RESULT), "warning: something"));
        AgentReply reply = agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add);
        assertEquals(List.of("Leverage rose."), reply.paragraphs());
    }

    @Test
    void withoutAResultEventTheLastAssistantMessageIsTheAnswer() {
        agentPrints(INIT, "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Here is the answer.\"}]}}");
        AgentReply reply = agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add);
        assertEquals(List.of("Here is the answer."), reply.paragraphs());
    }

    @Test
    void aSandboxThatCannotBeCreatedFailsTheTurnWithTheReason() {
        runner.on(has("create"), Response.fail(1, "sandboxd is not running"));
        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add));
        assertTrue(e.getMessage().contains("sandboxd is not running"), e.getMessage());
        assertTrue(registry.current(SESSION).isEmpty());
    }

    // --- a sandbox that is gone --------------------------------------------------------------------------------

    private static final String NEXT = "harness-0a1b2c3d4e5f-g1";

    /** A session that has already had a turn in {@code location}, with one attached file and the agent's own id. */
    private Session earlierTurnAt(Location location) {
        uploads.put("a.pdf", "pdf text".getBytes(StandardCharsets.UTF_8));
        Session session = sessionAt(location);
        session.postUserMessage("Summarize a.pdf", List.of(new FileRef("a.pdf", FileSource.UPLOAD)), T0);
        session.recordAgentReply(new AgentReply(List.of(), List.of("It is a 10-Q."), Optional.empty()), T0);
        registry.set(SESSION, new Handle(location, NAME, 0));
        registry.setAgentSession(SESSION, "agent-0");
        // The old sandbox is gone: every command against it fails, and it is not listed.
        runner.on(command -> command.contains("exec") && command.contains(NAME),
                Response.fail(1, "error: sandbox '" + NAME + "' not found"));
        return session;
    }

    @Test
    void aSandboxThatIsGoneIsReplacedFromTheHistoryWithANoticeForTheAnalyst() {
        Session session = earlierTurnAt(Location.LOCAL);
        agentPrints(INIT, RESULT);

        AgentReply reply = agent.runTurn(turn(session, "And leverage?"), streamed::add);

        assertEquals(List.of("Leverage rose."), reply.paragraphs());
        String notice = reply.notice().orElseThrow();
        assertTrue(notice.startsWith("The local sandbox was no longer there"), notice);
        assertTrue(notice.contains("Files you attached earlier were copied in again"), notice);

        assertEquals(1, runner.linesWith("create").size());
        assertEquals("sbx create --name " + NEXT + " claude", runner.linesWith("create").get(0));
        assertEquals("pdf text", copied.get(NEXT + ":/home/agent/workspace/files/a.pdf"), "the earlier file is back");
        List<String> rerun = runner.calls.stream().map(FakeRunner.Call::command).filter(SbxSandboxAgentTest::isAgentRun)
                .filter(command -> command.contains(NEXT)).findFirst().orElseThrow();
        assertFalse(rerun.contains("--resume"), "the old agent conversation is gone with the sandbox");
        assertTrue(rerun.get(5).contains("Earlier in this conversation:\nAnalyst: Summarize a.pdf\nYou: It is a 10-Q."),
                rerun.get(5));
        assertEquals(Optional.of(new Handle(Location.LOCAL, NEXT, 1)), registry.current(SESSION));
        assertEquals(Optional.of("agent-1"), registry.agentSession(SESSION));
    }

    @Test
    void aCloudSandboxThatIsGoneSaysSoInTheNotice() {
        Session session = earlierTurnAt(Location.CLOUD);
        agentPrints(INIT, RESULT);

        AgentReply reply = agent.runTurn(turn(session, "And leverage?"), streamed::add);

        String notice = reply.notice().orElseThrow();
        assertTrue(notice.startsWith("The cloud sandbox was no longer there (cloud sandboxes can time out or be deleted)"),
                notice);
        assertTrue(notice.contains("a new cloud sandbox"), notice);
        assertEquals("sbx --cloud create --name " + NEXT + " --ttl 8h --on-timeout stop claude",
                runner.linesWith("create").get(0));
    }

    @Test
    void aGoneSandboxIsDetectedWhileCopyingTheNewFilesToo() {
        Session session = earlierTurnAt(Location.LOCAL);
        agentPrints(INIT, RESULT);
        uploads.put("b.pdf", "second".getBytes(StandardCharsets.UTF_8));

        AgentReply reply = agent.runTurn(turn(session, "Compare", new FileRef("b.pdf", FileSource.UPLOAD)),
                streamed::add);

        assertTrue(reply.notice().isPresent());
        assertEquals("pdf text", copied.get(NEXT + ":/home/agent/workspace/files/a.pdf"));
        assertEquals("second", copied.get(NEXT + ":/home/agent/workspace/files/b.pdf"));
    }

    @Test
    void anOrdinaryReplyHasNoNotice() {
        agentPrints(INIT, RESULT);
        assertTrue(agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add).notice().isEmpty());
    }

    @Test
    void ifTheListingFailsTooTheOriginalFailureIsReported() {
        Session session = earlierTurnAt(Location.LOCAL);
        runner.on(has("ls"), Response.fail(1, "error: not signed in"));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> agent.runTurn(turn(session, "again"), streamed::add));

        assertTrue(e.getMessage().contains("The agent failed in " + NAME), e.getMessage());
        assertTrue(runner.linesWith("create").isEmpty(), "nothing is replaced when it cannot be checked");
    }

    @Test
    void aSessionThatMovedAwayFromAGoneSandboxStartsTheNextGenerationAtTheDestination() {
        uploads.put("a.pdf", "pdf text".getBytes(StandardCharsets.UTF_8));
        registry.set(SESSION, new Handle(Location.CLOUD, "harness-0a1b2c3d4e5f-g1", 1));
        Session session = sessionAt(Location.LOCAL);
        session.postUserMessage("Summarize a.pdf", List.of(new FileRef("a.pdf", FileSource.UPLOAD)), T0);
        session.recordAgentReply(new AgentReply(List.of(), List.of("It is a 10-Q."), Optional.empty()), T0);
        agentPrints(INIT, RESULT);

        agent.runTurn(turn(session, "next"), streamed::add);

        assertEquals("sbx create --name harness-0a1b2c3d4e5f-g2 claude", runner.linesWith("create").get(0));
        assertEquals("pdf text", copied.get("harness-0a1b2c3d4e5f-g2:/home/agent/workspace/files/a.pdf"),
                "a fresh sandbox gets every file shared so far, not only the new message's");
    }

    @Test
    void theAgentConversationIdIsReplacedByTheLatestOneEveryTurn() {
        Session session = sessionAt(Location.LOCAL);
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        runner.on(SbxSandboxAgentTest::isAgentRun, command -> runs.incrementAndGet() == 1
                ? Response.ok(INIT, RESULT)
                : Response.ok(INIT.replace("agent-1", "agent-2"), RESULT.replace("agent-1", "agent-2")));
        agent.runTurn(turn(session, "first"), streamed::add);
        session.recordAgentReply(new AgentReply(List.of(), List.of("ok"), Optional.empty()), T0);

        agent.runTurn(turn(session, "second"), streamed::add);

        List<String> second = runner.calls.stream().map(FakeRunner.Call::command)
                .filter(SbxSandboxAgentTest::isAgentRun).reduce((a, b) -> b).orElseThrow();
        assertEquals(List.of("--resume", "agent-1"), second.subList(second.size() - 2, second.size()));
        assertEquals(Optional.of("agent-2"), registry.agentSession(SESSION), "resuming returns a new id each time");
    }

    @Test
    void aRejectedCredentialInTheCloudSaysWhichSecretToSetAndKeepsWhatTheAgentSaid() {
        runner.on(has("ls"), Response.ok(NAME));
        agentPrints(INIT, "{\"type\":\"result\",\"is_error\":true,\"result\":\"Invalid API key · Fix external API key\"}");

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> agent.runTurn(turn(sessionAt(Location.CLOUD), "hello"), streamed::add));

        assertTrue(e.getMessage().startsWith("The agent failed in " + NAME + ": The agent's credential was rejected (401)"),
                e.getMessage());
        assertTrue(e.getMessage().contains("`sbx --cloud secret set anthropic --oauth`"), e.getMessage());
        assertTrue(e.getMessage().contains("(Invalid API key · Fix external API key)"), e.getMessage());
    }
}
