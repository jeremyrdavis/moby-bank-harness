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

## 2. Create with no workspace (recorded 2026-10-14)

- **Local:** `sbx create --name vtest-local claude` works with no workspace. It prints `workspace none · no workspace bind mount`, image `docker/sandbox-templates:claude-code-docker`, and **cpu 18, memory 32 GiB** (the local defaults). Ends with `✓ Created sandbox vtest-local`. The first create also prints `✓ Image ready`, so a first create may pull an image; the 2m `command-timeout` is worth keeping in mind.
- **Cloud:** `sbx --cloud create --name vtest-cloud --ttl 1h claude` works. After a `configure 1 kit(s)` step (about 7s) it prints a bare sandbox id line (`sbx_…`) on its own, then `✓ Created sandbox claude/vtest-cloud (sbx_…, image …)`. The sandbox is reported as **`claude/vtest-cloud`**: the agent name is a prefix on the name that was asked for, and `create` added no random suffix.
- **`--on-timeout` is accepted on `create`**: `sbx --cloud create --name vtest-cloud2 --ttl 1h --on-timeout stop claude` succeeded with no warning. So Task 3 can pass it on both `create` and `move`. (That the stop behaviour actually keeps state is not yet shown; step 6 covers it.)
- **Two things to watch in later steps:**
  - The adapter creates and addresses a sandbox by the bare name `harness-<id>-g<n>`. Check in step 4 whether `sbx --cloud exec harness-… ` works with the bare name or needs `claude/harness-…`.
  - The local sandbox's recorded size is 18 CPU and 32 GiB. `sbx move` rounds that up to a cloud size and **blocks the move if it is above the largest cloud size**. Step 5 will show whether that bites; if it does, the fix is a `create` flag or a setting for the local sandbox's size (the move docs list no `--cpus` or `--memory` on `move`).
- Test sandboxes now existing, to remove at the end: `vtest-local` (local), `claude/vtest-cloud` and `claude/vtest-cloud2` (cloud).

## 3. Listing (recorded 2026-10-14)

- `sbx ls -q` prints plain names, one per line. It includes `harness-c08cdbecaade-g0` (left by the earlier local run, which confirms the `harness-<12 hex>-g<n>` naming on a real host) and `vtest-local`.
- `sbx --cloud ls -q` prints `claude/vtest-cloud` and `claude/vtest-cloud2`: the agent name and a slash in front, no random suffix on a sandbox made by `create`. `SandboxRegistry.generationOf` already strips everything up to the last `/`.

## 4. Headless agent run and stream (recorded 2026-10-14)

Local, `sbx exec vtest-local claude -p … --output-format stream-json --verbose --dangerously-skip-permissions`:

- Worked. Two lines before the JSON: `Sandbox vtest-local started successfully` (so **`exec` starts a stopped local sandbox by itself**) and `Warning: no stdin data received in 3s, proceeding without it…` (the agent CLI waiting on an open stdin; `ProcessCommandRunner` closes stdin, so it should not wait; which stream each line used is not known). `ClaudeStreamParser` ignores lines that aren't JSON, so neither matters.
- The stream is: `system`/`init` (has `session_id`), `assistant` (`message.content[]` with `text` blocks), `rate_limit_event`, then `result` with `result`, `is_error`, `session_id`, `subtype`, `terminal_reason`, `api_error_status`, `total_cost_usd`. This matches what the parser reads; `rate_limit_event` and `system` are ignored.
- **`--resume` works but gives a new `session_id` each run.** The second run (`--resume <first id>`) answered "You asked me to say hello in one line", so the conversation carried, and its `session_id` was different from the first. `SbxSandboxAgent` stores the id from every run, so the next turn resumes the latest one; that is correct, and a test should pin it.
- **The local agent is not the same agent as the cloud one.** Local: model `claude-sonnet-5`, `apiKeySource: none`, the host's skills, memory and **connected MCP servers (Gmail, Slack, Notion, Google Drive, Calendar, Granola)** with about 42k input tokens per turn (about $0.09 each). Cloud: model `claude-opus-5-5[1m]`, `apiKeySource: apiKeyHelper`, 22 built-in tools, no MCP servers, no skills beyond the defaults.

Cloud, `sbx --cloud exec vtest-cloud claude -p …`:

