package com.mobybank.harness.infrastructure.sbx;

import static com.mobybank.harness.infrastructure.sbx.FakeRunner.has;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.infrastructure.sbx.FakeRunner.Response;
import com.mobybank.harness.infrastructure.sbx.SbxPreflight.Report;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SbxPreflightTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-15T09:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final String DIAGNOSE_OK = "INSTALLATION\n  ✓ CLI binary — found\n\n  14 passed";

    private FakeRunner runner;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        runner = new FakeRunner();
        clock = new TestClock();
    }

    private SbxPreflight preflight(boolean probe) {
        SbxSettings settings = new SbxSettings("claude", List.of("--dangerously-skip-permissions"),
                "/home/agent/workspace", Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(8), "stop");
        return new SbxPreflight(new SbxCli(runner, "sbx", Duration.ofMinutes(2)), settings, Duration.ofMinutes(5), probe,
                clock);
    }

    /** A host where everything works: the version, both diagnoses, and a secret in each location. */
    private void healthyHost() {
        runner.on(has("--version"), Response.ok("v0.48.0 0123abc"));
        runner.on(has("diagnose"), Response.ok(DIAGNOSE_OK.split("\n")));
        runner.on(has("--cloud", "secret", "ls"),
                Response.ok("SCOPE   SERVICE    TYPE            SECRET   CREATED", "global  anthropic  oauth_refresh  ***      1d"));
        runner.on(has("secret", "ls"), Response.ok("SCOPE   TYPE   NAME        SECRET",
                "global  llm    anthropic   (oauth configured)"));
    }

    @Test
    void aHealthyHostIsReadyWithNoWarnings() {
        healthyHost();

        Report report = preflight(false).check();

        assertTrue(report.ready());
        assertEquals(List.of(), report.warnings());
        assertEquals("v0.48.0 0123abc", report.details().get("sbx"));
        assertEquals("14 passed", report.details().get("local diagnose"));
        assertEquals("14 passed", report.details().get("cloud diagnose"));
        assertEquals("anthropic is set", report.details().get("local secret"));
        assertEquals("anthropic is set", report.details().get("cloud secret"));
        assertEquals(List.of("sbx --version", "sbx diagnose", "sbx --cloud diagnose", "sbx secret ls",
                "sbx --cloud secret ls"), runner.lines());
    }

    @Test
    void aMissingSbxMakesTheHostNotReadyAndSaysSo() {
        runner.on(has("--version"), command -> {
            throw new SandboxFailureException("Could not start 'sbx': no such file (is it installed and on the PATH?)");
        });

        Report report = preflight(false).check();

        assertFalse(report.ready());
        assertTrue(report.details().get("sbx").startsWith("not available: Could not start 'sbx'"), report.details().toString());
        assertEquals(1, report.warnings().size());
        assertEquals(List.of("sbx --version"), runner.lines(), "nothing else is attempted");
    }

    @Test
    void anOldSbxWarnsThatTheCloudNeedsANewerOne() {
        healthyHost();
        runner.calls.clear();
        FakeRunner old = new FakeRunner();
        old.on(has("--version"), Response.ok("v0.44.9 abc"));
        old.on(has("diagnose"), Response.ok("  1 passed"));
        old.on(has("secret", "ls"), Response.ok("global anthropic"));
        SbxSettings settings = new SbxSettings("claude", List.of(), "/w", Duration.ofMinutes(1), Duration.ofMinutes(1),
                Duration.ofHours(1), "stop");
        Report report = new SbxPreflight(new SbxCli(old, "sbx", Duration.ofMinutes(1)), settings, Duration.ofMinutes(5),
                false, clock).check();

        assertTrue(report.ready(), "local sandboxes still work");
        assertTrue(report.warnings().get(0).contains("older than 0.45.0"), report.warnings().toString());
    }

    @Test
    void aMissingCloudSecretIsAWarningThatNamesTheCommandNotAFailure() {
        healthyHost();
        FakeRunner other = new FakeRunner();
        runner = other; // replace the cloud secret listing with one that has no anthropic row
        other.on(has("--version"), Response.ok("v0.48.0"));
        other.on(has("diagnose"), Response.ok("  14 passed"));
        other.on(has("--cloud", "secret", "ls"), Response.ok("SCOPE SERVICE TYPE SECRET CREATED"));
        other.on(has("secret", "ls"), Response.ok("global llm anthropic (oauth configured)"));

        Report report = preflight(false).check();

        assertTrue(report.ready());
        assertEquals("anthropic is not set", report.details().get("cloud secret"));
        assertEquals(1, report.warnings().size());
        assertTrue(report.warnings().get(0).contains("`sbx --cloud secret set anthropic --oauth`"), report.warnings().get(0));
        assertTrue(report.warnings().get(0).contains("start a new sandbox"));
    }

    @Test
    void aFailingLocalDiagnoseMeansNotReadyButAFailingCloudDiagnoseOnlyWarns() {
        runner.on(has("--version"), Response.ok("v0.48.0"));
        runner.on(has("--cloud", "diagnose"), Response.fail(1, "error: not signed in to the cloud"));
        runner.on(has("diagnose"), Response.fail(1, "✗ Daemon — not running\n  Cannot connect to the sbx daemon"));
        runner.on(has("secret", "ls"), Response.ok("global anthropic"));

        Report report = preflight(false).check();

        assertFalse(report.ready());
        assertTrue(report.details().get("local diagnose").startsWith("failed: The Docker Sandboxes daemon is not running"),
                report.details().get("local diagnose"));
        assertTrue(report.details().get("cloud diagnose").startsWith("failed: Not signed in"), report.details().get("cloud diagnose"));
        assertEquals(2, report.warnings().size());
    }

    @Test
    void theReportIsCachedUntilItIsOld() {
        healthyHost();
        SbxPreflight preflight = preflight(false);

        Report first = preflight.report();
        int calls = runner.calls.size();
        clock.now = clock.now.plus(Duration.ofMinutes(4));
        assertSame(first, preflight.report());
        assertEquals(calls, runner.calls.size(), "no sbx command ran for a cached answer");

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        preflight.report();
        assertEquals(calls * 2, runner.calls.size(), "checked again once it was older than the cache time");
    }

    @Test
    void theProbeRunsOneTrivialTurnInAThrowawayCloudSandboxAndRemovesIt() {
        healthyHost();
        runner.on(command -> command.contains("exec") && command.contains("--output-format"), Response.ok(
                "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"s\"}",
                "{\"type\":\"result\",\"is_error\":false,\"result\":\"OK\",\"session_id\":\"s\"}"));
        SbxPreflight preflight = preflight(true);

        Report report = preflight.check();

        assertEquals("ok", report.details().get("cloud probe"));
        assertEquals(List.of(), report.warnings());
        List<String> creates = runner.linesWith("create");
        assertEquals(1, creates.size());
        assertTrue(creates.get(0).matches("sbx --cloud create --name harness-probe-[0-9a-f]{8} --ttl 1h --on-timeout delete claude"),
                creates.get(0));
        String name = creates.get(0).split(" ")[4];
        assertEquals(List.of("sbx --cloud rm " + name), runner.linesWith("rm"), "the throwaway sandbox is removed");

        preflight.check();
        assertEquals(1, runner.linesWith("create").size(), "the probe runs once per start, not on every check");
    }

    @Test
    void aProbeThatFindsARejectedCredentialWarnsWithTheHintAndStillRemovesTheSandbox() {
        healthyHost();
        runner.on(command -> command.contains("exec") && command.contains("--output-format"), Response.ok(
                "{\"type\":\"result\",\"is_error\":true,\"result\":\"Invalid API key · Fix external API key\"}"));

        Report report = preflight(true).check();

        assertTrue(report.details().get("cloud probe").startsWith("The agent's credential was rejected (401)"),
                report.details().get("cloud probe"));
        assertEquals(1, report.warnings().size());
        assertEquals(1, runner.linesWith("rm").size());
    }

    @Test
    void aProbeSandboxThatCannotBeRemovedDoesNotFailTheCheck() {
        healthyHost();
        runner.on(command -> command.contains("exec") && command.contains("--output-format"), Response.ok(
                "{\"type\":\"result\",\"is_error\":false,\"result\":\"OK\"}"));
        runner.on(has("rm"), Response.fail(1, "error: confirmation required"));

        Report report = preflight(true).check();

        assertEquals("ok", report.details().get("cloud probe"));
    }

    @Test
    void versionsAreComparedNumerically() {
        int[] minimum = {0, 45, 0};
        assertTrue(SbxPreflight.atLeast("v0.45.0 abc", minimum));
        assertTrue(SbxPreflight.atLeast("sbx version 0.48.0", minimum));
        assertTrue(SbxPreflight.atLeast("v1.0.0", minimum));
        assertTrue(SbxPreflight.atLeast("v0.100.0", minimum), "0.100 is newer than 0.45");
        assertFalse(SbxPreflight.atLeast("v0.44.9", minimum));
        assertFalse(SbxPreflight.atLeast("v0.9.0", minimum));
        assertTrue(SbxPreflight.atLeast("dev build", minimum), "an unreadable version is not a complaint");
    }

    @Test
    void theCliAsksForTheVersionTheDiagnosisTheSecretsAndRemovalWithTheRightArguments() {
        SbxCli cli = new SbxCli(runner, "sbx", Duration.ofSeconds(30));
        runner.on(has("--version"), Response.ok("", "v0.48.0 abc"));
        assertEquals("v0.48.0 abc", cli.version());
        cli.diagnose(Location.CLOUD);
        cli.secrets(Location.LOCAL);
        cli.remove(Location.CLOUD, "x");
        assertEquals(List.of("sbx --version", "sbx --cloud diagnose", "sbx secret ls", "sbx --cloud rm x"), runner.lines());
    }
}
