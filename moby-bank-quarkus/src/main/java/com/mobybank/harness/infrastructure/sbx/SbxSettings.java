package com.mobybank.harness.infrastructure.sbx;

import java.time.Duration;
import java.util.List;

/**
 * Settings for the sbx adapters.
 *
 * @param agent         the sbx agent a sandbox is created for, and the command run inside it (for example claude)
 * @param agentArgs     extra arguments for every agent run, such as permission flags suited to a sandbox
 * @param workspaceDir  the working directory inside a sandbox; attached files go in its files/ folder
 * @param turnTimeout   how long one agent turn may take
 * @param moveTimeout   how long one {@code sbx move} may take
 * @param cloudTtl      how long a cloud sandbox lives before it times out; null means the service default
 */
public record SbxSettings(String agent, List<String> agentArgs, String workspaceDir, Duration turnTimeout,
                          Duration moveTimeout, Duration cloudTtl) {

    public SbxSettings {
        agentArgs = List.copyOf(agentArgs);
    }

    String filesDir() {
        return workspaceDir.endsWith("/") ? workspaceDir + "files" : workspaceDir + "/files";
    }
}
