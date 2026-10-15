package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import com.mobybank.harness.domain.SandboxFailureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Checks, without changing anything the demo depends on, that this host can run what the adapters will ask of it: sbx
 * is installed and new enough for the cloud, the local daemon is healthy, and the agent has a credential in each
 * location. The answer is cached briefly so a health endpoint can call it freely.
 *
 * <p>A secret being listed does not mean it works (a stale one is listed too), so an optional <em>probe</em> runs one
 * trivial agent turn in a throwaway sandbox. It costs a few cents and half a minute, so it is off by default and runs
 * once per start, not on every check.
 */
public final class SbxPreflight {

    private static final Logger LOG = Logger.getLogger(SbxPreflight.class);

    /** The first sbx release with {@code --cloud}. */
    static final int[] CLOUD_MINIMUM = {0, 45, 0};

    /** The secret the claude agent needs, in sbx's own name for it. */
    private static final String SECRET = "anthropic";

    private static final Pattern VERSION = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern SUMMARY = Pattern.compile("\\d+ passed.*");

    /**
     * What the checks found.
     *
     * @param ready    false when the local sandboxes cannot work at all (sbx missing, daemon unhealthy)
     * @param details  one line per check, in the order they ran
     * @param warnings what would break a part of the demo, such as the cloud, each saying what to do
     */
    public record Report(boolean ready, Map<String, String> details, List<String> warnings) {
    }

    private final SbxCli cli;
    private final SbxSettings settings;
    private final Duration cacheFor;
    private final boolean probe;
    private final Clock clock;

    private Report cached;
    private Instant cachedAt;
    private String probeResult;

    public SbxPreflight(SbxCli cli, SbxSettings settings, Duration cacheFor, boolean probe, Clock clock) {
        this.cli = cli;
        this.settings = settings;
        this.cacheFor = cacheFor;
        this.probe = probe;
        this.clock = clock;
    }

    /** The latest report, checking again only when the cached one is older than the cache time. */
    public synchronized Report report() {
        Instant now = clock.instant();
        if (cached == null || cachedAt.plus(cacheFor).isBefore(now)) {
            cached = check();
            cachedAt = now;
        }
        return cached;
    }

    /** Runs every check now, and the probe if it is enabled and has not run yet. */
    Report check() {
        Map<String, String> details = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        String version;
        try {
            version = cli.version();
        } catch (SandboxFailureException e) {
            details.put("sbx", "not available: " + e.getMessage());
            return new Report(false, details, List.of(e.getMessage()));
        }
        details.put("sbx", version);
        if (!atLeast(version, CLOUD_MINIMUM)) {
            warnings.add("sbx " + version + " is older than 0.45.0, the first release with --cloud. Update sbx to use the cloud.");
        }

        boolean ready = diagnose(Location.LOCAL, details, warnings, true);
        diagnose(Location.CLOUD, details, warnings, false);
        secret(Location.LOCAL, details, warnings);
        secret(Location.CLOUD, details, warnings);

        if (probe) {
            probeOnce(details, warnings);
        }
        return new Report(ready, details, warnings);
    }

    private boolean diagnose(Location location, Map<String, String> details, List<String> warnings, boolean required) {
        String key = (location == Location.CLOUD ? "cloud" : "local") + " diagnose";
        try {
            CommandResult result = cli.diagnose(location);
            if (result.succeeded()) {
                details.put(key, summary(result).orElse("ok"));
                return true;
            }
            String why = SbxErrors.hint(result.tail(), location).orElse("exit " + result.exitCode() + ": " + result.tail());
            details.put(key, "failed: " + why);
            warnings.add("`sbx " + (location == Location.CLOUD ? "--cloud " : "") + "diagnose` failed: " + why);
            return !required;
        } catch (SandboxFailureException e) {
            details.put(key, "failed: " + e.getMessage());
            warnings.add(e.getMessage());
            return !required;
        }
    }

    private void secret(Location location, Map<String, String> details, List<String> warnings) {
        String where = location == Location.CLOUD ? "cloud" : "local";
        String command = location == Location.CLOUD ? "sbx --cloud secret set anthropic --oauth" : "sbx secret set anthropic";
        try {
            boolean present = cli.secrets(location).lines().anyMatch(line -> line.toLowerCase().contains(SECRET));
            details.put(where + " secret", present ? SECRET + " is set" : SECRET + " is not set");
            if (!present) {
                warnings.add("No " + where + " credential for the agent. Set it with `" + command
                        + "`, then start a new sandbox.");
            }
        } catch (SandboxFailureException e) {
            details.put(where + " secret", "could not be listed: " + e.getMessage());
            warnings.add("Could not check the " + where + " credential: " + e.getMessage());
        }
    }

    /** One trivial agent turn in a throwaway cloud sandbox, which proves the credential works. At most once per start. */
    private void probeOnce(Map<String, String> details, List<String> warnings) {
        if (probeResult == null) {
            probeResult = runProbe(Location.CLOUD);
        }
        details.put("cloud probe", probeResult);
        if (!probeResult.equals("ok")) {
            warnings.add("The cloud probe turn failed: " + probeResult);
        }
    }

    private String runProbe(Location location) {
        String name = "harness-probe-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try {
            cli.create(location, name, settings.agent(), Duration.ofHours(1), "delete");
            List<String> command = new ArrayList<>(List.of(settings.agent(), "-p", "Reply with the single word OK",
                    "--output-format", "stream-json", "--verbose"));
            command.addAll(settings.agentArgs());
            ClaudeStreamParser parser = new ClaudeStreamParser(step -> { });
            CommandResult result = cli.exec(location, name, command, settings.turnTimeout(), parser::accept);
            if (parser.isError() || !(result.succeeded() || parser.resultText().isPresent())) {
                String said = parser.isError() ? parser.resultText().orElse("the agent reported an error") : result.tail();
                return SbxErrors.hint(said, location).map(hint -> hint + " (" + said + ")").orElse(said);
            }
            return "ok";
        } catch (SandboxFailureException e) {
            return e.getMessage();
        } finally {
            try {
                cli.remove(location, name);
            } catch (SandboxFailureException e) {
                LOG.warnf("Could not remove the probe sandbox %s; remove it with `sbx %srm %s`: %s", name,
                        location == Location.CLOUD ? "--cloud " : "", name, e.getMessage());
            }
        }
    }

    private static java.util.Optional<String> summary(CommandResult result) {
        return result.stdout().lines().map(String::strip).filter(line -> SUMMARY.matcher(line).matches()).reduce((a, b) -> b);
    }

    /** Whether the first version number found in {@code text} is at least {@code minimum}. */
    static boolean atLeast(String text, int[] minimum) {
        Matcher matcher = VERSION.matcher(text);
        if (!matcher.find()) {
            return true; // an unreadable version is not a reason to complain
        }
        for (int i = 0; i < 3; i++) {
            int found = Integer.parseInt(matcher.group(i + 1));
            if (found != minimum[i]) {
                return found > minimum[i];
            }
        }
        return true;
    }
}
