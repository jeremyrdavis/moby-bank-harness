package com.mobybank.harness.domain;

import java.util.Objects;

/** One entry in an agent's trace: a file it read or a calculation it ran. */
public record Step(StepKind kind, String label) {

    public Step {
        Objects.requireNonNull(kind, "step kind required");
        Objects.requireNonNull(label, "step label required");
        label = label.strip();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("step label must not be blank");
        }
    }
}
