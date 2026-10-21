package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds the sbx-backed adapters from configuration ({@code harness.sbx.*}). Nothing here touches {@code sbx} until a
 * turn or move runs, so the app starts fine without it installed; the adapters are only used when
 * {@code harness.sandbox.mode=sbx}.
 */
@ApplicationScoped
public class SbxAdapters {

    /**
     * Headless runs cannot answer permission prompts, and the same flags must work in both locations: the model is
     * pinned so local and cloud agree, and {@code --strict-mcp-config} keeps a host's claude.ai connectors out of a
     * local sandbox (a cloud one has none).
     */
    static final String DEFAULT_AGENT_ARGS = "--dangerously-skip-permissions --model claude-opus-5-5 --strict-mcp-config";

    private final SbxCli cli;
    private final SandboxRegistry registry;
    private final UploadStore uploads;
    private final SbxSettings settings;
    private final SbxPreflight preflight;

    @Inject
    public SbxAdapters(
            SandboxRegistry registry,
            UploadStore uploads,
            @ConfigProperty(name = "harness.sbx.binary", defaultValue = "sbx") String binary,
            @ConfigProperty(name = "harness.sbx.agent", defaultValue = "claude") String agent,
            @ConfigProperty(name = "harness.sbx.agent-args", defaultValue = DEFAULT_AGENT_ARGS) String agentArgs,
            @ConfigProperty(name = "harness.sbx.workspace-dir", defaultValue = "/home/agent/workspace") String workspaceDir,
            @ConfigProperty(name = "harness.sbx.command-timeout", defaultValue = "2m") Duration commandTimeout,
            @ConfigProperty(name = "harness.sbx.turn-timeout", defaultValue = "10m") Duration turnTimeout,
            @ConfigProperty(name = "harness.sbx.move-timeout", defaultValue = "1h") Duration moveTimeout,
            @ConfigProperty(name = "harness.sbx.cloud-ttl", defaultValue = "8h") Duration cloudTtl,
            @ConfigProperty(name = "harness.sbx.cloud-on-timeout", defaultValue = "stop") String cloudOnTimeout,
            @ConfigProperty(name = "harness.sbx.preflight-cache", defaultValue = "5m") Duration preflightCache,
            @ConfigProperty(name = "harness.sbx.preflight-probe", defaultValue = "false") boolean preflightProbe) {
        this.cli = new SbxCli(new ProcessCommandRunner(), binary, commandTimeout);
        this.registry = registry;
        this.uploads = uploads;
        this.settings = new SbxSettings(agent, agentArgs.isBlank() ? java.util.List.of()
                : Arrays.asList(agentArgs.trim().split("\\s+")), workspaceDir, turnTimeout, moveTimeout,
                cloudTtl, cloudOnTimeout);
        this.preflight = new SbxPreflight(cli, settings, preflightCache, preflightProbe, java.time.Clock.systemUTC());
    }

    /** The host checks behind {@code /q/health/ready} and the warning logged at startup. */
    public SbxPreflight preflight() {
        return preflight;
    }

    public SandboxAgent agent() {
        return new SbxSandboxAgent(cli, registry, uploads, settings);
    }

    public SandboxTransfer transfer() {
        return new SbxSandboxTransfer(cli, registry, settings);
    }
}
