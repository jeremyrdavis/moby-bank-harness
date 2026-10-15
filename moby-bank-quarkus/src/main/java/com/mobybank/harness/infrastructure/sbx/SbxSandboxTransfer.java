package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.MoveProgress;
import com.mobybank.harness.domain.MoveRequest;
import com.mobybank.harness.domain.MoveStage;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SandboxTransfer;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Moves a session with {@code sbx move}, which captures the whole sandbox filesystem as an image and starts a new
 * sandbox from it at the destination. The conversation files, the agent's own history and everything it installed
 * travel with it, so the next turn can carry on where the last one stopped.
 *
 * <p>Each move asks for the next generation's name ({@code harness-<session>-g<n>}); the service may add a suffix,
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
        if (source.isEmpty()) {
            onProgress.accept(new MoveProgress(MoveStage.PACKAGING,
                    "Nothing to package yet · the agent will start at the destination"));
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
            registry.forgetAgentSession(session);
            onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, "The sandbox at the "
                    + request.from().name().toLowerCase() + " source is no longer there · the agent will start at the "
                    + "destination from the conversation history"));
            return;
        }

        onProgress.accept(new MoveProgress(MoveStage.TRANSFERRING, "Starting the session at the destination"));
        Handle moved = locate(session, request.to(), generation);
        registry.set(session, moved);
    }

    /** The sandbox the session runs in now, from memory or, after a restart, from the source location's listing. */
    private Optional<Handle> source(MoveRequest request) {
        Optional<Handle> known = registry.current(request.sessionId()).filter(h -> h.location() == request.from());
        if (known.isPresent()) {
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
            throw new SandboxFailureException("The move finished but the new sandbox "
                    + SandboxRegistry.nameFor(session, generation) + " is not listed at the "
                    + destination.name().toLowerCase() + " destination");
        }
        return found;
    }
}
