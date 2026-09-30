# Plan: Quarkus MVP backend for the Moby Bank agent harness

## Context

`moby-bank-prototype/` is a front-end-only demo: sessions, agent replies, OneDrive picking and the local→cloud move are all simulated with `setTimeout` in the `DCLogic` class of `Bank Agent Harness.dc.html`. The product goal (`agent-os/product/`) is a reference harness where one agent runs in a Docker Sandbox, local or cloud, and sessions move between the two. Quarkus is backend implementation #1 (Python comes later), so the HTTP contract must be language-neutral.

Scope chosen: the **full MVP backend**. Every behavior in the prototype gets a real implementation, plus the roadmap items the prototype lacks (real sandboxes, cloud→local move, real OneDrive).

Decisions from shaping:
- **Layout:** `ddd-foundations` layering: `com.mobybank.harness.{domain, application, infrastructure, interfaces.rest}`. The `quarkus-ddd` package layout is not used (it conflicts with ddd-foundations/ddd-services).
- **Persistence:** none. In-memory repositories behind domain repository interfaces (interface in `domain`, impl in `infrastructure`). State is lost on restart; acceptable for the demo.
- **Sandboxes:** driven by shelling out to the `sbx` CLI, with **separate local and cloud adapters** behind one domain port. A fake adapter is the default so the UI works with no sandbox installed.
- **Reference:** the prototype is the visual and behavioral spec. No other visuals.
- **Standards:** `agent-os/standards/index.yml` is empty, so none apply.
- **Skills to use while building:** `quarkus-app` (scaffold), `ddd-foundations`, `ddd-value-objects`, `ddd-aggregates`, `ddd-services`. Their companion skills (`ddd-repositories`, `quarkus-rest`, `quarkus-persistence`, `quarkus-testing`, `quarkus-logging`) are not installed, so follow the rules in the installed skills and keep repositories simple.

Environment caveats: `mvn`, `quarkus`, `jbang` and `sbx` are not on the PATH in this sandbox (JDK present, version unchecked). The real `sbx` adapters cannot be exercised here, so they are built behind a `CommandRunner` seam, unit-tested with a fake runner, and verified manually on the user's host.

## Execution scope on approval

**Execute Task 1 only.** Tasks 2-10 stay in the plan but are deferred until the user has a Sandbox kit with Java, Maven, the Quarkus CLI and JBang (the user is setting that up; see the kit instructions given in the conversation). Do not scaffold or write any application code yet.

## Task 1: Save spec documentation

Create `agent-os/specs/2026-09-30-1452-quarkus-mvp-backend/` with:
- `plan.md`: this plan
- `shape.md`: scope, the decisions above, product alignment (confirmed), visuals: none
- `standards.md`: "No standards indexed" (index.yml is empty)
- `references.md`: pointer to `moby-bank-prototype/Bank Agent Harness.dc.html` (`SEED`, `FOLDER_LIB`, `Component` handlers, `renderVals`) and the installed skills
- `visuals/`: empty (prototype is the visual reference)

## Task 2: Scaffold the Quarkus app

Use the `quarkus-app` skill. New sibling folder `moby-bank-quarkus/` (Python later gets its own sibling). Confirm the JDK first and pick the Java version (the skills default to 25). Extensions, all official: `rest-jackson`, `smallrye-openapi`, `smallrye-health`, `rest-client-jackson` (Graph), `junit`/`rest-assured` for tests. No JPA, no Kafka. Install Maven/Quarkus CLI if the network allows; otherwise use the Maven wrapper that the generator emits. Add `.gitignore`, and a README section on running it.

## Task 3: Domain layer (`com.mobybank.harness.domain`)

Rules from `ddd-foundations`/`ddd-aggregates`/`ddd-value-objects`: plain POJOs/records, no `jakarta.*`, typed IDs, factory + `rehydrate()` on aggregates, invariants inside the root.

- **Value objects (records):** `SessionId`, `MessageId`, `Location` (`LOCAL|CLOUD`), `FileRef(name, source: UPLOAD|ONEDRIVE)`, `Step(kind: READ|COMPUTE, label)`, `ResultTable(cols, rows)`, `FolderId`.
- **Aggregate `Session`** (owns its `Message` entities): `title`, `location`, `updatedAt`, `messages`, `status` (`IDLE|RUNNING|MOVING`). Behavior: `start()`, `postUserMessage(text, files)`, `recordAgentReply(steps, paragraphs, table)`, `beginMove(target)` / `completeMove()`. Invariants: title derived from the first message (48 chars, as in the prototype); no message or move while `RUNNING`/`MOVING`; move target must differ from the current location (this enables both directions).
- **Aggregate `ConnectedFolders`** per user: connect/disconnect folder ids; references folder ids only.
- **Ports (interfaces):** `SessionRepository`, `ConnectedFoldersRepository`, `SandboxAgent` (`runTurn(session, message) → steps/paragraphs/table`), `SandboxTransfer` (`moveSession(session, from, to)` with progress callbacks), `DocumentCatalog` (folder library, files per folder, fetch file).
- **Events:** `SessionMoved`, `AgentReplied` (used to feed the SSE stream).

## Task 4: Application layer (`…application`)

