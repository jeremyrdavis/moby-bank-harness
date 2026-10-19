package com.mobybank.harness.infrastructure;

import com.mobybank.harness.application.BackgroundRunner;
import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs background work on virtual threads, which suits the blocking calls agent turns and sandbox transfers make.
 * A task that throws is logged and never takes the runner down.
 */
@ApplicationScoped
public class VirtualThreadBackgroundRunner implements BackgroundRunner {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void run(Runnable task) {
        Log.trace("Queueing a background task");
        executor.execute(() -> {
            long started = System.nanoTime();
            Log.debugf("Background task started on %s", Thread.currentThread());
            try {
                task.run();
                Log.debugf("Background task finished in %d ms", (System.nanoTime() - started) / 1_000_000);
            } catch (Throwable t) {
                Log.errorf(t, "Background task failed after %d ms", (System.nanoTime() - started) / 1_000_000);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        Log.debug("Shutting down the background runner");
        executor.shutdownNow();
    }
}
