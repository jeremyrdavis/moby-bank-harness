package com.mobybank.harness.domain;

/** The two kinds of {@link Step} in an agent's trace: reading a file, or running a calculation. */
public enum StepKind {
    /** The agent read a file. */
    READ,
    /** The agent ran a calculation. */
    COMPUTE
}
