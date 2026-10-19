package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import io.quarkus.logging.Log;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Moves a session with {@code sbx move}, which captures the whole sandbox filesystem as an image and starts a new
 * sandbox from it at the destination. The conversation files, the agent's own history and everything it installed
 * travel with it, so the next turn can carry on where the last one stopped.
 *
 * <p>Each move asks for the next generation's name ({@code moby-bank-<session>-g<n>}); the service may add a suffix,
 * so the new sandbox is found afterwards by listing the destination. A session that has not started a sandbox yet has
 * nothing to transfer: it simply starts at the destination on its next turn.
 */
public final class SbxSandboxTransfer implements SandboxTransfer {

    private final SbxCli cli;
    private final SandboxRegistry registry;
    private final SbxSettings settings;

    public SbxSandboxTransfer(SbxCli cli, SandboxRegistry registry, SbxSettings settings) {
        this.cli = cli;
        this.registry = registry;
        this.settings = settings;
    }

    @Override
    public void move(MoveRequest request, Consumer<MoveProgress> onProgress) {
        SessionId session = request.sessionId();
        Optional<Handle> source = source(request);
        Log.debugf("Move of session %s %s -> %s: source sandbox %s", session.value(), request.from(), request.to(),
                source.map(Handle::name).orElse("none yet"));
        if (source.isEmpty()) {
            startAtDestination(request, onProgress);
            return;
        }
        Handle from = source.get();
        int generation = from.generation() + 1;
        String requested = SandboxRegistry.nameFor(session, generation);
        String summary = request.history().size() + " messages · " + request.files().size() + " files";

        onProgress.accept(new MoveProgress(MoveStage.PACKAGING, "Packaging the sandbox · " + summary));
        try {
            cli.move(from.name(), request.to(), requested, settings.cloudTtl(), settings.cloudOnTimeout(),
                    settings.moveTimeout());
        } catch (SandboxFailureException failure) {
            if (!isMissing(from, failure)) {
                throw failure;
            }
            // Nothing is left to carry (it was deleted): the next turn starts a fresh sandbox at the destination
            // from the conversation history, and copies the shared files in again.
            Log.warnf("The source sandbox %s of session %s is gone; the next turn starts fresh at the destination",
                    from.name(), session.value());
            registry.forgetAgentSession(session);
            onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, "The sandbox at the "
                    + request.from().name().toLowerCase() + " source is no longer there · the agent will start at the "
                    + "destination from the conversation history"));
            return;
        }

        onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, "Starting the session at the destination"));
        Handle moved = locate(session, request.to(), generation);
        Log.debugf("Move of session %s done: %s is now at %s", session.value(), moved.name(), request.to());
        registry.set(session, moved);
    }

    /**
     * A session that has no sandbox yet (no turn has run, or its sandbox is gone) has nothing to carry over, but the
     * move still has to leave a sandbox at the destination: it is created there now, empty, and the next turn copies
     * the shared files in and replays the conversation. Without this the move would only change what the app says.
     */
    private void startAtDestination(MoveRequest request, Consumer<MoveProgress> onProgress) {
        SessionId session = request.sessionId();
        int generation = registry.current(session).map(handle -> handle.generation() + 1).orElse(0);
        String name = SandboxRegistry.nameFor(session, generation);
        Log.debugf("Session %s has no sandbox to move; creating %s at %s", session.value(), name, request.to());
        onProgress.accept(new MoveProgress(MoveStage.PACKAGING,
                "No sandbox to package yet · creating a new one at the destination"));
        cli.create(request.to(), name, settings.agent(), settings.cloudTtl(), settings.cloudOnTimeout());
        onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, "Starting the session at the destination"));
        Handle created = locate(session, request.to(), generation);
        Log.debugf("Created %s at %s for session %s; the next turn copies the shared files in", created.name(),
                request.to(), session.value());
        registry.set(session, created);
        registry.forgetAgentSession(session);
        registry.expectFiles(session);
    }

    /** The sandbox the session runs in now, from memory or, after a restart, from the source location's listing. */
    private Optional<Handle> source(MoveRequest request) {
        Optional<Handle> known = registry.current(request.sessionId()).filter(h -> h.location() == request.from());
        if (known.isPresent()) {
            Log.tracef("Move source of session %s is known", request.sessionId().value());
            return known;
        }
        return registry.discover(cli, request.sessionId(), request.from());
    }

    /** True when the source is no longer listed. If it cannot be listed, the move's own failure stands. */
    private boolean isMissing(Handle source, SandboxFailureException failure) {
        try {
            return cli.listNames(source.location()).stream()
                    .noneMatch(listed -> SandboxRegistry.bareName(listed).equals(SandboxRegistry.bareName(source.name())));
        } catch (SandboxFailureException listing) {
            failure.addSuppressed(listing);
            return false;
        }
    }

    private Handle locate(SessionId session, Location destination, int generation) {
        Handle found = null;
        for (String listed : cli.listNames(destination)) {
            if (SandboxRegistry.generationOf(listed, session).filter(g -> g == generation).isPresent()) {
                found = new Handle(destination, SandboxRegistry.bareName(listed), generation);
            }
        }
        if (found == null) {
            Log.debugf("Generation %d of session %s is not listed at %s", generation, session.value(), destination);
            throw new SandboxFailureException("The move finished but the new sandbox "
                    + SandboxRegistry.nameFor(session, generation) + " is not listed at the "
                    + destination.name().toLowerCase() + " destination");
        }
        return found;
    }
}
