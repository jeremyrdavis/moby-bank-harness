# Product Roadmap

## Phase 1: MVP

Status of each item in the Quarkus implementation (`moby-bank-quarkus/` with the UI in `moby-bank-prototype/`).

| Item | Status |
|---|---|
| Chat UI with agent traces | Done. Live over server-sent events; the agent's steps show as they happen. |
| Real local sandbox backend | Built (`harness.sandbox.mode=sbx`). The commands, the agent's headless flags and its stream were run by hand on a real host (sbx 0.47/0.48) and match the adapter; **the app itself has not yet been run end to end against real sandboxes**. |
| Real cloud sandbox backend | Built with the same adapter (`sbx --cloud`). Create, exec and a headless agent turn worked on a real host once the cloud secret was set (`sbx --cloud secret set anthropic --oauth`). A stopped cloud sandbox starts again on the next `exec`. |
| Move session local → cloud | Done in the app, API and page. A real `sbx move --to cloud` took about 30 s and carried files. Cloud sandboxes now get `--ttl 8h --on-timeout stop`. |
| Move session cloud → local | Done in the app, API and page (**Move to local**). A real `sbx move --to local` carried files but took **5 to 17 minutes**, so the page follows a slow move to its end. |
| Recover from a lost sandbox | A sandbox that no longer exists is recreated from the conversation history, shared files are copied in again, and the analyst sees a **notice** on the reply (`notice` on the message; Python backend must match). |
| Host checks and clear errors | `/q/health/ready` and a startup warning say what is missing (sbx, daemon, cloud secret; optional probe turn). Common `sbx` failures say what to do. |
| Integration with Microsoft OneDrive | Built (`harness.documents.mode=graph`, Microsoft Graph). Tested against a stand-in for Graph; **not yet run against a real tenant**. |

Everything runs with simulated sandboxes and OneDrive by default, so the demo needs nothing installed.

Backend delivery: two implementations of the backend, built sequentially, one in Java/Quarkus and one in Python (see `tech-stack.md`). The Quarkus one is done; the Python one is next and must match `moby-bank-quarkus/openapi.yaml`.

### Verify on a real host before relying on it

- the app end to end against real sandboxes (a turn, a move to the cloud, a turn, a move back, a turn), and what is still unseen: `sbx --cloud diagnose`, `sbx --cloud rm`, moving a *stopped* cloud sandbox, and the exact `sbx` text for platform, disk and size failures (`moby-bank-quarkus/README.md`, `agent-os/specs/2026-10-14-0737-cloud-move-hardening/verification.md`)
- a real OneDrive drive with the intended Entra app permissions
- the UI in a browser: the page was tested through its logic and template, not rendered

## Phase 2: Post-Launch

To be determined

Known gaps, not committed work: per-user sign-in (every analyst sees the one configured drive), cleaning up what a move leaves behind (the stopped source sandbox and, for a move to the cloud, a backing cloud template), cloud `sbx volume` for files that outlive a sandbox, and showing the agent's text as it streams.
