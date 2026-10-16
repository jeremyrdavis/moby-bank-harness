package com.mobybank.harness.infrastructure.sbx;

import static com.mobybank.harness.infrastructure.sbx.FakeRunner.has;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.infrastructure.sbx.FakeRunner.Response;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SbxErrorsTest {

    // The first two are the strings captured on a real host (see verification.md); the rest follow Docker's docs.
    private static final String[][] CASES = {
            {"error: sandbox 'no-such-sandbox' not found\n  try: sbx --cloud ls", "no longer exists"},
            {"Invalid API key · Fix external API key", "credential was rejected"},
            {"API Error: 401 authentication_error", "credential was rejected"},
            {"Error: not authenticated; run sbx secret set anthropic", "has no credential"},
            {"Error: sign in to Docker first", "Not signed in"},
            {"error: platform mismatch: source linux/arm64, destination linux/amd64", "different platforms"},
            {"write /var/lib: no space left on device", "out of disk space"},
            {"sandbox size exceeds the largest cloud shape", "larger than the biggest cloud size"},
            {"Cannot connect to the sbx daemon", "daemon is not running"},
    };

    @Test
    void recognisesTheFailuresThatNeedAnInstruction() {
        for (String[] testCase : CASES) {
            String hint = SbxErrors.hint(testCase[0], null).orElseThrow(
                    () -> new AssertionError("not recognised: " + testCase[0]));
            assertTrue(hint.contains(testCase[1]), testCase[0] + " -> " + hint);
        }
    }

    @Test
    void anUnknownFailureHasNoHint() {
        assertEquals(Optional.empty(), SbxErrors.hint("something unexpected happened", Location.CLOUD));
        assertEquals(Optional.empty(), SbxErrors.hint("", Location.CLOUD));
        assertEquals(Optional.empty(), SbxErrors.hint(null, Location.CLOUD));
        assertEquals(Optional.empty(), SbxErrors.hint("copied 401 bytes", Location.CLOUD), "a bare 401 is not a status");
    }

    @Test
    void theCredentialAdviceNamesTheCommandForTheLocation() {
        String rejected = "Invalid API key · Fix external API key";
        assertTrue(SbxErrors.hint(rejected, Location.CLOUD).orElseThrow().contains("`sbx --cloud secret set anthropic --oauth`"));
        assertTrue(SbxErrors.hint(rejected, Location.LOCAL).orElseThrow().contains("`sbx secret set anthropic`"));
        assertTrue(SbxErrors.hint(rejected, Location.CLOUD).orElseThrow().contains("then start a new sandbox"));
    }

    @Test
    void aFailingCommandKeepsTheRawTextBesideTheHint() {
        FakeRunner runner = new FakeRunner();
        runner.on(has("exec"), Response.fail(1, "error: sandbox 'moby-bank-abc-g0' not found\n  try: sbx ls"));
        SbxCli cli = new SbxCli(runner, "sbx", Duration.ofSeconds(30));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> cli.makeDirectory(Location.LOCAL, "moby-bank-abc-g0", "/home/agent/workspace/files"));

        assertTrue(e.getMessage().startsWith("Creating /home/agent/workspace/files in moby-bank-abc-g0 failed: The sandbox no longer exists"),
                e.getMessage());
        assertTrue(e.getMessage().contains("(exit 1: error: sandbox 'moby-bank-abc-g0' not found"), e.getMessage());
    }

    @Test
    void anUnrecognisedFailureKeepsTheOriginalForm() {
        FakeRunner runner = new FakeRunner();
        runner.on(has("create"), Response.fail(2, "kaboom"));
        SbxCli cli = new SbxCli(runner, "sbx", Duration.ofSeconds(30));

        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> cli.create(Location.LOCAL, "moby-bank-abc-g0", "claude", null, null));

        assertEquals("Creating sandbox moby-bank-abc-g0 failed (exit 2: kaboom)", e.getMessage());
    }
}
