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
                "/home/agent/workspace", Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(8));
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
        return new AgentTurn(session.id(), session.location(), history, message);
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
        assertEquals("sbx --cloud create --name " + NAME + " --ttl 8h claude", runner.lines().get(1));
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
        assertTrue(runner.lines().get(1).startsWith("sbx --cloud exec claude/harness-0a1b2c3d4e5f-g2-ab12 claude"),
                runner.lines().get(1));
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
        runner.on(SbxSandboxAgentTest::isAgentRun, Response.fail(1, "Error: not authenticated; run sbx secret set anthropic"));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> agent.runTurn(turn(sessionAt(Location.LOCAL), "hello"), streamed::add));

        assertTrue(e.getMessage().contains(NAME), e.getMessage());
        assertTrue(e.getMessage().contains("not authenticated"), e.getMessage());
        assertTrue(registry.agentSession(SESSION).isEmpty());
    }

    @Test
    void anErrorResultFromTheAgentFailsTheTurn() {
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
}
