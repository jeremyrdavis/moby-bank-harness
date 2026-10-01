package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.application.UploadStore;
import com.mobybank.harness.domain.AgentReply;
import com.mobybank.harness.domain.AgentTurn;
import com.mobybank.harness.domain.DocumentCatalog;
import com.mobybank.harness.domain.FileRef;
import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.Message;
import com.mobybank.harness.domain.MessageRole;
import com.mobybank.harness.domain.SandboxAgent;
import com.mobybank.harness.domain.SandboxFailureException;
import com.mobybank.harness.domain.SessionId;
import com.mobybank.harness.domain.Step;
import com.mobybank.harness.infrastructure.sbx.SandboxRegistry.Handle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Runs each turn by driving the agent (the Claude Code CLI) inside a Docker Sandbox through the {@code sbx} CLI,
 * locally or in the cloud according to the turn's location.
 *
 * <p>A session gets one sandbox, created on its first turn. Files attached to a message are copied into the
 * sandbox's files/ folder first. The agent then runs headless with its JSON stream on standard output; its tool
 * calls become steps as they appear, and its final answer becomes the reply. The agent's own conversation id is kept
 * so the next turn resumes it; when there is no id to resume (a new sandbox, or a session that only has history in
 * this app), the earlier conversation is included in the prompt instead.
 */
public final class SbxSandboxAgent implements SandboxAgent {

    private static final Logger LOG = Logger.getLogger(SbxSandboxAgent.class);

    private static final String PREAMBLE = """
            You are a credit-research assistant helping a bank analyst. Work from the attached files when there are \
            any. Answer in short paragraphs. When a table helps, give exactly one Markdown table.""";

    private static final int HISTORY_CHARS_PER_MESSAGE = 1500;

    private final SbxCli cli;
    private final SandboxRegistry registry;
    private final UploadStore uploads;
    private final DocumentCatalog catalog;
    private final SbxSettings settings;

    public SbxSandboxAgent(SbxCli cli, SandboxRegistry registry, UploadStore uploads, DocumentCatalog catalog,
                           SbxSettings settings) {
        this.cli = cli;
        this.registry = registry;
        this.uploads = uploads;
        this.catalog = catalog;
        this.settings = settings;
    }

    @Override
    public AgentReply runTurn(AgentTurn turn, Consumer<Step> onStep) {
        Handle sandbox = sandboxFor(turn);
        stage(turn, sandbox);

        Optional<String> resume = registry.agentSession(turn.sessionId());
        ParsedRun run = run(turn, sandbox, resume, onStep);
        if (!run.succeeded() && resume.isPresent()) {
            LOG.warnf("Resuming the agent session failed for %s; retrying with the conversation in the prompt",
                    turn.sessionId().value());
            registry.forgetAgentSession(turn.sessionId());
            run = run(turn, sandbox, Optional.empty(), onStep);
        }
        if (!run.succeeded()) {
            throw new SandboxFailureException("The agent failed in " + sandbox.name() + ": " + run.failure());
        }
        run.parser().sessionId().ifPresent(id -> registry.setAgentSession(turn.sessionId(), id));
        return ReplyFormatter.toReply(run.answer(), run.parser().steps());
    }

    private ParsedRun run(AgentTurn turn, Handle sandbox, Optional<String> resume, Consumer<Step> onStep) {
        ClaudeStreamParser parser = new ClaudeStreamParser(onStep);
        List<String> command = new ArrayList<>(List.of(settings.agent(), "-p",
                prompt(turn, resume.isEmpty()), "--output-format", "stream-json", "--verbose"));
        command.addAll(settings.agentArgs());
        resume.ifPresent(id -> command.addAll(List.of("--resume", id)));

        CommandResult result = cli.exec(sandbox.location(), sandbox.name(), command, settings.turnTimeout(),
                parser::accept);
        return new ParsedRun(parser, result);
    }

    /** What one attempt produced. */
    private record ParsedRun(ClaudeStreamParser parser, CommandResult result) {

        boolean succeeded() {
            boolean answered = parser.resultText().isPresent() || !parser.lastAssistantText().isBlank();
            return !parser.isError() && (result.succeeded() || answered);
        }

        String answer() {
            return parser.resultText().orElse(parser.lastAssistantText());
        }

