package com.mobybank.harness.domain;

/** Where a session's agent runs: a sandbox on the analyst's machine, or a sandbox in the cloud. */
public enum Location {
    LOCAL,
    CLOUD;

    public Location other() {
        return this == LOCAL ? CLOUD : LOCAL;
    }
}
