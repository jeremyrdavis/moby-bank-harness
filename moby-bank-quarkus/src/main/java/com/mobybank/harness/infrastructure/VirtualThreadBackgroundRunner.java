package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.BackgroundRunner;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.logging.Logger;

/**
 * Runs background work on virtual threads, which suits the blocking calls agent turns and sandbox transfers make.
 * A task that throws is logged and never takes the runner down.
 */
@ApplicationScoped
public class VirtualThreadBackgroundRunner implements BackgroundRunner {

    private static final Logger LOG = Logger.getLogger(VirtualThreadBackgroundRunner.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void run(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.error("Background task failed", t);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
