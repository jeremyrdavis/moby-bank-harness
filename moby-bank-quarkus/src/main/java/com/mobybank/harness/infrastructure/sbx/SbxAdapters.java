package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds the sbx-backed adapters from configuration ({@code harness.sbx.*}). Nothing here touches {@code sbx} until a
 * turn or move runs, so the app starts fine without it installed; the adapters are only used when
 * {@code harness.sandbox.mode=sbx}.
 */
@ApplicationScoped
public class SbxAdapters {

    private final SbxCli cli;
    private final SandboxRegistry registry;
    private final UploadStore uploads;
    private final DocumentCatalog catalog;
    private final SbxSettings settings;

    @Inject
    public SbxAdapters(
            SandboxRegistry registry,
            UploadStore uploads,
            DocumentCatalog catalog,
            @ConfigProperty(name = "harness.sbx.binary", defaultValue = "sbx") String binary,
            @ConfigProperty(name = "harness.sbx.agent", defaultValue = "claude") String agent,
            @ConfigProperty(name = "harness.sbx.agent-args", defaultValue = "--dangerously-skip-permissions") String agentArgs,
            @ConfigProperty(name = "harness.sbx.workspace-dir", defaultValue = "/home/agent/workspace") String workspaceDir,
            @ConfigProperty(name = "harness.sbx.command-timeout", defaultValue = "2m") Duration commandTimeout,
            @ConfigProperty(name = "harness.sbx.turn-timeout", defaultValue = "10m") Duration turnTimeout,
            @ConfigProperty(name = "harness.sbx.move-timeout", defaultValue = "30m") Duration moveTimeout,
            @ConfigProperty(name = "harness.sbx.cloud-ttl") Optional<Duration> cloudTtl) {
        this.cli = new SbxCli(new ProcessCommandRunner(), binary, commandTimeout);
        this.registry = registry;
        this.uploads = uploads;
        this.catalog = catalog;
        this.settings = new SbxSettings(agent, agentArgs.isBlank() ? java.util.List.of()
                : Arrays.asList(agentArgs.trim().split("\\s+")), workspaceDir, turnTimeout, moveTimeout,
                cloudTtl.orElse(null));
    }

    public SandboxAgent agent() {
        return new SbxSandboxAgent(cli, registry, uploads, catalog, settings);
    }

    public SandboxTransfer transfer() {
        return new SbxSandboxTransfer(cli, registry, settings);
    }
}