        String failure() {
            if (parser.isError()) {
                return answer().isBlank() ? "it reported an error" : answer().strip();
            }
            return "exit " + result.exitCode() + (result.tail().isEmpty() ? "" : ": " + result.tail());
        }
    }

    // --- sandbox ----------------------------------------------------------------------------------------------

    /** The session's sandbox at the turn's location: known, rediscovered after a restart, or newly created. */
    private Handle sandboxFor(AgentTurn turn) {
        SessionId session = turn.sessionId();
        Location location = turn.location();

        Optional<Handle> known = registry.current(session).filter(handle -> handle.location() == location);
        if (known.isPresent()) {
            return known.get();
        }
        Optional<Handle> existing = findExisting(session, location);
        if (existing.isPresent()) {
            registry.set(session, existing.get());
            return existing.get();
        }
        int generation = registry.current(session).map(handle -> handle.generation() + 1).orElse(0);
        String name = SandboxRegistry.nameFor(session, generation);
        cli.create(location, name, settings.agent(), settings.cloudTtl());
        Handle created = new Handle(location, name, generation);
        registry.set(session, created);
        return created;
    }

    private Optional<Handle> findExisting(SessionId session, Location location) {
        Handle best = null;
        for (String listed : cli.listNames(location)) {
            Optional<Integer> generation = SandboxRegistry.generationOf(listed, session);
            if (generation.isPresent() && (best == null || generation.get() >= best.generation())) {
                best = new Handle(location, listed, generation.get());
            }
        }
        return Optional.ofNullable(best);
    }

    // --- files ------------------------------------------------------------------------------------------------

    private void stage(AgentTurn turn, Handle sandbox) {
        List<FileRef> files = turn.userMessage().files();
        if (files.isEmpty()) {
            return;
        }
        cli.makeDirectory(sandbox.location(), sandbox.name(), settings.filesDir());
        Path dir;
        try {
            dir = Files.createTempDirectory("harness-stage-");
        } catch (IOException e) {
            throw new SandboxFailureException("Could not prepare files for the sandbox: " + e.getMessage(), e);
        }
        try {
            for (FileRef file : files) {
                Path local = dir.resolve(file.name()).normalize();
                if (!dir.equals(local.getParent())) {
                    throw new SandboxFailureException("Unsupported file name: " + file.name());
                }
                Files.write(local, content(turn.sessionId(), file));
                cli.copyIn(sandbox.location(), local, sandbox.name(), settings.filesDir() + "/" + file.name());
            }
        } catch (IOException e) {
            throw new SandboxFailureException("Could not prepare files for the sandbox: " + e.getMessage(), e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private byte[] content(SessionId session, FileRef file) {
        Optional<byte[]> content = switch (file.source()) {
            case UPLOAD -> uploads.find(session, file.name());
            case ONEDRIVE -> catalog.fetch(file.name());
        };
        return content.orElseThrow(() -> new SandboxFailureException(
                "The attached file " + file.name() + " is no longer available (" + file.source().name().toLowerCase() + ")"));
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.debugf("Could not clean up %s: %s", dir, e.getMessage());
        }
    }

    // --- prompt -----------------------------------------------------------------------------------------------

    private String prompt(AgentTurn turn, boolean includeHistory) {
        StringBuilder prompt = new StringBuilder(PREAMBLE).append("\n\n");
        if (includeHistory && !turn.history().isEmpty()) {
            prompt.append("Earlier in this conversation:\n");
            for (Message message : turn.history()) {
                prompt.append(message.role() == MessageRole.USER ? "Analyst: " : "You: ")
                        .append(clip(messageText(message))).append("\n");
            }
            prompt.append("\n");
        }
        prompt.append("Analyst request:\n").append(turn.userMessage().text());
        List<FileRef> files = turn.userMessage().files();
        if (!files.isEmpty()) {
            prompt.append("\n\nAttached files (in ").append(settings.filesDir()).append("):");
            files.forEach(file -> prompt.append("\n- ").append(file.name()));
        }
        return prompt.toString();
    }

    private static String messageText(Message message) {
        return message.role() == MessageRole.USER ? message.text() : String.join("\n", message.paragraphs());
    }

    private static String clip(String text) {
        return text.length() <= HISTORY_CHARS_PER_MESSAGE ? text : text.substring(0, HISTORY_CHARS_PER_MESSAGE) + "…";
    }
}
