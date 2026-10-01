package com.mobybank.harness.infrastructure;

import com.mobybank.harness.domain.ConnectedFolders;
import com.mobybank.harness.domain.ConnectedFoldersRepository;
import com.mobybank.harness.domain.SessionRepository;
import com.mobybank.harness.domain.UserId;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Clock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Loads the prototype's demo conversations at startup (turn off with harness.seed-demo-data=false), and connects its
 * first three demo folders too, but only when the document catalog is the fake one: a real OneDrive has no such
 * folders.
 */
@ApplicationScoped
public class DemoDataSeeder {

    private static final Logger LOG = Logger.getLogger(DemoDataSeeder.class);

    private final SessionRepository sessions;
    private final ConnectedFoldersRepository connectedFolders;
    private final Clock clock;
    private final boolean enabled;
    private final String documentsMode;

    @Inject
    public DemoDataSeeder(SessionRepository sessions, ConnectedFoldersRepository connectedFolders, Clock clock,
                          @ConfigProperty(name = "harness.seed-demo-data", defaultValue = "true") boolean enabled,
                          @ConfigProperty(name = "harness.documents.mode", defaultValue = "fake") String documentsMode) {
        this.sessions = sessions;
        this.connectedFolders = connectedFolders;
        this.clock = clock;
        this.enabled = enabled;
        this.documentsMode = documentsMode;
    }

    void onStart(@Observes StartupEvent event) {
        if (enabled) {
            seed();
        }
    }

    void seed() {
        DemoData.sessions(clock).forEach(sessions::persist);
        if ("fake".equals(documentsMode)) {
            ConnectedFolders folders = ConnectedFolders.none(UserId.DEMO);
            folders.connect(DemoData.initiallyConnected());
            connectedFolders.persist(folders);
            LOG.info("Seeded the demo conversations and connected folders");
        } else {
            LOG.info("Seeded the demo conversations (folders come from the real document source)");
        }
    }
}
