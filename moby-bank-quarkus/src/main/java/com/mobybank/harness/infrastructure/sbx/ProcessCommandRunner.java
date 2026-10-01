package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.SandboxFailureException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs commands as operating-system processes. Arguments are passed as-is with no shell, so nothing a user typed can
 * be interpreted as shell syntax. Standard input is closed at once, and a command that outlives its timeout is killed.
 */
public final class ProcessCommandRunner implements CommandRunner {

    private static final int KEEP_CHARS = 64 * 1024;

    @Override
    public CommandResult run(List<String> command, Duration timeout, Consumer<String> onStdoutLine) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new SandboxFailureException("Could not start '" + command.get(0) + "': " + e.getMessage()
                    + " (is it installed and on the PATH?)", e);
        }
        closeQuietly(process);

        StringBuilder stderr = new StringBuilder();
        Thread stderrReader = Thread.ofVirtual().start(() -> drain(process, stderr));

        AtomicBoolean timedOut = new AtomicBoolean();
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                if (!process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    timedOut.set(true);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                // the command finished first
            }
        });

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                append(stdout, line);
                onStdoutLine.accept(line);
            }
            int exit = process.waitFor();
            stderrReader.join();
            if (timedOut.get()) {
                throw new SandboxFailureException("'" + String.join(" ", redacted(command)) + "' did not finish within "
                        + timeout);
            }
            return new CommandResult(exit, stdout.toString(), stderr.toString());
        } catch (IOException e) {
            if (timedOut.get()) {
                throw new SandboxFailureException("'" + String.join(" ", redacted(command)) + "' did not finish within "
                        + timeout, e);
            }
            throw new SandboxFailureException("Reading the output of '" + command.get(0) + "' failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new SandboxFailureException("Interrupted while waiting for '" + command.get(0) + "'", e);
        } finally {
            watchdog.interrupt();
        }
    }

    private static void drain(Process process, StringBuilder into) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                append(into, line);
            }
        } catch (IOException e) {
            // the process ended; nothing more to read
        }
    }

    private static void append(StringBuilder into, String line) {
        synchronized (into) {
            into.append(line).append('\n');
            if (into.length() > KEEP_CHARS) {
                into.delete(0, into.length() - KEEP_CHARS);
            }
        }
    }

    private static void closeQuietly(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            // stdin is already closed
        }
    }

    /** Long arguments (a whole prompt) are cut so error messages stay readable. */
    private static List<String> redacted(List<String> command) {
        return command.stream().map(arg -> arg.length() > 60 ? arg.substring(0, 57) + "..." : arg).toList();
    }
}
