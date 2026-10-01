# moby-bank-quarkus

Quarkus backend for the Moby Bank agent harness (implementation #1; a Python implementation follows). It serves the API the `moby-bank-prototype/` UI needs and drives an agent running in a Docker Sandbox, local or cloud. See `agent-os/specs/2026-09-30-1452-quarkus-mvp-backend/` for the spec.

## Requirements

- JDK 25
- Maven (the `./mvnw` wrapper is included)
- Optional: Quarkus CLI (`quarkus dev`)

## Run

```bash
./mvnw quarkus:dev          # http://localhost:8080 , Dev UI at /q/dev
./mvnw test                 # unit and @QuarkusTest tests
```

Useful endpoints today: `/q/health`, `/q/openapi`, `/q/swagger-ui`.

## Running against real Docker Sandboxes

By default everything is simulated (`harness.sandbox.mode=fake`). To drive real sandboxes, install the
[`sbx` CLI](https://docs.docker.com/ai/sandboxes/), sign in with `sbx login`, and start the app with:

```bash
./mvnw quarkus:dev -Dharness.sandbox.mode=sbx
```

- **Local and cloud** use the same commands; cloud adds the global `--cloud` flag (`sbx --cloud exec ...`).
  Check cloud access with `sbx --cloud diagnose`.
- **One sandbox per session**, created on its first turn and named `harness-<session>-g<generation>`. Attached
  files are copied into `<workspace-dir>/files` with `sbx cp`, then the agent runs headless:
  `sbx exec <sandbox> claude -p <prompt> --output-format stream-json --verbose`. Its tool calls appear as steps,
  and its final answer becomes the reply (the first Markdown table in it becomes the table).
- **Moving a session** runs `sbx move <sandbox> --to cloud|local`, which carries the whole sandbox filesystem
  (including the agent's conversation history) to a new sandbox. Processes and in-memory state do not travel,
  and the source sandbox is stopped, not deleted. Each move creates the next generation; sandboxes are found
  again after a restart by listing them.
- **The agent needs credentials inside the sandbox.** Configure them with `sbx secret`, and separately for cloud
  (credentials saved for local sandboxes are not available there). The sandbox's network policy must allow the
  agent's API host.
- Settings are in `application.properties` (`harness.sbx.*`).

What the automated tests cover: every `sbx` command the adapters issue (asserted exactly, through a fake command
runner), parsing of the agent's JSON stream, moves in both directions, and failure handling. What they cannot
cover is the real CLI, so check these on a machine with `sbx` installed: the agent's headless flags and stream
format, that `sbx ls -q` lists cloud sandboxes as `agent/name` (the adapters accept either form), and that
`sbx move --force` runs without a prompt.

## Layout

Packages follow the `ddd-foundations` layering under `com.mobybank.harness`: `domain`, `application`, `infrastructure`, `interfaces.rest`.

## Notes

- Generated with the Quarkus CLI 3.40.1 (`--no-code`), extensions: `rest-jackson`, `rest-client-jackson`, `smallrye-openapi`, `smallrye-health`.
- There is no database. State is held in memory.
