# Real-host verification checklist

Run on a host with `sbx` 0.45.0 or later, signed in (`sbx login`), with a cloud subscription. Paste each command's output (or the exact error) back into the session. Use a throwaway name such as `vtest`. Remove test sandboxes at the end: `sbx rm vtest-local`, `sbx --cloud rm <name from sbx --cloud ls>`.

Record, for every step: the command, the exit code, stdout and stderr.

## 1. Version, access and secrets

```bash
sbx --version
sbx --cloud diagnose
sbx secret ls            # local secrets (if the subcommand exists)
sbx --cloud secret ls    # is the cloud anthropic secret listed? what does the output look like when it is missing?
sbx --help               # the exact subcommands for checking secrets
```

If the cloud anthropic secret is not set: `sbx --cloud secret set anthropic`.

## 2. Create with no workspace

```bash
sbx create --name vtest-local claude
sbx --cloud create --name vtest-cloud --ttl 1h claude
sbx --cloud create --name vtest-cloud2 --ttl 1h --on-timeout stop claude   # is --on-timeout accepted on create?
```

## 3. Listing

```bash
sbx ls -q
sbx --cloud ls -q                 # exact name form: plain name, agent/name, with a suffix?
```

## 4. Headless agent run and stream

```bash
sbx exec vtest-local claude -p "Say hello in one line" --output-format stream-json --verbose --dangerously-skip-permissions
sbx exec vtest-local claude -p "What did I just ask?" --output-format stream-json --verbose --dangerously-skip-permissions --resume <session_id from the first run>
sbx --cloud exec vtest-cloud claude -p "Say hello in one line" --output-format stream-json --verbose --dangerously-skip-permissions
```

Record the full JSON lines of one run (redact anything private). Note whether the `system`/`init` line carries `session_id`, and whether the final `result` line has `result`, `is_error` and `session_id`. Include anything the earlier local run showed that differs from this.

## 5. Moves

Create a file in the local sandbox first so the transfer is visible: `sbx exec vtest-local sh -c 'echo hi > /home/agent/workspace/marker.txt'`.

```bash
time sbx move vtest-local --to cloud --name vmove --force --ttl 8h --on-timeout stop
sbx --cloud ls -q                                        # the destination's name
sbx --cloud exec <destination> cat /home/agent/workspace/marker.txt
sbx --cloud exec <destination> claude -p "What did I ask earlier?" --output-format stream-json --verbose --dangerously-skip-permissions --resume <session_id>   # does the conversation resume?
sbx ls                                                   # is the local source stopped?
time sbx move <destination> --to local --name vback --force
sbx ls -q ; sbx --cloud ls                               # is the cloud source stopped or still running?
```

## 6. Stopped cloud sandbox

After a move with `--on-timeout stop`, or after a short-TTL expiry, run `sbx --cloud exec <name> echo hi` on the stopped sandbox. Does it start again by itself, or fail? If it fails, record the error and what command starts it (`sbx --cloud --help`).

## 7. Failure texts (exact stderr and exit code)

```bash
sbx --cloud exec does-not-exist echo hi                  # a sandbox that does not exist
sbx --cloud rm vtest-cloud && sbx --cloud exec vtest-cloud echo hi      # deleted
sbx move does-not-exist --to cloud --force               # move of a missing source
```

Also, if you can reproduce them: let a TTL of 1h pass with `--on-timeout delete` and exec into it; a platform mismatch on a move (`--platform` differing from the local one); a cloud-to-local move with too little disk; a move of a sandbox above the largest cloud size. For each, paste the message. These become the patterns in `SbxErrors`.

---

# Results

## 1. Version, access and secrets (recorded 2026-10-14)

- `sbx version` prints `v0.47.0 0411f50ee4700fe7bd37e6e7e3aced563e850ca9`: a leading `v`, then the version, then a commit. The preflight must parse the `vX.Y.Z` token. 0.47.0 meets the 0.45.0 minimum; 0.48.0 is available.
- `sbx diagnose` prints sections (Installation, Connection) of lines marked `✓` or `!`, and ends with `4 passed · 1 warnings`. Checks include `Authentication`, `Cloud API` and `Cloud entitlement`. An available update is a warning (`!`), not a failure, so the preflight must not fail on warnings. **Not yet known:** the exit code, the failure marker, and whether `sbx --cloud diagnose` behaves the same (the pasted output was headed `sbx diagnose`).
- `sbx secret ls` (local) columns: `SCOPE TYPE NAME SECRET`. The anthropic row is `(global) service anthropic (oauth configured)`.
- `sbx --cloud secret ls` columns: `SCOPE SERVICE TYPE SECRET CREATED`. The anthropic row is `(global) anthropic token *** <date>`. The cloud anthropic secret is set, so the preflight can look for a row whose service is `anthropic`. The two listings have different layouts, so parse each separately.
- `sbx --help` shows commands the plan did not use:
  - `ttl`: "Inspect or extend a cloud sandbox's TTL".
  - `attach`: "starting it first if it is stopped" (a hint that `exec` may not start a stopped sandbox; step 6 will say).
  - `stop`, `rm`, `prune`.
  - `volume`: persistent volumes, cloud-only.
  - The global `--cloud` flag is "supported by a growing set of verbs".
