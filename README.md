# Moby Bank agent harness

A demo for engineering and platform teams of a **custom agent harness**: a chat UI for a fictional bank credit analyst, backed by an agent that runs in a **Docker Sandbox, locally or in the cloud**. A session can move between the two, in either direction, and the agent can read documents from OneDrive.

| Folder | What it is |
|---|---|
| [`index.html`](index.html) | A single-file htmx chat page. The Quarkus backend serves it at `/` and answers it with HTML fragments. |
| [`moby-bank-prototype/`](moby-bank-prototype/) | The chat UI, built on Docker's Trident design system. It uses the JSON API, as the Python backend's UI will. |
| [`moby-bank-quarkus/`](moby-bank-quarkus/) | The backend in Java/Quarkus. A Python backend is planned; both implement `moby-bank-quarkus/openapi.yaml`. |
| [`moby-bank-quarkus/docs/`](moby-bank-quarkus/docs/README.md) | Backend documentation: **[ONBOARDING.md](moby-bank-quarkus/docs/ONBOARDING.md)** for new developers, architecture, API, configuration, integrations, testing, and the Javadoc. |
| [`agent-os/`](agent-os/) | Product docs (mission, roadmap, tech stack) and the spec for the Quarkus backend. |
| [`SANDBOX-KIT.md`](SANDBOX-KIT.md) | How to build a Docker Sandbox kit with Java, Maven, the Quarkus CLI and JBang. |

## Try it

Everything is simulated by default (the agent, the sandboxes and OneDrive), so nothing needs installing beyond Java 25 and Node 22.

```bash
# terminal 1: the backend, on http://localhost:8080 (it also serves index.html)
cd moby-bank-quarkus && ./mvnw quarkus:dev

# terminal 2, optional: the prototype UI, on http://localhost:4173
cd moby-bank-prototype && npm start
```

Open <http://localhost:8080> for the htmx page, or <http://localhost:4173> for the prototype. Pick a conversation, send a message with an attached file, and use **Move to cloud** in the header (the htmx page offers that direction; the prototype also has **Move to local**).

To use real Docker Sandboxes or a real OneDrive, see [`moby-bank-quarkus/README.md`](moby-bank-quarkus/README.md): `-Dharness.sandbox.mode=sbx` and `-Dharness.documents.mode=graph`.

## How it fits together

```
 index.html (htmx) ─── HTML fragments ──────────────┐
                                                    ▼
 UI (Trident, React) ── HTTP + server-sent events ──▶  backend ──▶ SandboxAgent ──▶ sbx exec claude …   (local or --cloud)
                                                          │    ├─▶ SandboxTransfer ─▶ sbx move --to cloud|local
                                                          │    └─▶ DocumentCatalog ─▶ Microsoft Graph (OneDrive)
                                                          └── in-memory state; fakes stand in for all three by default
```

## Status

The UI, the API, the simulated mode and both move directions work and are tested (about 320 backend tests and 44 UI tests). The real `sbx` adapters, the real OneDrive adapter and the page in a browser have **not** been checked against the real thing; [`agent-os/product/roadmap.md`](agent-os/product/roadmap.md) lists what to verify.

## Working on it

[`CLAUDE.md`](CLAUDE.md) explains the architecture and the non-obvious parts of both modules.