- **The bare name `vtest-cloud` is accepted** by `sbx --cloud exec` (the agent started and printed `init`). So the adapter, which creates and addresses `harness-…-g<n>` without the `claude/` prefix, works. The prefixed form `claude/vtest-cloud` was not run; the adapter uses it after a restart, when the name comes from `ls -q`.
- **The turn failed: `401 authentication_failed`, "Invalid API key · Fix external API key".** The agent retried 10 times with growing delays (2s, 4s, 8s, 16s, 36s, 32s, 39s, 32s) and gave up after about **175 seconds** (`duration_ms 175041`). The `result` line has `is_error: true`, `api_error_status: 401`, `terminal_reason: api_error`. The exit code was not recorded. The cloud anthropic secret is listed (created 2026-08-20, type `token`) but the key is rejected, so it is probably stale.
- The failure arrives as `system`/`api_retry` lines (with `error_status`) followed by the `result`. The parser ignores the retries, and the final message would read "The agent failed in <name>: Invalid API key · Fix external API key".

## 4b. Cloud credential attempt (recorded 2026-10-14)

- The API-key test against `api.anthropic.com` returned 401, so the key that was tried is not valid.
- `sbx --cloud secret set anthropic --oauth` was run (the `--oauth` flag is accepted for the anthropic service on the cloud store; Docker's credentials page documents it only for OpenAI). What the command printed was not captured.
- The command run afterwards was `sbx exec vtest-local …` (**local**, no `--cloud`). It succeeded exactly as before: model `claude-sonnet-5`, `apiKeySource: none`, the host's MCP servers, `is_error: false`, `Hello!`, about $0.09. It says nothing about the cloud sandbox. **The cloud turn is still unverified.**
- Open: `sbx --cloud secret ls` after the `--oauth` set (does the row's TYPE change from `token`?), and a cloud `exec` on a sandbox created after the set.

## 4c. Cloud turn works after `--oauth` (recorded 2026-10-14)

- `sbx --cloud secret ls` now shows `(global) anthropic oauth_refresh *** 2026-08-20 15:16:31`. The TYPE changed from `token` to `oauth_refresh` and the CREATED time stayed the same, so **`secret set` replaces the secret in place** (no `rm` needed first). Cloud secret TYPEs seen so far: `token`, `oauth_refresh`; local shows `(oauth configured)`. The preflight must accept an `anthropic` row of any type.
- `sbx --cloud exec vtest-cloud claude -p "Say hello in one line" …` **succeeded**: reply "Hello! What can I help you with today?", `is_error: false`, 2.4 s, $0.027, `apiKeySource: apiKeyHelper`, model `claude-opus-5-5[1m]`, 22 tools, no MCP servers. (It was not recorded whether the sandbox was recreated after the secret change; if it was not, a changed secret reaches an existing cloud sandbox.)
- Before the JSON the agent printed: `claude.ai connectors are disabled because ANTHROPIC_API_KEY or another auth source is set and takes precedence over your claude.ai login`. It is not JSON, so the parser ignores it. It **explains the local/cloud difference**: the cloud agent authenticates through an API-key helper, so claude.ai connectors are off; the local agent signs in with the claude.ai login (`apiKeySource: none`), so the user's organization connectors (Gmail, Slack, Notion, Drive, Calendar, Granola) load.
- Per-turn context is about 5k tokens in the cloud against about 42k locally (`cache_creation_input_tokens` 5008 against 22696 plus 19113 cached), so the local turn costs about 3.5x as much even on a smaller model.

## 4d. Making the local agent match the cloud one (recorded 2026-10-14)

- The cloud sandbox **was recreated** before the working cloud turn, so it is still unknown whether a changed secret reaches an existing cloud sandbox. The 401 message should say "set the secret, then start a new sandbox".
- Local `exec` with `--model claude-opus-5-5 --strict-mcp-config` printed an `init` line with **`mcp_servers: []`, no `mcp__claude_ai_*` tools, and `model: claude-opus-5-5`**. Both flags work in the local sandbox. The connectors are gone.
- Remaining differences from the cloud agent:
  - **Model:** the cloud default is `claude-opus-5-5[1m]` (the 1M-context variant); local with the flag is `claude-opus-5-5`.
  - **Tools:** local has four more built-in tools: `Monitor`, `PushNotification`, `RemoteTrigger`, `ShareOnboardingGuide` (they come with the claude.ai login).
  - **Skills and slash commands:** not captured in this run. The local sandbox links the host's skills read-only (`skills 2 entries`), so they probably still differ.
- Not yet run: the same flags in the **cloud** sandbox (the model flag in particular), and a check of `skills` in the `init` line.
