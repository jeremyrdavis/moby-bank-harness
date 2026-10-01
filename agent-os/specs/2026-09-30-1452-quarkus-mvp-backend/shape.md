# Quarkus MVP Backend — Shaping Notes

## Scope

The full MVP backend for the Moby Bank agent harness, implemented in Java/Quarkus (implementation #1; Python follows later). Every behavior in the `moby-bank-prototype` UI gets a real implementation: sessions and messages with agent steps, paragraphs and tables, local upload, OneDrive folder connect and attach, and the local→cloud move. On top of that come the roadmap items the prototype lacks: real local and cloud Docker Sandbox agents, cloud→local moves, and real OneDrive via Microsoft Graph.

## Decisions

- **Package layout:** `ddd-foundations` layering, `com.mobybank.harness.{domain, application, infrastructure, interfaces.rest}`. The `quarkus-ddd` layout (`domain.services`, `persistence`) is not used because it conflicts with `ddd-foundations` and `ddd-services`.
- **Persistence:** none for now. In-memory repositories behind domain repository interfaces; state is lost on restart.
- **Sandbox access:** shell out to the `sbx` CLI. Separate local and cloud adapters behind one domain port. A fake adapter is the default so the UI works without a sandbox.
- **Contract:** language-neutral HTTP + SSE API, exported as `openapi.yaml`, so the Python implementation can match it.
- **Both move directions** (local→cloud and cloud→local) are in the MVP, modelled as `Session.beginMove(target)` where the target must differ from the current location.
- **Auth:** out of scope. `GET /api/me` is config-driven; Graph uses a configured token or client credentials.
- **Execution order:** only Task 1 (this spec) is done now. Tasks 2–10 wait until the user has a Sandbox kit with Java, Maven, the Quarkus CLI and JBang.

## Context

- **Visuals:** None. The prototype UI (`moby-bank-prototype/`) is the visual and behavioral reference.
- **References:** `moby-bank-prototype/Bank Agent Harness.dc.html` (`SEED`, `FOLDER_LIB`, `Component` handlers, `renderVals`). See `references.md`.
- **Product alignment:** Confirmed against `agent-os/product/` (mission, roadmap, tech stack): one agent, local or cloud, for engineering/platform teams; MVP includes both moves and OneDrive; Quarkus first, Python later; no database yet.

## Standards Applied

None. `agent-os/standards/index.yml` is empty.

## Open Items

- Exact `sbx` commands and flags for create, exec, transfer, and the cloud invocation.
- Entra app registration and token source for Microsoft Graph.
- JDK version and Maven availability (resolved by the Sandbox kit).
- Companion skills not installed: `ddd-repositories`, `quarkus-rest`, `quarkus-persistence`, `quarkus-testing`, `quarkus-logging`.

## What changed during implementation

The plan in `plan.md` is kept as written. These are the places the build departed from it, and why.

- **Branches:** one branch per task, stacked (`task-02-…` through `task-10-…`), so the last branch contains everything and the tasks can be merged together or in order.
- **Moves use `sbx move`.** The plan described packaging the context and copying files by hand. The CLI moves a whole sandbox between local and cloud itself, carrying the filesystem (and so the agent's conversation), so `SbxSandboxTransfer` calls it and then finds the new sandbox by name.
- **Cloud and local share one adapter.** The plan said separate adapters. `sbx` uses the same verbs for both and differs only by the global `--cloud` flag, so a single `SbxCli` covers both.
- **OneDrive uses the JDK `HttpClient`,** not the REST-client extension, which is removed: Graph pages by absolute link and downloads through a pre-authenticated URL, which a declarative client handles badly.
- **Ports added:** `DocumentCatalog.fetch` (to copy OneDrive files into a sandbox), and in `application`: `BackgroundRunner`, `SessionEventStream`, `UploadStore`. New domain exceptions: `StaleAggregateException`, `DocumentSourceException`.
- **Event stream:** the first SSE event is `ready`, sent after subscribing, so a client knows it will not miss what follows (and re-syncs on reconnect).
- **Tests:** ~280 backend tests and 44 UI tests, including layering tests that fail the build on a forbidden import. The UI has no browser in CI; a static template check, logic tests and a live test against the real backend stand in for one.
- **Two bugs the live UI test caught** that the unit tests missed: a stale conversation title and a stale shared-files list after events arrived.

Still open: the real `sbx` commands, a real Entra tenant, and the page in a browser (see `agent-os/product/roadmap.md`).
