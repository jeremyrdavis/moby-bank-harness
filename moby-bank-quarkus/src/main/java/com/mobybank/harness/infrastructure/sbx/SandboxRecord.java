package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;

/**
 * What is known about one session's sandbox: the sandbox itself (null when none), the agent's own conversation id
 * inside it (null when none), and whether the sandbox still needs the session's shared files copied in.
 */
public record SandboxRecord(Handle handle, String agentSession, boolean awaitingFiles) {

    /** Nothing known yet. */
    public static final SandboxRecord NONE = new SandboxRecord(null, null, false);

    SandboxRecord withHandle(Handle newHandle) {
        return new SandboxRecord(newHandle, agentSession, awaitingFiles);
    }

    SandboxRecord withAgentSession(String id) {
        return new SandboxRecord(handle, id, awaitingFiles);
    }

    SandboxRecord withAwaitingFiles(boolean awaiting) {
        return new SandboxRecord(handle, agentSession, awaiting);
    }
}
