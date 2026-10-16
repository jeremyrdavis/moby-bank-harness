# moby-bank-quarkus

Quarkus backend for the Moby Bank agent harness (implementation #1; a Python implementation follows). It serves the JSON API the `moby-bank-prototype/` UI uses, and also the htmx page `index.html` (repository root) at `/` with the HTML endpoints behind it, and drives an agent running in a Docker Sandbox, local or cloud. Run the prototype UI against it with `npm start` in `moby-bank-prototype/` (see that README). See `agent-os/specs/2026-09-30-1452-quarkus-mvp-backend/` for the spec.

## Documentation

Detailed documentation is in [`docs/`](docs/README.md): [onboarding for new developers](docs/ONBOARDING.md) (including a
guided code review), [architecture](docs/architecture.md), the [domain model](docs/domain-model.md), the
[API](docs/api.md), [configuration](docs/configuration.md), the [`sbx` and OneDrive integrations](docs/integrations.md),
[testing](docs/testing.md), and the generated [Javadoc](docs/apidocs/index.html).

## Requirements

- JDK 25
- Maven (the `./mvnw` wrapper is included)
- Optional: Quarkus CLI (`quarkus dev`)

## Run

```bash
./mvnw quarkus:dev          # http://localhost:8080 , Dev UI at /q/dev
./mvnw test                 # unit and @QuarkusTest tests
```

Open <http://localhost:8080/> for the htmx page (see [`docs/ui-fragments.md`](docs/ui-fragments.md)); it needs nothing else running. After editing `index.html` in dev mode, run `./mvnw resources:copy-resources@copy-index-page` to refresh the served copy.

The API is described in `openapi.yaml` (written at build time; commit changes to it). Useful endpoints: `/api/sessions`, `/api/folders`, `/q/health`, `/q/openapi`, `/q/swagger-ui`. The `/events` stream per session is documented on that operation.

## Running against real Docker Sandboxes

By default everything is simulated (`harness.sandbox.mode=fake`). To drive real sandboxes, install the
[`sbx` CLI](https://docs.docker.com/ai/sandboxes/), sign in with `sbx login`, and start the app with:

```bash
./mvnw quarkus:dev -Dharness.sandbox.mode=sbx
```

- **Local and cloud** use the same commands; cloud adds the global `--cloud` flag (`sbx --cloud exec ...`).
  The cloud needs sbx 0.45.0 or newer, a subscription and its own agent credential
  (`sbx --cloud secret set anthropic --oauth`). `/q/health/ready` shows whether the host is set up.
- **One sandbox per session**, created on its first turn and named `moby-bank-<session>-g<generation>`. Attached
  files are copied into `<workspace-dir>/files` with `sbx cp`, then the agent runs headless:
  `sbx exec <sandbox> claude -p <prompt> --output-format stream-json --verbose`. Its tool calls appear as steps,
  and its final answer becomes the reply (the first Markdown table in it becomes the table).
- **Moving a session** runs `sbx move <sandbox> --to cloud|local`, which carries the whole sandbox filesystem
  (including the agent's conversation history) to a new sandbox. Processes and in-memory state do not travel,
  and the source sandbox is stopped, not deleted. Each move creates the next generation; sandboxes are found
  again after a restart by listing them. Moving to the cloud takes about half a minute; moving back took 5 to 17
  minutes on a real host.
- **A cloud sandbox times out** after `harness.sbx.cloud-ttl` (8h) and is stopped, not deleted
  (`harness.sbx.cloud-on-timeout`); the next turn starts it again. A sandbox that is really gone is recreated from the
  conversation history, and the analyst sees a notice.
- **The agent needs credentials inside the sandbox.** Configure them with `sbx secret`, and separately for cloud
  (credentials saved for local sandboxes are not available there). The sandbox's network policy must allow the
  agent's API host.
- Settings are in `application.properties` (`harness.sbx.*`).

What the automated tests cover: every `sbx` command the adapters issue (asserted exactly, through a fake command
runner), parsing of the agent's JSON stream, moves in both directions, and failure handling. What they cannot
cover is the real CLI. The commands, the agent's headless flags and its stream, the listing forms, `--ttl` and
`--on-timeout`, both moves (with `--force`, no prompt) and starting a stopped cloud sandbox were run by hand on a real
host and are recorded in
[`verification.md`](../agent-os/specs/2026-10-14-0737-cloud-move-hardening/verification.md). **Still to check on a host
with `sbx`:** the app itself end to end in `sbx` mode; `sbx --cloud diagnose`; `sbx --cloud rm` (used only by the
optional probe); moving a stopped cloud sandbox; and the exact `sbx` text for platform, disk and size failures.

## Reading documents from OneDrive

By default the folder library is the prototype's demo data (`harness.documents.mode=fake`). To read a real OneDrive
through Microsoft Graph, start with `-Dharness.documents.mode=graph` and configure **one** of:

- **A ready-made token** (quickest for a demo): set `harness.graph.access-token`. A token from Graph Explorer with
  the `Files.Read.All` permission works, and expires in about an hour.
- **An Entra app registration** (client credentials): register an app, grant it the **application** permission
  `Files.Read.All` with admin consent, and set `harness.graph.tenant-id`, `harness.graph.client-id` and
  `harness.graph.client-secret` (from the environment: `HARNESS_GRAPH_CLIENT_SECRET`, never a committed file). An
  app-only token has no signed-in user, so also set `harness.graph.drive` to `users/<upn>/drive` or `drives/<id>`.

`harness.graph.folders-root` limits the library to one folder's sub-folders. Per-user sign-in (the delegated
authorization-code flow) is not built: every analyst sees the one configured drive.

How it behaves: the library is the drive's (or `folders-root`'s) sub-folders; a folder's documents are its files;
an attached OneDrive file is found by exact name and downloaded through the short-lived link Graph returns, which is
requested without the bearer token. The token is only ever sent to the configured Graph host, and a paging link that
points elsewhere is refused. Files over `harness.graph.max-download-bytes` are refused. Failures reach the UI as
502 `document_source_failure`. In this mode the demo conversations still load, but no demo folders are pre-connected.

The tests run the real HTTP code against an in-process stand-in for Graph, the token endpoint and the download host.
They cannot confirm your tenant's permissions, so try it once against your own drive.

## Layout

Packages follow the `ddd-foundations` layering under `com.mobybank.harness`: `domain`, `application`, `infrastructure`, `interfaces.rest` (JSON) and `interfaces.web` (HTML for `index.html`, using Qute templates).

## Notes

- Generated with the Quarkus CLI 3.40.1 (`--no-code`), extensions: `rest-jackson`, `rest-qute`, `rest-client-jackson`, `smallrye-openapi`, `smallrye-health`.
- There is no database. State is held in memory.
