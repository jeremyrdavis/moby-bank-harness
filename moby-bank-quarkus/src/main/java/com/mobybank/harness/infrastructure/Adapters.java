package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.infrastructure.graph.GraphAdapters;
import com.mobybank.harness.infrastructure.sbx.SbxAdapters;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Chooses the implementation of each external port from configuration, so the same build can run against fakes or
 * real sandboxes:
 *
 * <ul>
 *   <li>{@code harness.sandbox.mode}: {@code fake} or {@code sbx} (agent and transfer)</li>
 *   <li>{@code harness.documents.mode}: {@code fake} or {@code graph} (document catalog)</li>
 * </ul>
 *
 * The adapter producers are {@code @Startup}: an application-scoped bean is otherwise created on first use, which would
 * let a misspelt mode or missing credentials go unnoticed until the first request. Created at boot, a bad
 * configuration stops the app with a message naming the property.
 */
@ApplicationScoped
public class Adapters {

    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Produces
    @ApplicationScoped
    @Startup
    SandboxAgent sandboxAgent(
            @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode,
            @ConfigProperty(name = "harness.fake.agent-delay-ms", defaultValue = "1800") long agentDelayMs,
            @ConfigProperty(name = "harness.fake.cloud-expiry-turns", defaultValue = "0") int cloudExpiryTurns,
            SbxAdapters sbx) {
        Log.infof("Sandbox agent: harness.sandbox.mode=%s", mode);
        return selectAgent(mode, Duration.ofMillis(agentDelayMs), cloudExpiryTurns, sbx::agent);
    }

    @Produces
    @ApplicationScoped
    @Startup
    SandboxTransfer sandboxTransfer(
            @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode,
            @ConfigProperty(name = "harness.fake.move-packaging-ms", defaultValue = "1100") long packagingMs,
            @ConfigProperty(name = "harness.fake.move-transfer-ms", defaultValue = "1500") long transferMs,
            @ConfigProperty(name = "harness.fake.move-failure", defaultValue = "none") String moveFailure,
            SbxAdapters sbx) {
        Log.infof("Sandbox transfer: harness.sandbox.mode=%s", mode);
        return selectTransfer(mode, Duration.ofMillis(packagingMs), Duration.ofMillis(transferMs),
                FakeMoveFailure.parse(moveFailure), sbx::transfer);
    }

    @Produces
    @ApplicationScoped
    @Startup
    DocumentCatalog documentCatalog(
            @ConfigProperty(name = "harness.documents.mode", defaultValue = "fake") String mode,
            GraphAdapters graph) {
        Log.infof("Document catalog: harness.documents.mode=%s", mode);
        return selectCatalog(mode, graph::catalog);
    }

    static SandboxAgent selectAgent(String mode, Duration fakeDelay, Supplier<SandboxAgent> sbx) {
        return selectAgent(mode, fakeDelay, 0, sbx);
    }

    static SandboxAgent selectAgent(String mode, Duration fakeDelay, int fakeCloudExpiryTurns,
                                    Supplier<SandboxAgent> sbx) {
        return switch (mode) {
            case "fake" -> new FakeSandboxAgent(fakeDelay, fakeCloudExpiryTurns);
            case "sbx" -> sbx.get();
            default -> throw unsupported("harness.sandbox.mode", mode, "fake, sbx");
        };
    }

    static SandboxTransfer selectTransfer(String mode, Duration fakePackaging, Duration fakeTransferring,
                                          Supplier<SandboxTransfer> sbx) {
        return selectTransfer(mode, fakePackaging, fakeTransferring, FakeMoveFailure.NONE, sbx);
    }

    static SandboxTransfer selectTransfer(String mode, Duration fakePackaging, Duration fakeTransferring,
                                          FakeMoveFailure fakeFailure, Supplier<SandboxTransfer> sbx) {
        return switch (mode) {
            case "fake" -> new FakeSandboxTransfer(fakePackaging, fakeTransferring, fakeFailure);
            case "sbx" -> sbx.get();
            default -> throw unsupported("harness.sandbox.mode", mode, "fake, sbx");
        };
    }

    static DocumentCatalog selectCatalog(String mode, Supplier<DocumentCatalog> graph) {
        return switch (mode) {
            case "fake" -> new FakeDocumentCatalog();
            case "graph" -> graph.get();
            default -> throw unsupported("harness.documents.mode", mode, "fake, graph");
        };
    }

    private static IllegalStateException unsupported(String property, String mode, String supported) {
        Log.errorf("Unsupported %s=%s (supported: %s)", property, mode, supported);
        return new IllegalStateException("Unsupported " + property + "=" + mode + " (supported: " + supported + ")");
    }
}
