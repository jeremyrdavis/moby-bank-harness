# Configuration

Every setting is a Quarkus property, read from `src/main/resources/application.properties`. Override any of them at
start-up without editing the file:

```bash
./mvnw quarkus:dev -Dharness.sandbox.mode=sbx                       # a system property
HARNESS_SANDBOX_MODE=sbx ./mvnw quarkus:dev                         # an environment variable
java -Dharness.sandbox.mode=sbx -jar target/quarkus-app/quarkus-run.jar   # the packaged app
```

(An environment variable is the property name in upper case with dots and dashes replaced by underscores. Put `-D`
options **before** `-jar`; after it, Java passes them to the program, which ignores them.)

Keep secrets out of committed files: supply `harness.graph.client-secret` and `harness.graph.access-token` from the
environment (`HARNESS_GRAPH_CLIENT_SECRET`, `HARNESS_GRAPH_ACCESS_TOKEN`).

`DocumentationTest` checks that this page lists exactly the properties the code reads.

## Which implementation runs

| Property | Default | Values | Meaning |
|---|---|---|---|
| `harness.sandbox.mode` | `fake` | `fake`, `sbx` | What runs the agent and moves sessions: the simulation, or Docker Sandboxes through the `sbx` CLI |
| `harness.documents.mode` | `fake` | `fake`, `graph` | Where documents come from: the demo library, or OneDrive through Microsoft Graph |

Any other value stops the app at startup with a message naming the property and the allowed values.

## The app

| Property | Default | Meaning |
|---|---|---|
| `harness.user.name` | `Hermione Granger` | The analyst's name in the sidebar |
| `harness.user.role` | `Credit Research` | Their role |
| `harness.seed-demo-data` | `true` | Load the five demo conversations at startup. Also connects the first three demo folders, but only when `harness.documents.mode` is `fake` |

## The fakes' timing

The fake adapters imitate the prototype's timings. Values are milliseconds.

| Property | Default | Meaning |
|---|---|---|
| `harness.fake.agent-delay-ms` | `1800` | How long a fake agent turn takes, spread across its steps |
| `harness.fake.move-packaging-ms` | `1100` | The packaging stage of a fake move |
| `harness.fake.move-transfer-ms` | `1500` | The transfer stage of a fake move |

`application.properties` sets all three to `0` for the `test` profile (`%test.…`) so tests don't wait. To watch the
UI and event stream with shorter waits than the defaults, use about `300` each (the UI's live test does).

## Real sandboxes (`harness.sandbox.mode=sbx`)

| Property | Default | Meaning |
|---|---|---|
| `harness.sbx.binary` | `sbx` | The executable to run |
| `harness.sbx.agent` | `claude` | The agent a sandbox is created for, and the command run inside it |
| `harness.sbx.agent-args` | `--dangerously-skip-permissions` | Extra arguments on every agent run, split on whitespace. Empty means none |
| `harness.sbx.workspace-dir` | `/home/agent/workspace` | The sandbox's working directory; attached files are copied to `<dir>/files` |
| `harness.sbx.command-timeout` | `2m` | Timeout for `create`, `cp`, `ls` and the `mkdir` run |
| `harness.sbx.turn-timeout` | `10m` | Timeout for one agent turn |
| `harness.sbx.move-timeout` | `30m` | Timeout for one `sbx move` |
| `harness.sbx.cloud-ttl` | *(unset: the service's default)* | How long a cloud sandbox lives before it times out, for example `8h` |

Durations accept `30s`, `10m`, `2h`. Prerequisites and what to check on a real host are in
[integrations.md](integrations.md#docker-sandboxes-through-sbx).

## OneDrive (`harness.documents.mode=graph`)

Give it **one** of an access token or a client-credentials app registration. With neither (or half of the
credentials), the app fails at startup naming the properties it needs.

| Property | Default | Meaning |
|---|---|---|
| `harness.graph.access-token` | *(unset)* | A ready-made bearer token, used as-is. Quickest for a demo; it expires in about an hour |
| `harness.graph.tenant-id` | *(unset)* | The Entra tenant, used to build the token endpoint URL unless `token-url` is set |
| `harness.graph.client-id` | *(unset)* | The Entra app registration's client id |
| `harness.graph.client-secret` | *(unset)* | Its client secret |
| `harness.graph.token-url` | `https://login.microsoftonline.com/common/oauth2/v2.0/token` | The token endpoint, for tests or sovereign clouds |
| `harness.graph.base-url` | `https://graph.microsoft.com/v1.0` | The Graph root |
| `harness.graph.drive` | `me/drive` | Which drive: `me/drive` needs a signed-in user's token; with client credentials use `users/<upn>/drive` or `drives/<id>` |
| `harness.graph.folders-root` | *(unset: the drive root)* | A folder path whose sub-folders form the library, for example `Credit Research/Coverage` |
| `harness.graph.request-timeout` | `30s` | Timeout for each HTTP request |
| `harness.graph.max-download-bytes` | `52428800` | The largest file that will be downloaded (50 MiB) |

## HTTP and the API document

Set in `application.properties`, these are standard Quarkus properties rather than `harness.*` ones:

| Property | Value | Meaning |
|---|---|---|
| `quarkus.http.cors.enabled`, `.origins`, `.methods`, `.headers` | on; `http://localhost:4173`, `http://127.0.0.1:4173`; `GET,POST,OPTIONS`; `Content-Type,Accept` | Lets the UI call the API from another origin. Add your UI's origin here if you serve it elsewhere |
| `quarkus.http.limits.max-body-size` | `50M` | The largest request body, which bounds uploads |
| `quarkus.smallrye-openapi.store-schema-directory` | `.` | Writes `openapi.yaml` and `openapi.json` into the module root at build time. Commit the `.yaml`; the `.json` is git-ignored |

## Two modes at a glance

| You want | Run |
|---|---|
| The demo, nothing installed | `./mvnw quarkus:dev` |
| Real sandboxes, demo documents | `./mvnw quarkus:dev -Dharness.sandbox.mode=sbx` |
| Fake sandboxes, your OneDrive | `HARNESS_GRAPH_ACCESS_TOKEN=… ./mvnw quarkus:dev -Dharness.documents.mode=graph` |
| Both real | add both `-D` options and the settings above |
