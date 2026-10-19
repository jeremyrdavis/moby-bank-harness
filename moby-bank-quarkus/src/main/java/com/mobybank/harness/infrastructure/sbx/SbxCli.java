package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import io.quarkus.logging.Log;

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

    /**
     * Creates a sandbox without a workspace mount. A cloud sandbox can be given a time-to-live and told what to do
     * when it runs out ({@code stop}, {@code delete} or {@code restart}); neither applies locally.
     */
    public void create(Location target, String name, String agent, Duration cloudTtl, String cloudOnTimeout) {
        List<String> command = base(target);
        command.addAll(List.of("create", "--name", name));
        if (target == Location.CLOUD) {
            addCloudLifetime(command, cloudTtl, cloudOnTimeout);
        }
        command.add(agent);
        Log.debugf("Creating sandbox %s at %s", name, target);
        requireSuccess(runner.run(command, commandTimeout), "Creating sandbox " + name);
    }

    /** The names of the sandboxes at a location, exactly as {@code sbx ls -q} prints them. */
    public List<String> listNames(Location target) {
        List<String> command = base(target);
        command.addAll(List.of("ls", "-q"));
        Log.debugf("Listing sandboxes at %s", target);
        CommandResult result = requireSuccess(runner.run(command, commandTimeout), "Listing sandboxes");
        List<String> names = result.stdout().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        Log.debugf("Found %d sandboxes at %s: %s", names.size(), target, names);
        return names;
    }

    public void makeDirectory(Location target, String sandbox, String directory) {
        Log.debugf("Creating %s in sandbox %s", directory, sandbox);
        requireSuccess(exec(target, sandbox, List.of("mkdir", "-p", directory), commandTimeout, line -> { }),
                "Creating " + directory + " in " + sandbox);
    }

    public void copyIn(Location target, Path localFile, String sandbox, String remotePath) {
        List<String> command = base(target);
        command.addAll(List.of("cp", localFile.toString(), sandbox + ":" + remotePath));
        Log.debugf("Copying a file into %s at %s (%s)", sandbox, remotePath, target);
        requireSuccess(runner.run(command, commandTimeout), "Copying " + localFile.getFileName() + " into " + sandbox);
    }

    /** Runs a command inside a sandbox. The caller decides what a non-zero exit code means. */
    public CommandResult exec(Location target, String sandbox, List<String> inside, Duration timeout,
                              Consumer<String> onStdoutLine) {
        List<String> command = base(target);
        command.addAll(List.of("exec", sandbox));
        command.addAll(inside);
        Log.debugf("Executing %s inside sandbox %s at %s (timeout %s)", inside.get(0), sandbox, target, timeout);
        return runner.run(command, timeout, onStdoutLine);
    }

    /**
     * Moves a sandbox to the other location with {@code sbx move}, which captures the sandbox's filesystem and starts a
     * new sandbox from it at the destination. The destination gets a new id; {@code newName} is the name requested
     * for it (a cloud destination may be listed with an agent prefix, and the service may add a suffix).
     */
    public void move(String source, Location to, String newName, Duration cloudTtl, String cloudOnTimeout,
                     Duration timeout) {
        List<String> command = new ArrayList<>(List.of(binary, "move", source, "--to", to == Location.CLOUD ? "cloud" : "local",
                "--name", newName, "--force"));
        if (to == Location.CLOUD) {
            addCloudLifetime(command, cloudTtl, cloudOnTimeout);
        }
        Log.debugf("Moving %s to %s as %s (timeout %s)", source, to.name().toLowerCase(), newName, timeout);
        requireSuccess(runner.run(command, timeout), "Moving " + source + " to " + to.name().toLowerCase());
    }

    /** What {@code sbx --version} prints, for example {@code v0.48.0 <commit>}; fails if sbx cannot be started. */
    public String version() {
        Log.debugf("Asking %s for its version", binary);
        CommandResult result = requireSuccess(runner.run(List.of(binary, "--version"), commandTimeout),
                "Asking sbx for its version");
        return result.stdout().lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
    }

    /**
     * Runs {@code sbx diagnose} (with {@code --cloud} for the cloud). The caller reads the result: a healthy host exits
     * 0 and prints {@code ✓} lines and a summary such as {@code 14 passed}.
     */
    public CommandResult diagnose(Location target) {
        List<String> command = base(target);
        command.add("diagnose");
        Log.debugf("Running diagnose at %s", target);
        CommandResult result = runner.run(command, commandTimeout);
        Log.debugf("Diagnose at %s exited %d", target, result.exitCode());
        return result;
    }

    /** The output of {@code sbx secret ls}: one line per stored secret, with the service name in it. */
    public String secrets(Location target) {
        List<String> command = base(target);
        command.addAll(List.of("secret", "ls"));
        Log.debugf("Listing secrets at %s", target);
        return requireSuccess(runner.run(command, commandTimeout), "Listing secrets").stdout();
    }

    /** Deletes a sandbox. */
    public void remove(Location target, String sandbox) {
        List<String> command = base(target);
        command.addAll(List.of("rm", sandbox));
        Log.debugf("Removing sandbox %s at %s", sandbox, target);
        requireSuccess(runner.run(command, commandTimeout), "Removing sandbox " + sandbox);
    }

    private static void addCloudLifetime(List<String> command, Duration ttl, String onTimeout) {
        if (ttl != null) {
            command.addAll(List.of("--ttl", formatDuration(ttl)));
        }
        if (onTimeout != null) {
            command.addAll(List.of("--on-timeout", onTimeout));
        }
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
            String detail = "exit " + result.exitCode() + ": " + result.tail();
            Log.debugf("%s failed: %s", what, detail);
            throw new SandboxFailureException(SbxErrors.hint(result.tail(), null)
                    .map(hint -> what + " failed: " + hint + " (" + detail + ")")
                    .orElse(what + " failed (" + detail + ")"));
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
