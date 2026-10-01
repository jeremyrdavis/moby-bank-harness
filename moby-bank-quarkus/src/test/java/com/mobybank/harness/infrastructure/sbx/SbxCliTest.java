package com.mobybank.harness.infrastructure.sbx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.infrastructure.sbx.FakeRunner.Response;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SbxCliTest {

    private final FakeRunner runner = new FakeRunner();
    private final SbxCli cli = new SbxCli(runner, "sbx", Duration.ofSeconds(30));

    @Test
    void createsALocalSandboxWithoutAWorkspace() {
        cli.create(Location.LOCAL, "harness-abc-g0", "claude", Duration.ofHours(8));
        assertEquals(List.of("sbx create --name harness-abc-g0 claude"), runner.lines(), "no --ttl for local sandboxes");
    }

    @Test
    void createsACloudSandboxWithTheCloudFlagAndATimeToLive() {
        cli.create(Location.CLOUD, "harness-abc-g0", "claude", Duration.ofHours(8));
        assertEquals(List.of("sbx --cloud create --name harness-abc-g0 --ttl 8h claude"), runner.lines());
    }

    @Test
    void aCloudSandboxWithoutATtlUsesTheServiceDefault() {
        cli.create(Location.CLOUD, "harness-abc-g0", "claude", null);
        assertEquals(List.of("sbx --cloud create --name harness-abc-g0 claude"), runner.lines());
    }

    @Test
    void listsSandboxNamesOneUnstrippedLinePerName() {
        runner.on(FakeRunner.has("ls"), Response.ok("claude/harness-abc-g1-xy", "", "  other  "));
        assertEquals(List.of("claude/harness-abc-g1-xy", "other"), cli.listNames(Location.CLOUD));
        assertEquals(List.of("sbx --cloud ls -q"), runner.lines());
        cli.listNames(Location.LOCAL);
        assertEquals("sbx ls -q", runner.lines().get(1));
    }

    @Test
    void copiesAFileIntoTheSandboxByPath() {
        cli.copyIn(Location.LOCAL, Path.of("/tmp/stage/a.pdf"), "sb", "/home/agent/workspace/files/a.pdf");
        assertEquals(List.of("sbx cp /tmp/stage/a.pdf sb:/home/agent/workspace/files/a.pdf"), runner.lines());

        cli.copyIn(Location.CLOUD, Path.of("/tmp/stage/a.pdf"), "sb", "/home/agent/workspace/files/a.pdf");
        assertEquals("sbx --cloud cp /tmp/stage/a.pdf sb:/home/agent/workspace/files/a.pdf", runner.lines().get(1));
    }

    @Test
    void makesDirectoriesAndExecsInsideTheSandbox() {
        cli.makeDirectory(Location.CLOUD, "sb", "/home/agent/workspace/files");
        assertEquals(List.of("sbx --cloud exec sb mkdir -p /home/agent/workspace/files"), runner.lines());

        cli.exec(Location.LOCAL, "sb", List.of("claude", "-p", "hello world"), Duration.ofMinutes(5), line -> { });
        assertEquals("sbx exec sb claude -p hello world", runner.lines().get(1));
        assertEquals(Duration.ofMinutes(5), runner.calls.get(1).timeout());
    }

    @Test
    void promptsArePassedAsOneArgumentNotSplit() {
        cli.exec(Location.LOCAL, "sb", List.of("claude", "-p", "two words; and $(danger)"), Duration.ofMinutes(1), l -> { });
        List<String> command = runner.calls.get(0).command();
        assertEquals(List.of("sbx", "exec", "sb", "claude", "-p", "two words; and $(danger)"), command);
    }

    @Test
    void movesToTheCloudWithoutTheCloudFlagButWithForceAndTtl() {
        cli.move("harness-abc-g0", Location.CLOUD, "harness-abc-g1", Duration.ofHours(8), Duration.ofMinutes(30));
        assertEquals(List.of("sbx move harness-abc-g0 --to cloud --name harness-abc-g1 --force --ttl 8h"), runner.lines());
        assertEquals(Duration.ofMinutes(30), runner.calls.get(0).timeout());
    }

    @Test
    void movesBackToLocalWithoutATtl() {
        cli.move("claude/harness-abc-g1-xy", Location.LOCAL, "harness-abc-g2", Duration.ofHours(8), Duration.ofMinutes(30));
        assertEquals(List.of("sbx move claude/harness-abc-g1-xy --to local --name harness-abc-g2 --force"), runner.lines());
    }

    @Test
    void aFailingCommandBecomesASandboxFailureWithTheReason() {
        runner.on(FakeRunner.has("create"), Response.fail(1, "Error: name already in use"));
        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> cli.create(Location.LOCAL, "harness-abc-g0", "claude", null));
        assertTrue(e.getMessage().contains("harness-abc-g0"), e.getMessage());
        assertTrue(e.getMessage().contains("exit 1"), e.getMessage());
        assertTrue(e.getMessage().contains("name already in use"), e.getMessage());
    }

    @Test
    void durationsAreRenderedTheWaySbxReadsThem() {
        assertEquals("8h", SbxCli.formatDuration(Duration.ofHours(8)));
        assertEquals("90m", SbxCli.formatDuration(Duration.ofMinutes(90)));
        assertEquals("45s", SbxCli.formatDuration(Duration.ofSeconds(45)));
    }
}
