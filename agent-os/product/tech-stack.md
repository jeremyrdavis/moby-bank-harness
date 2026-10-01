# Tech Stack

## Frontend

- React 18.3.1 (vendored UMD), rendered through a Claude Design `.dc.html` component and its dc-runtime (`support.js`)
- Docker Trident design system (`window.Trident.*`, Tailwind v4 + semantic tokens)
- Zero-dependency Node 18+ static server (`serve.mjs`)
- Lives in `moby-bank-prototype/`

## Backend

Two implementations, built sequentially, behind one HTTP + server-sent-events API (`moby-bank-quarkus/openapi.yaml` is the contract):

1. **Java / Quarkus: done** (`moby-bank-quarkus/`). Quarkus 3.40.1, Java 25, Maven (wrapper). Extensions: `rest-jackson`, `smallrye-openapi`, `smallrye-health`. Layered as `domain` / `application` / `infrastructure` / `interfaces.rest` per the `ddd-foundations` skill. Drives sandboxes by running the `sbx` CLI, and reads OneDrive with the JDK `HttpClient` and Jackson (no REST-client extension).
2. **Python: to do.** To be scaffolded with the `python-app` skill and to match the same API.

Both run or drive agents in Docker Sandboxes (local and cloud).

## Database

None. The Quarkus backend keeps sessions and connected folders in memory behind repository interfaces (with an optimistic version check), so state is lost on restart. Adding persistence means a new repository implementation; the domain does not change.

## Other

- Docker Sandboxes: local and cloud agent runtimes, driven by the `sbx` CLI (`sbx --cloud` for cloud; `sbx move` to move a session); the agent inside is the Claude Code CLI, run headless
- Microsoft OneDrive integration for document sources, through Microsoft Graph (access token or Entra client credentials)
- Testing: JUnit 5 and RestAssured for the backend (with in-process stand-ins for Graph and `sbx`); `node:test` for the UI
- A Sandbox kit for the JDK, Maven, Quarkus CLI and JBang: `SANDBOX-KIT.md`
- Build skills and references:
  - https://github.com/jeremyrdavis/agentic-skills-for-python
  - https://github.com/jeremyrdavis/agentic-skills-for-quarkus
  - https://github.com/jeremyrdavis/ddd-foundations
  - https://github.com/jeremyrdavis/ddd-value-objects
  - https://github.com/jeremyrdavis/ddd-aggregates
  - https://github.com/jeremyrdavis/ddd-services
