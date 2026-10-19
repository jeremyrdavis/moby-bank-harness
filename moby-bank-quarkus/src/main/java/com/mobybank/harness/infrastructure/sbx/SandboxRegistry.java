package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SessionId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which sandbox currently backs each session, and the agent's own conversation id inside it so the next
 * turn can resume. Sandbox names follow {@code moby-bank-<session>-g<generation>}; each move creates the next
 * generation, which lets the adapters find a session's sandbox again after a restart by listing sandboxes.
 */
@ApplicationScoped
public class SandboxRegistry {

    /** The sandbox a session runs in now. {@code name} is exactly as {@code sbx ls} lists it. */
    public record Handle(Location location, String name, int generation) {
    }

    private final Map<SessionId, Handle> handles = new ConcurrentHashMap<>();
    private final Map<SessionId, String> agentSessions = new ConcurrentHashMap<>();
    private final Set<SessionId> awaitingFiles = ConcurrentHashMap.newKeySet();

    public Optional<Handle> current(SessionId session) {
        return Optional.ofNullable(handles.get(session));
    }

    public void set(SessionId session, Handle handle) {
        handles.put(session, handle);
    }

    /**
     * Notes that a session's sandbox was created empty by a move, so the next turn must copy the session's shared files
     * into it (a sandbox the turn creates itself gets them as part of the turn).
     */
    public void expectFiles(SessionId session) {
        awaitingFiles.add(session);
    }

    /** Whether {@link #expectFiles} was called and the shared files have not been copied in yet. */
    public boolean expectsFiles(SessionId session) {
        return awaitingFiles.contains(session);
    }

    /** Clears the note once the shared files are in the sandbox. A failed copy leaves it, so the next turn retries. */
    public void filesCopied(SessionId session) {
        awaitingFiles.remove(session);
    }

    public Optional<String> agentSession(SessionId session) {
        return Optional.ofNullable(agentSessions.get(session));
    }

    public void setAgentSession(SessionId session, String agentSessionId) {
        agentSessions.put(session, agentSessionId);
    }

    /**
     * A sandbox's name without the agent prefix {@code sbx --cloud ls} adds ({@code claude/name}). The bare name is what
     * later commands are given: it is the form that was run against a real host, and it is what a listing is compared
     * with.
     */
    static String bareName(String listedName) {
        return listedName.substring(listedName.lastIndexOf('/') + 1).strip();
    }

    /**
     * The newest generation of the session's sandbox listed at a location, for finding it again after a restart or
     * when the registry points elsewhere. When the registry already knows a sandbox (at the other location), only a
     * newer generation counts: an older one is a source that an earlier move left behind, and it lacks everything done
     * since (for example after the newer sandbox was deleted).
     */
    Optional<Handle> discover(SbxCli cli, SessionId session, Location location) {
        int newerThan = current(session).map(Handle::generation).orElse(-1);
        Handle best = null;
        for (String listed : cli.listNames(location)) {
            Optional<Integer> generation = generationOf(listed, session);
            if (generation.isPresent() && generation.get() > newerThan
                    && (best == null || generation.get() >= best.generation())) {
                best = new Handle(location, bareName(listed), generation.get());
            }
        }
        return Optional.ofNullable(best);
    }

    public void forgetAgentSession(SessionId session) {
        agentSessions.remove(session);
    }

    /** The name prefix shared by every generation of a session's sandbox. */
    static String baseName(SessionId session) {
        return "moby-bank-" + session.value().toString().replace("-", "").substring(0, 12);
    }

    static String nameFor(SessionId session, int generation) {
        return baseName(session) + "-g" + generation;
    }

    /**
     * Reads the generation out of a name listed by {@code sbx ls}, which may carry an {@code agent/} prefix and, for
     * cloud sandboxes, a suffix the service added after {@code -g<generation>}.
     */
    static Optional<Integer> generationOf(String listedName, SessionId session) {
        String name = bareName(listedName);
        String prefix = baseName(session) + "-g";
        if (!name.startsWith(prefix)) {
            return Optional.empty();
        }
        String rest = name.substring(prefix.length());
        int end = 0;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
            end++;
        }
        if (end == 0 || end > 6 || (end < rest.length() && rest.charAt(end) != '-')) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(rest.substring(0, end)));
    }
}
