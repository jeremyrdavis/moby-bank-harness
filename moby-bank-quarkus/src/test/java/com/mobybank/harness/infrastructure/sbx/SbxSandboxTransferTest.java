package com.mobybank.harness.infrastructure.sbx;

import static com.mobybank.harness.infrastructure.sbx.FakeRunner.has;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.FileSource;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.infrastructure.sbx.FakeRunner.Response;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SbxSandboxTransferTest {

    private static final SessionId SESSION = SandboxRegistryTest.SESSION;

    private FakeRunner runner;
    private SandboxRegistry registry;
    private SbxSandboxTransfer transfer;
    private final List<MoveProgress> progress = new ArrayList<>();

    @BeforeEach
    void setUp() {
        runner = new FakeRunner();
        registry = new SandboxRegistry();
        SbxSettings settings = new SbxSettings("claude", List.of(), "/home/agent/workspace", Duration.ofMinutes(10),
                Duration.ofMinutes(30), Duration.ofHours(8));
        transfer = new SbxSandboxTransfer(new SbxCli(runner, "sbx", Duration.ofMinutes(2)), registry, settings);
    }

    private static MoveRequest request(Location from, Location to) {
        return new MoveRequest(SESSION, from, to, List.of(), List.of(new FileRef("a.pdf", FileSource.UPLOAD)));
    }

    @Test
    void movesALocalSandboxToTheCloudAndFindsTheNewOne() {
        registry.set(SESSION, new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g0", 0));
        runner.on(has("--cloud", "ls"), Response.ok("claude/other-thing", "claude/harness-0a1b2c3d4e5f-g1-k3j2x"));

        transfer.move(request(Location.LOCAL, Location.CLOUD), progress::add);

        assertEquals(List.of(
                "sbx move harness-0a1b2c3d4e5f-g0 --to cloud --name harness-0a1b2c3d4e5f-g1 --force --ttl 8h",
                "sbx --cloud ls -q"), runner.lines());
        assertEquals(Optional.of(new Handle(Location.CLOUD, "claude/harness-0a1b2c3d4e5f-g1-k3j2x", 1)),
                registry.current(SESSION));
        assertEquals(List.of(MoveStage.PACKAGING, MoveStage.TRANSFERRING),
                progress.stream().map(MoveProgress::stage).toList());
        assertEquals("Packaging the sandbox · 0 messages · 1 files", progress.get(0).detail());
        assertEquals(Duration.ofMinutes(30), runner.calls.get(0).timeout());
    }

    @Test
    void movesACloudSandboxBackToLocalUsingItsListedName() {
        registry.set(SESSION, new Handle(Location.CLOUD, "claude/harness-0a1b2c3d4e5f-g1-k3j2x", 1));
        runner.on(command -> command.contains("ls") && !command.contains("--cloud"),
                Response.ok("harness-0a1b2c3d4e5f-g2"));

        transfer.move(request(Location.CLOUD, Location.LOCAL), progress::add);

        assertEquals(List.of(
                "sbx move claude/harness-0a1b2c3d4e5f-g1-k3j2x --to local --name harness-0a1b2c3d4e5f-g2 --force",
                "sbx ls -q"), runner.lines());
        assertEquals(Optional.of(new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g2", 2)), registry.current(SESSION));
    }

    @Test
    void aSessionThatNeverStartedASandboxHasNothingToMove() {
        transfer.move(request(Location.LOCAL, Location.CLOUD), progress::add);

        assertEquals(List.of("sbx ls -q"), runner.lines(), "only looked for a sandbox, never moved anything");
        assertEquals(1, progress.size());
        assertTrue(progress.get(0).detail().startsWith("Nothing to package yet"), progress.get(0).detail());
        assertTrue(registry.current(SESSION).isEmpty());
    }

    @Test
    void theSourceIsFoundAfterARestartFromTheListing() {
        runner.on(command -> command.contains("ls") && !command.contains("--cloud"),
                Response.ok("harness-0a1b2c3d4e5f-g0", "harness-0a1b2c3d4e5f-g3", "harness-ffffffffffff-g7"));
        runner.on(has("--cloud", "ls"), Response.ok("claude/harness-0a1b2c3d4e5f-g4-zz"));

        transfer.move(request(Location.LOCAL, Location.CLOUD), progress::add);

        assertTrue(runner.lines().get(1).startsWith("sbx move harness-0a1b2c3d4e5f-g3 --to cloud --name "
                + "harness-0a1b2c3d4e5f-g4 "), runner.lines().get(1));
        assertEquals(4, registry.current(SESSION).orElseThrow().generation());
    }

    @Test
    void otherSessionsAndOlderGenerationsAtTheDestinationAreNotMistakenForTheNewSandbox() {
        registry.set(SESSION, new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g1", 1));
        runner.on(has("--cloud", "ls"), Response.ok("claude/harness-0a1b2c3d4e5f-g1-old", "claude/harness-ffffffffffff-g2-x"));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> transfer.move(request(Location.LOCAL, Location.CLOUD), progress::add));

        assertTrue(e.getMessage().contains("harness-0a1b2c3d4e5f-g2"), e.getMessage());
        assertEquals(Optional.of(new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g1", 1)), registry.current(SESSION),
                "the session stays registered where it was");
    }

    @Test
    void aFailedMoveCommandFailsTheTransferAndKeepsTheSessionWhereItWas() {
        Handle before = new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g0", 0);
        registry.set(SESSION, before);
        runner.on(has("move"), Response.fail(1, "Error: sign in to Docker first"));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> transfer.move(request(Location.LOCAL, Location.CLOUD), progress::add));

        assertTrue(e.getMessage().contains("sign in to Docker first"), e.getMessage());
        assertEquals(Optional.of(before), registry.current(SESSION));
        assertEquals(1, progress.size(), "it never got to the transferring stage");
    }

    @Test
    void movingThereAndBackAdvancesTheGenerationEachTime() {
        registry.set(SESSION, new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g0", 0));
        runner.on(has("--cloud", "ls"), Response.ok("claude/harness-0a1b2c3d4e5f-g1-aa"));
        transfer.move(request(Location.LOCAL, Location.CLOUD), progress::add);

        runner.on(command -> command.contains("ls") && !command.contains("--cloud"), Response.ok("harness-0a1b2c3d4e5f-g2"));
        transfer.move(request(Location.CLOUD, Location.LOCAL), progress::add);

        assertEquals(Optional.of(new Handle(Location.LOCAL, "harness-0a1b2c3d4e5f-g2", 2)), registry.current(SESSION));
        assertTrue(runner.linesWith("move").get(1).contains("claude/harness-0a1b2c3d4e5f-g1-aa"));
    }
}
