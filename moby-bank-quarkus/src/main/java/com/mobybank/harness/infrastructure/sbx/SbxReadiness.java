package com.mobybank.harness.infrastructure.sbx;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether this host can run real sandboxes ({@code /q/health/ready}), using {@link SbxPreflight}. It is down
 * only when the local side cannot work (sbx missing, daemon unhealthy); a missing cloud credential is listed as a
 * warning, because a host without a cloud subscription can still run local sandboxes. With {@code harness.sandbox.mode}
 * other than {@code sbx} it says so and stays up.
 */
@Readiness
@ApplicationScoped
public class SbxReadiness implements HealthCheck {

    private final SbxAdapters sbx;
    private final String mode;

    @Inject
    public SbxReadiness(SbxAdapters sbx, @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode) {
        this.sbx = sbx;
        this.mode = mode;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("sbx");
        if (!"sbx".equals(mode)) {
            return response.up().withData("mode", mode).build();
        }
        SbxPreflight.Report report = sbx.preflight().report();
        response.status(report.ready()).withData("mode", mode);
        report.details().forEach(response::withData);
        for (int i = 0; i < report.warnings().size(); i++) {
            response.withData("warning " + (i + 1), report.warnings().get(i));
        }
        return response.build();
    }
}
