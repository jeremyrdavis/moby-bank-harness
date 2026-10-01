package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.SandboxFailureException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Runs an external command without a shell. A seam so adapters can be tested without the real tool. */
public interface CommandRunner {

    /**
     * Runs the command and waits for it to finish.
     *
     * @param onStdoutLine called for each line of standard output as it arrives
     * @return the result, even when the exit code is not zero
     * @throws SandboxFailureException if the command cannot start or does not finish within the timeout
     */
    CommandResult run(List<String> command, Duration timeout, Consumer<String> onStdoutLine);

    default CommandResult run(List<String> command, Duration timeout) {
        return run(command, timeout, line -> { });
    }
}
