package com.mobybank.harness.infrastructure.sbx;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Logs what is wrong with this host's sandbox setup when the app starts in {@code sbx} mode, so a missing cloud
 * credential shows up in the log before the first turn fails. It never stops the app, and it runs in the background so
 * it does not slow the start.
 */
@ApplicationScoped
public class SbxStartupCheck {

    private static final Logger LOG = Logger.getLogger(SbxStartupCheck.class);

    private final SbxAdapters sbx;
    private final String mode;

    @Inject
    public SbxStartupCheck(SbxAdapters sbx, @ConfigProperty(name = "harness.sandbox.mode", defaultValue = "fake") String mode) {
        this.sbx = sbx;
        this.mode = mode;
    }

    void onStart(@Observes StartupEvent event) {
        if ("sbx".equals(mode)) {
            Thread.startVirtualThread(this::logReport);
        }
    }

    void logReport() {
        try {
            SbxPreflight.Report report = sbx.preflight().report();
            report.details().forEach((check, result) -> LOG.debugf("sbx preflight: %s: %s", check, result));
            if (report.warnings().isEmpty()) {
                LOG.infof("sbx preflight passed (%s)", report.details().getOrDefault("sbx", "unknown version"));
            }
            report.warnings().forEach(warning -> LOG.warnf("sbx preflight: %s", warning));
        } catch (RuntimeException e) {
            LOG.warnf("sbx preflight could not run: %s", e.getMessage());
        }
    }
}
