package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.MessageDTO;
import com.mobybank.harness.application.SessionEvent;
import com.mobybank.harness.application.SessionEvent.MessageAdded;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEvent.MoveFailed;
import com.mobybank.harness.application.SessionEventStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lets a request wait for something the application does in the background. {@code index.html} has no live channel,
 * so the request that sends a message or starts a move stays open until the agent replies or the move ends, or until
 * {@code harness.ui.wait-timeout} passes. The wait subscribes to the session's events <em>before</em> starting the
 * work, so an outcome that arrives at once is not missed.
 */
@ApplicationScoped
public class SessionWaiter {

    /** How a move ended. */
    public record MoveOutcome(boolean completed, String detail) {
    }

    private final SessionEventStream events;
    private final Duration timeout;

    @Inject
    public SessionWaiter(SessionEventStream events,
                         @ConfigProperty(name = "harness.ui.wait-timeout", defaultValue = "180s") Duration timeout) {
        this.events = events;
        this.timeout = timeout;
    }

    /** Runs {@code start} (which sends the message), then waits for the agent's reply; empty if it takes too long. */
    public Optional<MessageDTO> awaitReply(String sessionId, Runnable start) {
        return await(sessionId, start, event ->
                event instanceof MessageAdded added && "assistant".equals(added.message().role()) ? added.message() : null);
    }

    /** Runs {@code start} (which begins the move), then waits for the move to end; empty if it takes too long. */
    public Optional<MoveOutcome> awaitMove(String sessionId, Runnable start) {
        return await(sessionId, start, event -> switch (event) {
            case MoveCompleted completed -> new MoveOutcome(true, completed.location());
            case MoveFailed failed -> new MoveOutcome(false, failed.reason());
            default -> null;
        });
    }

    private <R> Optional<R> await(String sessionId, Runnable start, Function<SessionEvent, R> outcome) {
        CompletableFuture<R> done = new CompletableFuture<>();
        try (SessionEventStream.Subscription ignored = events.subscribe(sessionId, event -> {
            R result = outcome.apply(event);
            if (result != null) {
                done.complete(result);
            }
        })) {
            start.run();
            return Optional.of(done.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
