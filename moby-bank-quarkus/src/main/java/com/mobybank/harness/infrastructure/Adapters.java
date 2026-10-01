package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.infrastructure.graph.GraphAdapters;
import com.mobybank.harness.infrastructure.sbx.SbxAdapters;
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
 * Other modes are added with their adapters and fail fast at startup until then.
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
    SandboxAgent sandboxAgent(
            @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode,
            @ConfigProperty(name = "harness.fake.agent-delay-ms", defaultValue = "1800") long agentDelayMs,
            SbxAdapters sbx) {
        return selectAgent(mode, Duration.ofMillis(agentDelayMs), sbx::agent);
    }

    @Produces
    @ApplicationScoped
    SandboxTransfer sandboxTransfer(
            @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode,
            @ConfigProperty(name = "harness.fake.move-packaging-ms", defaultValue = "1100") long packagingMs,
            @ConfigProperty(name = "harness.fake.move-transfer-ms", defaultValue = "1500") long transferMs,
            SbxAdapters sbx) {
        return selectTransfer(mode, Duration.ofMillis(packagingMs), Duration.ofMillis(transferMs), sbx::transfer);
    }

    @Produces
    @ApplicationScoped
    DocumentCatalog documentCatalog(
            @ConfigProperty(name = "harness.documents.mode", defaultValue = "fake") String mode,
            GraphAdapters graph) {
        return selectCatalog(mode, graph::catalog);
    }

    static SandboxAgent selectAgent(String mode, Duration fakeDelay, Supplier<SandboxAgent> sbx) {
        return switch (mode) {
            case "fake" -> new FakeSandboxAgent(fakeDelay);
            case "sbx" -> sbx.get();
            default -> throw unsupported("harness.sandbox.mode", mode, "fake, sbx");
        };
    }

    static SandboxTransfer selectTransfer(String mode, Duration fakePackaging, Duration fakeTransferring,
                                          Supplier<SandboxTransfer> sbx) {
        return switch (mode) {
            case "fake" -> new FakeSandboxTransfer(fakePackaging, fakeTransferring);
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
        return new IllegalStateException("Unsupported " + property + "=" + mode + " (supported: " + supported + ")");
    }
}
