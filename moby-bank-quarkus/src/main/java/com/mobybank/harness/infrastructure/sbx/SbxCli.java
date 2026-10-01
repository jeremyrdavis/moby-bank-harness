package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The few {@code sbx} commands the harness needs, built as argument lists. {@link Location#CLOUD} adds the global
 * {@code --cloud} flag, which points the same commands at Docker's hosted sandboxes instead of the local daemon.
 */
public final class SbxCli {

    private final CommandRunner runner;
    private final String binary;
    private final Duration commandTimeout;

    public SbxCli(CommandRunner runner, String binary, Duration commandTimeout) {
        this.runner = runner;
        this.binary = binary;
        this.commandTimeout = commandTimeout;
    }

    /** Creates a sandbox without a workspace mount. Cloud sandboxes can be given a time-to-live. */
    public void create(Location target, String name, String agent, Duration cloudTtl) {
        List<String> command = base(target);
        command.addAll(List.of("create", "--name", name));
        if (target == Location.CLOUD && cloudTtl != null) {
            command.addAll(List.of("--ttl", formatDuration(cloudTtl)));
        }
        command.add(agent);
        requireSuccess(runner.run(command, commandTimeout), "Creating sandbox " + name);
    }

    /** The names of the sandboxes at a location, exactly as {@code sbx ls -q} prints them. */
    public List<String> listNames(Location target) {
        List<String> command = base(target);
        command.addAll(List.of("ls", "-q"));
        CommandResult result = requireSuccess(runner.run(command, commandTimeout), "Listing sandboxes");
        return result.stdout().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    public void makeDirectory(Location target, String sandbox, String directory) {
        requireSuccess(exec(target, sandbox, List.of("mkdir", "-p", directory), commandTimeout, line -> { }),
                "Creating " + directory + " in " + sandbox);
    }

    public void copyIn(Location target, Path localFile, String sandbox, String remotePath) {
        List<String> command = base(target);
        command.addAll(List.of("cp", localFile.toString(), sandbox + ":" + remotePath));
        requireSuccess(runner.run(command, commandTimeout), "Copying " + localFile.getFileName() + " into " + sandbox);
    }

    /** Runs a command inside a sandbox. The caller decides what a non-zero exit code means. */
    public CommandResult exec(Location target, String sandbox, List<String> inside, Duration timeout,
                              Consumer<String> onStdoutLine) {
        List<String> command = base(target);
        command.addAll(List.of("exec", sandbox));
        command.addAll(inside);
        return runner.run(command, timeout, onStdoutLine);
    }

    /**
     * Moves a sandbox to the other location with {@code sbx move}, which captures the sandbox's filesystem and starts a
     * new sandbox from it at the destination. The destination gets a new id; {@code newName} is the name requested
     * for it (a cloud destination always appends a short suffix).
     */
    public void move(String source, Location to, String newName, Duration cloudTtl, Duration timeout) {
        List<String> command = new ArrayList<>(List.of(binary, "move", source, "--to", to == Location.CLOUD ? "cloud" : "local",
                "--name", newName, "--force"));
        if (to == Location.CLOUD && cloudTtl != null) {
            command.addAll(List.of("--ttl", formatDuration(cloudTtl)));
        }
        requireSuccess(runner.run(command, timeout), "Moving " + source + " to " + to.name().toLowerCase());
    }

    private List<String> base(Location target) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        if (target == Location.CLOUD) {
            command.add("--cloud");
        }
        return command;
    }

    static CommandResult requireSuccess(CommandResult result, String what) {
        if (!result.succeeded()) {
            throw new SandboxFailureException(what + " failed (exit " + result.exitCode() + "): " + result.tail());
        }
        return result;
    }

    /** sbx accepts durations such as 30m and 2h; this renders whole hours, minutes or seconds that way. */
    static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }
}
