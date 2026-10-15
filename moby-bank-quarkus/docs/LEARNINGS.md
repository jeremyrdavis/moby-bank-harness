# Learnings: moving sandboxes to the cloud

What we learned while verifying and hardening local ↔ cloud session moves (branch `feature/cloud-move-hardening`, spec in `agent-os/specs/2026-10-14-0737-cloud-move-hardening/`). The raw command-by-command record is `verification.md` in that folder. This file is the short version, with what it changed in the code.

Everything under "Observed" was seen on a real host (`sbx` v0.47.0 and v0.48.0, with a cloud subscription) on 2026-10-14 and 2026-10-15. Anything marked **unverified** comes from Docker's documentation or from reasoning, and was not run.

## Summary

- Moves work in both directions and files travel with them. The agent's conversation does not travel; the adapter replays history after a move.
- Cloud→local moves are slow and uneven (5m12s and 17m06s). A single HTTP request cannot wait for them.
- A cloud sandbox can disappear. The adapter now notices, rebuilds it, and tells the analyst.
- The cloud agent needs its own credential. Without it, a turn fails after about 3 minutes of retries.
- The local agent and the cloud agent were different agents until we gave them the same flags.
- The automated tests could not have found most of this. A real host did.

## Observed on a real host

### Credentials

- **The cloud has its own secret store.** `sbx secret ls` (local) and `sbx --cloud secret ls` (cloud) are separate, with different columns. A managed secret is not copied by a move.
- **A stale cloud secret fails slowly.** The turn got `401 authentication_failed`, "Invalid API key · Fix external API key". The agent retried 10 times with growing delays and gave up after about **175 s**, so the analyst waits three minutes for an error.
- **The fix that worked:** `sbx --cloud secret set anthropic --oauth`. It replaced the secret in place (type `token` → `oauth_refresh`, same created time), with no `rm` first. Whether a changed secret reaches an *existing* cloud sandbox is unknown, because the sandbox was recreated before the working turn. The error hint therefore says "set the secret, then start a new sandbox".
- **Secret types seen:** `token`, `oauth_refresh` (cloud) and `(oauth configured)` (local). A preflight must accept an `anthropic` row of any type.

### Creating and naming

- `sbx create --name X claude` works with no workspace, locally and with `--cloud`. The first local create also pulls an image, so it can be slow.
- **`--on-timeout` is accepted on `create`** as well as `move`, so both get the cloud lifetime flags.
- **Cloud names carry an agent prefix.** `sbx --cloud ls -q` prints `claude/vtest-cloud`; local prints plain names. `create` and `move` added **no random suffix**: the destination is exactly the `--name` asked for.
- **The bare name works for commands.** `sbx --cloud exec vtest-cloud …` is accepted. The `claude/…` form was never run, so commands use the bare name (`SandboxRegistry.bareName`). **Unverified:** the prefixed form.
- The local sandbox naming `harness-<12 hex>-g<n>` is confirmed by a leftover sandbox in `sbx ls -q`.

### The agent's headless run

- The stream matches `ClaudeStreamParser`: `system/init` (has `session_id`), `assistant`, `rate_limit_event`, `result` (`result`, `is_error`, `session_id`). `system/api_retry` lines appear during failures.
- **`--resume` returns a new `session_id` on every run.** The conversation carries, but the adapter must store the id from each run, not keep the first one.
- `sbx` and the agent print non-JSON lines before the stream (`Sandbox … started`, `Starting cloud sandbox…`, a "claude.ai connectors are disabled" warning, "no stdin data received in 3s"). The parser ignores lines that are not JSON, which is why none of them matter.

### Local and cloud agents were not the same agent

| | Local (before) | Cloud |
|---|---|---|
| Model | `claude-sonnet-5` | `claude-opus-5-5[1m]` |
| Auth | claude.ai login (`apiKeySource: none`) | API-key helper |
| MCP servers | the host's Gmail, Slack, Notion, Drive, Calendar, Granola | none |
| Input per turn | about 42k tokens (about $0.09) | about 5k tokens (about $0.027) |

The cause is auth: the cloud agent uses an API-key helper, so claude.ai connectors are off; the local agent uses the login, so the user's organization connectors load. That matters for a bank demo (a local agent that can read the analyst's mail is not the cloud agent) and for cost.

**Fix:** `--model claude-opus-5-5 --strict-mcp-config` as the default `harness.sbx.agent-args`. Both flags work in both locations, so one value serves both. **Still different:** the cloud default model is the `[1m]` variant, local with the flag is not; local has four extra built-in tools (Monitor, PushNotification, RemoteTrigger, ShareOnboardingGuide); the local skills list was never captured, and the local sandbox links the host's skills read-only, so skills probably differ.

### Moves

- **Local→cloud: 27 s.** Warnings on stderr, none fatal: managed secrets not copied, credentials inside copied files still travel, local network rules not copied, "source limits are missing for CPU and memory; using cloud defaults".
- **No size block.** A local sandbox created with no limits was reported as 18 CPU / 32 GiB, but `move` used the cloud default shape (`small`, 2 vCPU / 4 GiB). **Unverified:** what a genuinely oversized sandbox prints.
- **Files travel both ways**, including edits made in the cloud. The home directory (`CLAUDE.md`, `.claude`, `.gitconfig`, `workspace/`) came across. An early "file missing" result was our mistake: the marker was written after the move.
- **Cloud→local: 5m12s, then 17m06s.** The duration varies by 3x and cannot be planned around. Local layer reuse may need up to 32 GiB of temporary disk.
- **The cloud source is stopped, not deleted,** after a cloud→local move. Whether the local source is stopped after a local→cloud move was not recorded.
- **Each move to the cloud leaves a backing template** (`tmpl_…`) behind. Remove it with `sbx --cloud template rm <id>` after deleting the sandbox. This is Phase 2 cleanup work.
- **Network policy and environment variables do not carry over** to a moved cloud sandbox.