Per `ddd-services`: `@ApplicationScoped` orchestration that owns the transaction-like boundary (no JPA here), loads aggregates, calls ports, returns result records and never calls REST types.
- `SessionApplicationService`: `create`, `list` (with Today / Previous 7 days / Earlier grouping rule, as the prototype), `get`, `sendMessage`, `attachUpload`, `move`.
- `FolderApplicationService`: `library` (with connected flag), `connect`, `connectedFiles`.
- Progress for long operations is published as application events; the REST layer turns them into SSE.

## Task 5: Infrastructure, default (fake) adapters (`…infrastructure`)

- `InMemorySessionRepository`, `InMemoryConnectedFoldersRepository`, seeded at startup from the prototype's `SEED` and `FOLDER_LIB` (fictional names already sanitized).
- `FakeSandboxAgent`: port of the prototype's canned reply (steps, paragraph, table when files are attached, ~1.8s delay).
- `FakeSandboxTransfer`: two-stage progress ("Packaging context", "Transferring files"), ~2.6s total.
- `FakeDocumentCatalog`: `FOLDER_LIB` data.
- Adapter selection by config/profile: `harness.sandbox.mode=fake|sbx`, `harness.documents.mode=fake|graph`, defaulting to `fake`.

## Task 6: REST interface and contract (`…interfaces.rest`)

Quarkus REST endpoints and DTOs only; map DTOs to commands; a single `ExceptionMapper` for domain errors (404 unknown session, 409 busy/moving/invalid move). Enable CORS for `http://localhost:4173`.

| Endpoint | Purpose (prototype behavior) |
|---|---|
| `GET /api/me` | analyst name/role for the sidebar card (config-driven; auth is out of scope) |
| `GET/POST /api/sessions`, `GET /api/sessions/{id}` | history, New conversation, open a session |
| `POST /api/sessions/{id}/messages` | Send (text + attached files); returns 202 |
| `POST /api/sessions/{id}/uploads` | local Upload (multipart) |
| `POST /api/sessions/{id}/move` `{target}` | Move to cloud / Move to local; 202 |
| `GET /api/sessions/{id}/events` | SSE: `thinking`, `step`, `message`, `move-progress`, `moved` |
| `GET /api/folders`, `POST /api/folders/connect`, `GET /api/folders/connected/files` | Connect dialog and Attach dialog |

Export the OpenAPI document to `moby-bank-quarkus/openapi.yaml` and commit it. It is the contract the Python implementation must match.

## Task 7: Real `sbx` adapters

- `CommandRunner` interface plus a `ProcessBuilder` implementation (timeouts, stderr capture, no shell interpolation of user input), and a fake for tests.
- `LocalSbxAgent` / `LocalSbxTransfer` and a separate `CloudSbxAgent` / `CloudSbxTransfer`, both implementing the domain ports, selected by `harness.sandbox.mode=sbx`.
- Spike first: on the user's host, inspect `sbx --help` and the commands for create, exec and file transfer, and settle the cloud invocation. Exact flags are an **open item**; the adapters are written against those findings.
- Move semantics: package session context (messages plus files) → start or attach the sandbox at the target → transfer → flip `Location`. Local copy is retained, as in the prototype toast.

## Task 8: OneDrive via Microsoft Graph

`GraphDocumentCatalog` using `rest-client-jackson`: list folders, list files, download files into the sandbox. Token source for the demo is a configured access token or client-credentials; per-user sign-in is out of scope. Needs an Entra app registration (**open item**: who provides it). Selected by `harness.documents.mode=graph`.

## Task 9: Wire the prototype to the API

In `moby-bank-prototype/Bank Agent Harness.dc.html`: replace the `setTimeout` simulations in `send`, `moveToCloud`, the folder picker and the initial `SEED`/`FOLDER_LIB` with `fetch` calls and an `EventSource` for `/events`. Add a **Move to local** action in the `isCloud` header block (today a disabled "Running in cloud" button) and update README/CLAUDE.md, which currently describe cloud→local as planned. Keep template expressions to paths/literals (runtime limitation); compute flags in `renderVals()`.

## Task 10: Tests, docs, roadmap

Domain unit tests (invariants, both move directions), application tests with fake ports, `@QuarkusTest` REST tests against the fake profile including SSE, and adapter tests using the fake `CommandRunner`. Update `agent-os/product/roadmap.md` and `tech-stack.md` (Quarkus details, database = none).

## Verification

1. `./mvnw test` passes in `moby-bank-quarkus/`.
2. `./mvnw quarkus:dev`, then curl the flows on the fake profile: create a session, post a message with a file and watch SSE `thinking → step → message`, move local→cloud→local, and confirm that a second move or message during `MOVING` returns 409.
3. `npm start` in `moby-bank-prototype/` against the running backend: the seeded conversations load, sending a message works, Move to cloud and Move to local both work, and the folder dialogs match the prototype.
4. Confirm `openapi.yaml` is regenerated and matches the endpoints above.
5. On the user's host with `sbx` installed: run with `harness.sandbox.mode=sbx` for a real local turn, then the cloud adapter once its invocation is settled. This cannot be checked inside this sandbox.

## Open items

- Exact `sbx` commands/flags for create, exec, transfer, and the cloud invocation (Task 7 spike).
- Entra app registration and token source for Graph (Task 8).
- JDK version / Maven availability in this sandbox (Task 2).
