package com.mobybank.harness.infrastructure.sbx;

import com.mobybank.harness.domain.Location;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns what {@code sbx} (or the agent inside a sandbox) printed into a sentence that says what to do. The raw text is
 * always kept next to it, so an unrecognised failure loses nothing.
 *
 * <p>Patterns marked <em>seen</em> were captured on a real host (sbx 0.47/0.48, 2026-10); the others follow Docker's
 * documentation and are expected to match, but have not been seen yet.
 */
final class SbxErrors {

    private record Rule(Pattern pattern, Function<Location, String> hint) {
    }

    private static Rule rule(String regex, Function<Location, String> hint) {
        return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL), hint);
    }

    private static String secretCommand(Location location) {
        return location == Location.CLOUD ? "sbx --cloud secret set anthropic --oauth"
                : location == Location.LOCAL ? "sbx secret set anthropic"
                : "sbx secret set anthropic (add --cloud for the cloud)";
    }

    private static final List<Rule> RULES = List.of(
            // seen: "error: sandbox 'no-such-sandbox' not found" + "try: sbx --cloud ls", exit 1
            rule("sandbox '[^']*' not found", where -> "The sandbox no longer exists (it was deleted, or removed when it "
                    + "timed out). Start a new one."),
            // seen: the agent's result when the cloud secret was missing or stale: "Invalid API key · Fix external API key"
            rule("invalid api key|fix external api key|authentication_error|status[: ]+401|http 401|\\(401\\)", where -> "The agent's credential was "
                    + "rejected (401). Set it with `" + secretCommand(where) + "`, then start a new sandbox: a "
                    + "sandbox that is already running may keep the old one."),
            // from the docs and the harness's own earlier wording
            rule("not authenticated|no (anthropic )?(secret|credential)|run sbx secret set", where ->
                    "The agent has no credential. Set it with `" + secretCommand(where) + "`, then start a new sandbox."),
            rule("not (signed|logged) in|sbx login|login required|sign in to docker", where -> "Not signed in to Docker "
                    + "Sandboxes. Run `sbx login` on the host that runs the backend."),
            rule("platform.*(mismatch|differ|does not match|incompatible)|(mismatch|differ).*platform", where ->
                    "The source and the destination run on different platforms (for example amd64 and arm64), which "
                    + "`sbx move` cannot cross. Use the same platform on both sides."),
            rule("no space left|not enough (disk )?space|insufficient (disk )?space|disk (is )?full", where ->
                    "The host is out of disk space. Moving a cloud sandbox back to local needs up to 32 GiB of temporary "
                    + "space; free some and try again."),
            rule("(larger|bigger|exceeds?|above) .*(largest|maximum) .*(cloud )?(size|shape)|too large for (the )?cloud",
                    where -> "The sandbox is larger than the biggest cloud size, so it cannot move to the cloud. "
                            + "Create the local sandbox with smaller limits."),
            rule("cannot connect to .*(daemon|sandboxd)|(daemon|sandboxd) (is )?not running", where ->
                    "The Docker Sandboxes daemon is not running. Start Docker Sandboxes (`sbx diagnose` shows why)."));

    private SbxErrors() {
    }

    /** A sentence saying what to do about this output, if it is a failure the harness recognises. */
    static Optional<String> hint(String output, Location location) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        return RULES.stream().filter(rule -> rule.pattern().matcher(output).find())
                .map(rule -> rule.hint().apply(location)).findFirst();
    }
}
