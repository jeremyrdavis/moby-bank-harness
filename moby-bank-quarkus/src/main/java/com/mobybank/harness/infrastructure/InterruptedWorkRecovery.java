package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.SessionApplicationService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Releases the sessions a restart interrupted, as soon as the application starts. Sessions are stored in a database
 * now, so one that was running a turn or moving when the process stopped is still marked busy afterwards, although the
 * background work that would have finished it is gone.
 */
@ApplicationScoped
public class InterruptedWorkRecovery {

    private final SessionApplicationService sessions;

    @Inject
    public InterruptedWorkRecovery(SessionApplicationService sessions) {
        this.sessions = sessions;
    }

    void onStart(@Observes StartupEvent event) {
        int released = sessions.recoverInterruptedWork();
        if (released > 0) {
            Log.warnf("Released %d session(s) left busy by the last shutdown", released);
        } else {
            Log.debug("No session was left busy by the last shutdown");
        }
    }
}
