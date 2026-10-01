package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.SandboxFailureException;
import java.time.Duration;

/** Sleeping that the fake adapters use to imitate real work. */
final class Pauses {

    private Pauses() {
    }

    static void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxFailureException("interrupted while waiting for the sandbox", e);
        }
    }
}
