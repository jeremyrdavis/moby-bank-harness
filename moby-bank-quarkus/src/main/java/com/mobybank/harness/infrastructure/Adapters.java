package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxTransfer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Chooses the implementation of each external port from configuration, so the same build can run against fakes or
 * real sandboxes:
 *
 * <ul>
 *   <li>{@code harness.sandbox.mode}: {@code fake} (agent and transfer)</li>
 *   <li>{@code harness.documents.mode}: {@code fake} (document catalog)</li>
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
            @ConfigProperty(name = "harness.fake.agent-delay-ms", defaultValue = "1800") long agentDelayMs) {
        return selectAgent(mode, Duration.ofMillis(agentDelayMs));
    }

    @Produces
    @ApplicationScoped
    SandboxTransfer sandboxTransfer(
            @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode,
            @ConfigProperty(name = "harness.fake.move-packaging-ms", defaultValue = "1100") long packagingMs,
            @ConfigProperty(name = "harness.fake.move-transfer-ms", defaultValue = "1500") long transferMs) {
        return selectTransfer(mode, Duration.ofMillis(packagingMs), Duration.ofMillis(transferMs));
    }

    @Produces
    @ApplicationScoped
    DocumentCatalog documentCatalog(
            @ConfigProperty(name = "harness.documents.mode", defaultValue = "fake") String mode) {
        return selectCatalog(mode);
    }

    static SandboxAgent selectAgent(String mode, Duration fakeDelay) {
        return switch (mode) {
            case "fake" -> new FakeSandboxAgent(fakeDelay);
            default -> throw unsupported("harness.sandbox.mode", mode);
        };
    }

    static SandboxTransfer selectTransfer(String mode, Duration fakePackaging, Duration fakeTransferring) {
        return switch (mode) {
            case "fake" -> new FakeSandboxTransfer(fakePackaging, fakeTransferring);
            default -> throw unsupported("harness.sandbox.mode", mode);
        };
    }

    static DocumentCatalog selectCatalog(String mode) {
        return switch (mode) {
            case "fake" -> new FakeDocumentCatalog();
            default -> throw unsupported("harness.documents.mode", mode);
        };
    }

    private static IllegalStateException unsupported(String property, String mode) {
        return new IllegalStateException("Unsupported " + property + "=" + mode + " (supported: fake)");
    }
}