### Lifetime and expiry

- `--ttl 8h --on-timeout stop` is honoured: "Expires in 7h59m; the sandbox will then be stopped (it can be started again later)".
- **A stopped cloud sandbox restarts on `exec`** in about 1 s. No start command is needed. Stopped sandboxes stay listed (one was a day old).
- **So expiry with `stop` is harmless; "gone" now means deleted:** `sbx rm`, `--on-timeout delete`, or a retention limit.
- **`sbx ttl` shows a maximum remaining lifetime** (`Expires in 6h2m (MAX remaining: 5h11m)`), so extending the TTL on every turn is not dependable. We dropped that idea.
- A local `exec` also starts a stopped local sandbox by itself.

### Failure texts

- **Missing sandbox:** `error: sandbox '<name>' not found` then `  try: sbx [--cloud] ls`, exit 1, for local and cloud.
- **`sbx diagnose`:** sections INSTALLATION, PLATFORM, STORAGE, CONNECTION, lines `✓ name — detail`, ending `14 passed` (`4 passed · 1 warnings` when there are warnings). Exit 0 when all pass. An available update is a warning, not a failure.
- **`sbx version`** prints `v0.47.0 <commit>`: a leading `v`, so parse the `vX.Y.Z` token.

## What changed in the code because of it

| Finding | Change |
|---|---|
| Moves take 5–17+ minutes | `move-timeout` default 1h. A move that outlasts the page's 180 s wait returns a busy pane that polls every 5 s, and the toast finishes when the move ends. |
| Cloud sandboxes expire or get deleted | `--ttl 8h --on-timeout stop` on create and move. If a sandbox is gone, the turn recreates it, copies every shared file back in, replays the history, and sets a **notice** on the reply. |
| The analyst must see that | `notice` is a nullable, persisted field on the assistant message (`MessageDTO.notice`, in `openapi.yaml`). The page shows it as a note above the reply. It is not replayed into prompts. |
| A move whose source is gone | Not a failure: the session starts fresh at the destination with its history. |
| Cloud→local was impossible from the page | **Move to local** button. |
| Slow, vague failures | `SbxErrors` maps known `sbx` output to a short hint ("…: set the secret…") and keeps the raw tail. |
| A bad host fails minutes in | `SbxPreflight`: version, `diagnose`, cloud secret, and an optional probe turn. Shown in `/q/health/ready` and a startup log, never fatal. |
| The page tests could not show a lost sandbox or a refused move | `harness.fake.cloud-expiry-turns` and `harness.fake.move-failure`. |
| Local agent ≠ cloud agent | New default `agent-args`. |

## What the tests could and could not tell us

- The automated tests assert argument lists through a fake `CommandRunner`. They cannot show that the real CLI accepts them, so they would have passed with a wrong flag. The real host showed that the flags work, that names have a prefix, that `--resume` changes the session id, and how long moves take.
- The two things most likely to hurt a demo were found only by running it: the **3-minute 401 retry loop** and the **local agent reading the analyst's connectors**.
- Fake mode is still the way to see the notice and a failed move in the page without a real sandbox.

## Still unseen on a real host

- `sbx --cloud diagnose`, and the exit code of a *failing* `sbx diagnose`.
- `sbx --cloud rm` (used only by the optional preflight probe).
- Moving a *stopped* cloud sandbox (suggested: `time sbx move vtest-cloud --to local --name vback4 --force`).
- The exact text for a platform mismatch, a full disk and an oversized sandbox. `SbxErrors` uses documented wording for these, plus a generic fallback.
- Whether a changed cloud secret reaches an already-existing cloud sandbox.
- The `claude/<name>` prefixed form in commands.
- The whole app end to end with `-Dharness.sandbox.mode=sbx`: turn → move to cloud → turn → delete the cloud sandbox → turn (notice) → move to local → turn.
- The local skills list, for the parity check.
- The page in a real browser (checked only through fake-mode HTTP and tests).

## Open items

- **Cleanup after a move** (source sandbox, backing template, cloud `sbx volume`) is deferred to Phase 2. Until then, test sandboxes and templates pile up and need removing by hand.
- **The Python backend must match `openapi.yaml`**, which now has `MessageDTO.notice` (`type: [string, null]`). The prototype UI ignores the field.
- **Test sandboxes left on the host** (check `sbx ls` and `sbx --cloud ls` first): local `vtest-local`, `vback`, `vback2`, `vback3`, `harness-c08cdbecaade-g0`; cloud `vtest-cloud`, `vtest-cloud2`, `vmove`, `vmove2`, `vmove3`, `cloud-project`, `harness-511e6094398e-g1`; plus the `tmpl_…` templates.
- **The live UI test** ("connect another folder") uses up one folder per run, so it fails on a long-running server and passes on a fresh one. This predates the branch.
