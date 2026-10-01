package com.mobybank.harness.infrastructure.sbx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.SandboxFailureException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs real (POSIX) processes. */
class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void stdoutLinesAreStreamedInOrderAndAlsoReturned() {
        List<String> seen = new ArrayList<>();
        CommandResult result = runner.run(List.of("sh", "-c", "echo one; echo two; echo three"),
                Duration.ofSeconds(10), seen::add);

        assertTrue(result.succeeded());
        assertEquals(List.of("one", "two", "three"), seen);
        assertEquals("one\ntwo\nthree\n", result.stdout());
    }

    @Test
    void aNonZeroExitIsReportedNotThrown() {
        CommandResult result = runner.run(List.of("sh", "-c", "echo out; echo problem >&2; exit 3"), Duration.ofSeconds(10));

        assertEquals(3, result.exitCode());
        assertEquals("problem\n", result.stderr());
        assertEquals("problem", result.tail());
    }

    @Test
    void theTailFallsBackToStdoutWhenStderrIsEmpty() {
        CommandResult result = runner.run(List.of("sh", "-c", "echo only-stdout; exit 1"), Duration.ofSeconds(10));
        assertEquals("only-stdout", result.tail());
    }

    @Test
    void standardInputIsClosedSoCommandsThatReadItDoNotHang() {
        CommandResult result = runner.run(List.of("cat"), Duration.ofSeconds(10));
        assertTrue(result.succeeded());
        assertEquals("", result.stdout());
    }

    @Test
    void argumentsAreNeverInterpretedByAShell() {
        CommandResult result = runner.run(List.of("echo", "$HOME; `id` && rm -rf /tmp/nothing"), Duration.ofSeconds(10));
        assertEquals("$HOME; `id` && rm -rf /tmp/nothing\n", result.stdout());
    }

    @Test
    void aCommandThatRunsTooLongIsKilledAndReported() {
        long start = System.nanoTime();
        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> runner.run(List.of("sleep", "30"), Duration.ofMillis(300)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(e.getMessage().contains("did not finish within"), e.getMessage());
        assertTrue(elapsedMs < 10_000, "should have been killed near the timeout, took " + elapsedMs + " ms");
    }

    @Test
    void aMissingProgramSaysSoAndHintsAtThePath() {
        SandboxFailureException e = assertThrows(SandboxFailureException.class,
                () -> runner.run(List.of("definitely-not-a-real-program-xyz"), Duration.ofSeconds(5)));
        assertTrue(e.getMessage().contains("definitely-not-a-real-program-xyz"), e.getMessage());
        assertTrue(e.getMessage().contains("installed"), e.getMessage());
    }

    @Test
    void veryLongOutputIsCappedInTheReturnedResultButFullyStreamed() {
        List<String> seen = new ArrayList<>();
        CommandResult result = runner.run(List.of("sh", "-c", "yes x | head -n 100000"), Duration.ofSeconds(20), seen::add);

        assertEquals(100_000, seen.size());
        assertTrue(result.stdout().length() <= 64 * 1024 + 2, "kept " + result.stdout().length());
    }
}
