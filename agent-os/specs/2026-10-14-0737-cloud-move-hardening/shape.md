# Cloud move hardening — Shaping Notes

## Scope

Verify and harden moving a session between local and cloud Docker Sandboxes on a real `sbx` host:

1. Real-host verification of the `sbx` commands, the agent's headless flags and stream format, cloud sandbox names, create without a workspace, and `exec` on a stopped cloud sandbox.
2. Hardening: cloud TTL and `--on-timeout` defaults; detecting a missing or expired sandbox and recovering visibly; preflight diagnostics; clear errors for platform, disk and size limits.
3. UI: Move to local in the htmx page; the recovery notice.
4. Fakes that can simulate expiry and a failed move.

Roadmap items are already marked built; this spec makes them verified and robust. It lands before the Python backend because it adds a field to `openapi.yaml`.

## Decisions

- **Visibility of recovery:** a nullable `notice` on the assistant message. It is persisted, styled in the page, and kept out of the paragraphs replayed into later prompts. Rejected: a transient SSE event (lost on reload) and a prose paragraph (looks like agent text, pollutes history).
- **Cloud lifetime:** default `harness.sbx.cloud-ttl=8h` and new `harness.sbx.cloud-on-timeout=stop`, passed to `create` and `move` for cloud destinations. The docs say the service default is 1 hour with deletion on expiry.
- **Source cleanup after a move:** deferred to Phase 2. `harness.sbx.remove-source-after-move` stays a proposed setting only.
- **Verification is a hand-off:** `sbx` is not installed in the dev sandbox. The user runs `verification.md` on a host with `sbx` 0.45.0+ and a cloud subscription and pastes the output back; error strings are taken from it, not guessed.
- **Out of scope:** OneDrive and the `graph/` package (another session), the Python backend, persistence across restarts, streaming partial agent text.

## Context

- **Visuals:** None. Follow the existing `index.html` styling and move toast.
- **References:** the sbx adapters, the move flow, and the two earlier specs (see `references.md`).
- **Product alignment:** consistent with `agent-os/product/`: one agent, local or cloud, a session moves either way. Roadmap Phase 1 rows are updated; source cleanup stays in Phase 2.
- **Facts from Docker's `sbx move` docs** that shaped the plan: sources are not deleted; managed secrets, processes and network rules do not transfer; the snapshot keeps its platform; cloud to local can need up to 32 GiB of temporary disk; cloud destinations always expire; `move` takes no global `--cloud` flag.

## Standards Applied

`agent-os/standards/index.yml` is empty, so no standards files apply. See `standards.md` for the rules used instead.
