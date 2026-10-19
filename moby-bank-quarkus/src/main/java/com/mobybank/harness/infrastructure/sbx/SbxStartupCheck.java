package com.mobybank.harness.infrastructure.sbx;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Logs what is wrong with this host's sandbox setup when the app starts in {@code sbx} mode, so a missing cloud
 * credential shows up in the log before the first turn fails. It never stops the app, and it runs in the background so
 * it does not slow the start.
 */
@ApplicationScoped
public class SbxStartupCheck {

    private final SbxAdapters sbx;
    private final String mode;

    @Inject
    public SbxStartupCheck(SbxAdapters sbx, @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode) {
        this.sbx = sbx;
        this.mode = mode;
    }

    void onStart(@Observes StartupEvent event) {
        if ("sbx".equals(mode)) {
            Log.debug("Starting the sbx preflight in the background");
            Thread.startVirtualThread(this::logReport);
        } else {
            Log.debugf("Skipping the sbx preflight (harness.sandbox.mode=%s)", mode);
        }
    }

    void logReport() {
        try {
            SbxPreflight.Report report = sbx.preflight().report();
            report.details().forEach((check, result) -> Log.debugf("sbx preflight: %s: %s", check, result));
            if (report.warnings().isEmpty()) {
                Log.infof("sbx preflight passed (%s)", report.details().getOrDefault("sbx", "unknown version"));
            }
            report.warnings().forEach(warning -> Log.warnf("sbx preflight: %s", warning));
        } catch (RuntimeException e) {
            Log.warnf("sbx preflight could not run: %s", e.getMessage());
        }
    }
}
