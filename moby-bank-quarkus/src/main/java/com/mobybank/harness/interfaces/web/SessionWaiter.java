package com.mobybank.harness.interfaces.web;

import com.mobybank.harness.application.MessageDTO;
import com.mobybank.harness.application.SessionApplicationService;
import com.mobybank.harness.application.SessionEvent;
import com.mobybank.harness.application.SessionEvent.MessageAdded;
import com.mobybank.harness.application.SessionEvent.MoveCompleted;
import com.mobybank.harness.application.SessionEvent.MoveFailed;
import com.mobybank.harness.application.SessionEventStream;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lets a request wait for something the application does in the background. {@code index.html} has no live channel,
 * so the request that sends a message or starts a move stays open until the agent replies or the move ends, or until
 * {@code harness.ui.wait-timeout} passes. The wait subscribes to the session's events <em>before</em> starting the
 * work, so an outcome that arrives at once is not missed.
 *
 * <p>The application persists the idle state <em>before</em> it publishes the outcome, so a request can begin new work
 * in that gap and see the previous operation's outcome arrive on its subscription. The waiter therefore accepts only
 * an outcome that belongs to this request: a reply that was not in the conversation before the send, a move that
 * ends where this one was going, and a failure only once the session is no longer moving. Events that arrive before
 * the work has started are held back and judged afterwards, when the session's state shows what is running.
 */
@ApplicationScoped
public class SessionWaiter {

    /** How a move ended. */
    public record MoveOutcome(boolean completed, String detail) {
    }

    private final SessionApplicationService sessions;
    private final SessionEventStream events;
    private final Duration timeout;

    @Inject
    public SessionWaiter(SessionApplicationService sessions, SessionEventStream events,
                         @ConfigProperty(name = "harness.ui.wait-timeout", defaultValue = "180s") Duration timeout) {
        this.sessions = sessions;
        this.events = events;
        this.timeout = timeout;
    }

    /** Runs {@code start} (which sends the message), then waits for the agent's reply; empty if it takes too long. */
    public Optional<MessageDTO> awaitReply(String sessionId, Runnable start) {
        Set<String> before = sessions.get(sessionId).messages().stream().map(MessageDTO::id)
                .collect(Collectors.toSet());
        return await(sessionId, start, event ->
                event instanceof MessageAdded added && "assistant".equals(added.message().role())
                        && !before.contains(added.message().id()) ? added.message() : null);
    }

    /**
     * Runs {@code start} (which begins the move to {@code target}), then waits for the move to end; empty if it takes
     * too long.
     */
    public Optional<MoveOutcome> awaitMove(String sessionId, String target, Runnable start) {
        return await(sessionId, start, event -> switch (event) {
            case MoveCompleted completed when completed.location().equalsIgnoreCase(target) ->
                    new MoveOutcome(true, completed.location());
            case MoveFailed failed when !"moving".equals(sessions.get(sessionId).status()) ->
                    new MoveOutcome(false, failed.reason());
            default -> null;
        });
    }

    private <R> Optional<R> await(String sessionId, Runnable start, Function<SessionEvent, R> outcome) {
        CompletableFuture<R> done = new CompletableFuture<>();
        Consumer<SessionEvent> judge = event -> {
            try {
                R result = outcome.apply(event);
                Log.tracef("Session %s event %s: %s", sessionId, event.type(), result == null ? "not the outcome" : "the outcome");
                if (result != null) {
                    done.complete(result);
                }
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        };
        Log.debugf("Waiting on session %s for up to %s", sessionId, timeout);
        long waitStarted = System.nanoTime();
        Object lock = new Object();
        List<SessionEvent> early = new ArrayList<>();
        boolean[] started = {false};
        try (SessionEventStream.Subscription ignored = events.subscribe(sessionId, event -> {
            synchronized (lock) {
                if (started[0]) {
                    judge.accept(event);
                } else {
                    early.add(event);
                }
            }
        })) {
            start.run();
            synchronized (lock) {
                Log.debugf("Work started for session %s; %d events arrived early", sessionId, early.size());
                started[0] = true;
                early.forEach(judge);
                early.clear();
            }
            R result = done.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Log.debugf("Session %s: the outcome arrived after %d ms", sessionId, (System.nanoTime() - waitStarted) / 1_000_000);
            return Optional.of(result);
        } catch (TimeoutException e) {
            Log.debugf("Session %s: no outcome within %s", sessionId, timeout);
            return Optional.empty();
        } catch (InterruptedException e) {
            Log.debugf("Session %s: interrupted while waiting", sessionId);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            Log.debugf("Session %s: judging an event failed: %s", sessionId, e.getCause());
            throw new IllegalStateException(e.getCause());
        }
    }
}
