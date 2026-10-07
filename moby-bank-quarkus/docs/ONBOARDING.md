# Onboarding: the Moby Bank harness backend

Welcome. This guide gets you from a fresh checkout to reviewing and changing this code with confidence. It is a
working guide: follow it in order, and by the end you will have run the app, read every layer, and done a structured
review of the code yourself. Budget **about a day** for the first pass (setup 30 minutes, reading and the guided
review about four hours, the exercises the rest).

| Part | What you do | Time |
|---|---|---|
| [1. The 10-minute picture](#1-the-10-minute-picture) | Understand what this is | 10 min |
| [2. Set up and run it](#2-set-up-and-run-it) | Build, test, run backend and UI | 30 min |
| [3. Reading order](#3-reading-order) | Which docs and files to read, in which order | 1 h |
| [4. Guided code review](#4-guided-code-review) | A 13-stop tour: what to look at, what to look for, what to answer | 3 h |
| [5. Reviewing a pull request here](#5-reviewing-a-pull-request-here) | The checklist we apply to changes | reference |
| [6. Known rough edges](#6-known-rough-edges) | Things you are expected to spot and discuss | 30 min |
| [7. Your first changes](#7-your-first-changes) | Four exercises with acceptance criteria | 2 to 3 h |
| [8. Cheat sheet](#8-cheat-sheet) | Commands, rules, where to ask | reference |

---

## 1. The 10-minute picture

A bank credit analyst chats with an **agent**. The agent reads documents (uploaded, or from OneDrive), runs
calculations, and answers with prose and sometimes a table. The agent runs in a **Docker Sandbox**, either on the
analyst's own machine (**local**) or in Docker's hosted service (**cloud**), and a conversation can be **moved**
between the two in either direction.

This repository is a demo of how a team would build that harness. It has two modules:

- `moby-bank-prototype/`: the chat UI (a Claude Design component, not a normal JS app).
- `moby-bank-quarkus/`: **this backend**, in Java with Quarkus. A Python backend with the same API is planned, which is
  why the API contract (`openapi.yaml`) matters more than any class.

Three facts to keep in your head:

1. **Everything external has a fake.** The agent, the sandboxes and OneDrive are simulated by default, so the app
   runs with nothing installed. Real ones are switched on by configuration. Most of your work will not need `sbx` or
   Microsoft.
2. **There is no database.** State is in memory. The code is shaped as if there were one (copies on read, optimistic
   versioning), so persistence can be added without touching the domain.
3. **The layering is enforced by tests.** Four layers, one direction of dependency. A wrong import fails the build.

Skim [architecture.md](architecture.md) now (15 minutes): the diagram and the three flows are enough for this step.

## 2. Set up and run it

### Prerequisites

- **Java 25** (`java -version`)
- **Node 22** for the UI (`node --version`)
- Git. You do **not** need Maven (the wrapper `./mvnw` is included), Docker, `sbx`, or a Microsoft account.

If you work inside a Docker Sandbox, see `SANDBOX-KIT.md` at the repository root for a kit that provides Java,
Maven, the Quarkus CLI and JBang.

### Build and test

```bash
cd moby-bank-quarkus
./mvnw test
```

Expect `BUILD SUCCESS` and roughly 320 tests in about half a minute. The first run downloads dependencies. If tests
fail on a clean checkout, **stop and ask**: that is a setup problem, not something to work around.

### Run the backend and the UI

```bash
# terminal 1: the backend (http://localhost:8080; Dev UI at /q/dev; live reload on save)
./mvnw quarkus:dev

# terminal 2: the UI (http://localhost:4173)
cd ../moby-bank-prototype && npm start
```

Open <http://localhost:4173>. Try: open the first conversation; send a message; attach a file with **Upload**; use
**From OneDrive**; press **Move to cloud**, then **Move to local**. Watch the toasts: they are driven by the events
described in [api.md](api.md#live-events).

### See the API directly

```bash
curl -s localhost:8080/api/me
curl -s localhost:8080/api/sessions | head -c 400
curl -N localhost:8080/api/sessions/<an id from above>/events      # leave this open, then act in the UI
```

`http://localhost:8080/q/swagger-ui` shows every endpoint. Open the events stream in one terminal and send a message in
the UI: you will see each `thinking`, `step` and `message` event as it happens. Doing this once explains the design
better than any diagram.

### Checkpoint

You are ready to continue when you can say: what happens when you press Move to cloud, in one sentence; and where
the demo conversations come from. (Answers: the UI calls `POST …/move`, which returns 202 at once while a background
task runs a fake transfer and publishes progress events; they are seeded at startup by `DemoDataSeeder` from
`DemoData`.)

## 3. Reading order

Read the docs first, then the code, in this order. Each step says what to take away.

| # | Read | Take away |
|---|---|---|
| 1 | [architecture.md](architecture.md) | The layers, the three flows, how consistency works |
| 2 | [domain-model.md](domain-model.md) | The two aggregates, the status machine, the invariants and which test proves each |
| 3 | [api.md](api.md) | The exact HTTP surface and event stream |
| 4 | [configuration.md](configuration.md) | Every setting, and what real vs fake means |
| 5 | [integrations.md](integrations.md) | How the real `sbx` and Graph adapters work and what they assume |
| 6 | [testing.md](testing.md) | The test doubles and patterns you will use |
| 7 | [apidocs/index.html](apidocs/index.html) | The Javadoc: open it in a browser and keep it beside the code |
| 8 | `../CLAUDE.md` | The non-obvious rules for both modules |

The Javadoc is generated from the source (`./mvnw javadoc:javadoc`), includes package-private classes, and opens on a
landing page that maps the layers.

## 4. Guided code review

This is the core of onboarding. You will **review the existing code as if it were a pull request**: not to find
fault for its own sake, but because reviewing is the fastest way to learn what the code promises and whether it
keeps those promises. At each stop: open the file, read it with the "look for" list in mind, answer the questions
(write your answers down; the answers are checkable in the code or the tests), and note anything you would
challenge in a real review.

**How to work through it**

- Keep the Javadoc open in a browser next to your editor.
- Read the **test** for each thing right after the code. A rule with no test is worth a note.
- When a question says "find", point to a line. If you can't find it, that is a finding.
- Keep a running list of "I would ask about this". Compare it with [section 6](#6-known-rough-edges) at the end.

Paths are under `src/main/java/com/mobybank/harness/` unless they start with `src/test`.

### Stop 1: `domain/Session.java` (the rules) — 30 min

The most important class. Read it top to bottom, then `src/test/.../domain/SessionTest.java`.

*Look for:* private constructor and factories (`start`, `rehydrate`); no setters; state changes only in named methods;
time passed in (`Instant now`), never read from a clock; `requireIdle`/`requireRunning`; events appended to
`pendingEvents` and drained by `pullPendingEvents`; `messages()` returning a copy.

*Answer:*
1. Which methods can throw `SessionBusyException`, and which throw `IllegalStateException`? Why the difference?
2. Where is the session's title decided, and what is the rule when the first message has no text?
3. Why does `beginMove` take a `target` instead of there being one method to move to the cloud and another to move
   to local?
4. What stops `completeMove` being called on a session that is not moving?
5. `rehydrate` raises no events. Why does that matter for a session stored mid-move?

*Challenge:* is there any way to reach a state the state machine forbids? (Try: post a message, then call
`beginMove`. Which test covers it?)

### Stop 2: the value objects (`domain/*.java`) — 20 min

Read `SessionTitle`, `ResultTable`, `FileRef`, `FolderId`, `AgentReply`, then `ValueObjectsTest`.

*Look for:* records; validation in the compact constructor; normalisation by reassigning the parameter (`value = value.strip()`);
defensive copies (`List.copyOf`).

*Answer:*
1. In `ResultTable`, why is the validation written with a local `columns` variable rather than using `cols` inside the
   lambda? (Hint: try it, and read the compiler error.)
2. `SessionTitle` cuts to 48 *code points*, not characters. Which test proves it doesn't split an emoji?
3. `FileRef` has a name and a source but no folder id. What does that force `DocumentCatalog.fetch` to do, and what
   breaks if two folders hold a file with the same name?

### Stop 3: the ports (`domain/Sandbox*.java`, `DocumentCatalog`, the repositories) — 15 min

Read each interface's Javadoc as a **contract**.

*Look for:* contracts stated in words: `persist` requires the stored version to match; `runTurn` calls `onStep` before
returning; `fetch` assumes unique names.

*Answer:*
1. What does `SandboxAgent.runTurn` receive, and why is it an `AgentTurn` snapshot rather than the `Session`?
2. Which exception does each port signal failure with?
3. Which ports live in `application` rather than `domain`, and what is the rule for choosing?

### Stop 4: `application/SessionApplicationService.java` (orchestration) — 40 min

Read `sendMessage`, `runTurn`, `failTurn`, `move`, `runMove`, `failMove`, `publish`; then
`src/test/.../application/SessionApplicationServiceTest.java` and `Fakes.java`.

*Look for:* each step is load, change, persist, publish; **no business rules** (they belong in the aggregate); the
session is **reloaded** in the background task; background work goes through `BackgroundRunner`; every failure path
ends with the session idle again.

*Answer:*
1. Trace `sendMessage` from the moment it is called to the moment it returns. What has been persisted, what published,
   and what is still to happen?
2. Why does `runTurn` call `load(...)` again instead of reusing the session from `sendMessage`? What would happen if
   it didn't? (Hint: look at what `persist` does to the version.)
3. `publish(Session)` has a `switch` with no `default`. Why is that safe, and what happens when someone adds a third
   `DomainEvent`?
4. List every way a turn can end and what the analyst sees in each.
5. In the test `aSecondMessageWhileTheAgentIsWorkingIsRefused`, why is `runner.pending()` asserted to be 1?

*Challenge:* `sendMessage` persists (status RUNNING) and only then calls `runner.run`. What if `runner.run` throws?
(See [section 6](#6-known-rough-edges).)

### Stop 5: the boundary (`application/Dtos.java`, the `*DTO` and `*Command` records, `SessionEvent`) — 20 min

*Look for:* API types contain only strings, numbers, lists and other DTOs; enums become lower-case strings in one place
(`Dtos.name`); input is validated and converted in one place (`Dtos.sessionId`, `Dtos.location`, `Dtos.fileRef`);
`@Schema` annotations list the allowed values (they feed `openapi.yaml`).

*Answer:*
1. Why are DTOs separate from domain types when they look so similar?
2. What happens to `{"name": null, "source": "upload"}` sent as an attachment? Which test proves it?
3. `SessionEvent` is a sealed interface of records. What does each record's `type()` return, and where is that used?

### Stop 6: `infrastructure/InMemorySessionRepository.java` — 15 min

*Look for:* `synchronized`; stores a copy and returns copies (`copy(session, version)`); the version check in `persist`.

*Answer:*
1. Walk through two threads loading the same session, both changing it, both saving. Who wins? What does the loser
   get, and what does the API turn that into?
2. What would need to change to back this with a real database? What would *not* need to change?

### Stop 7: wiring and the fakes (`infrastructure/Adapters.java`, `FakeSandboxAgent`, `DemoData`, `DemoDataSeeder`) — 25 min

*Look for:* producers that choose an implementation from config; the fakes are plain classes (not beans);
`@Startup`; unsupported modes throw `IllegalStateException` naming the property; seeded ids derived from fixed keys.

*Answer:*
1. Why are the fake adapters *not* CDI beans, but the in-memory repositories are?
2. What does `@Startup` on the producers change, and which test protects it? (Hint: what happened before it was
   added: see the commit history for "Fail fast at startup".)
3. How does `FakeSandboxAgent` make the UI's "steps appear one by one" behaviour work?
4. Why do the demo sessions keep the same ids across restarts?

### Stop 8: the REST layer (`interfaces/rest/`) — 30 min

Read `SessionsResource`, `FoldersResource`, `ApiExceptionMappers`, then `src/test/.../interfaces/rest/ApiTest.java`.

*Look for:* resources only call application services; a status code per situation (201 with `Location`, 202, 404, 409,
400); no domain types (except exceptions in the mapper); OpenAPI annotations describe each operation.

*Answer:*
1. Which exception becomes which HTTP status? Where is the single place that decides?
2. `POST …/messages` returns 202, not 200 or 201. Why?
3. The upload endpoint receives a `FileUpload`. Where is the file name sanitised, and which test proves `../../etc/passwd`
   can't escape?
4. What does CORS allow, and where is it configured?

### Stop 9: the event stream (`SessionsResource.events`, `InMemorySessionEventStream`) — 25 min

*Look for:* subscribe *before* emitting `ready`; the `onTermination` that closes the subscription; `BUFFER` back-pressure;
the keep-alive; a listener that throws cannot stop others.

*Answer:*
1. Why is `ready` emitted after subscribing rather than before?
2. What does a client miss if it connects late? How is it meant to recover?
3. What happens to the subscription when the browser tab closes? Show the line.
4. Why does the resource call `sessions.get(id)` before subscribing, given it throws the result away?

### Stop 10: the `sbx` adapter (`infrastructure/sbx/`) — 50 min

Read in this order: `SbxCli`, `ProcessCommandRunner`, `SandboxRegistry`, `SbxSandboxAgent`, `ClaudeStreamParser`,
`ReplyFormatter`, `SbxSandboxTransfer`; then `SbxCliTest`, `SbxSandboxAgentTest`, `FakeRunner`.
[integrations.md](integrations.md#docker-sandboxes-through-sbx) is the map.

*Look for:* every `sbx` argument built in one class; prompts passed as a single argument, never through a shell;
stdin closed; timeouts that kill the process; output parsed defensively (unknown lines ignored); failures wrapped in
`SandboxFailureException` with the tool's own message.

*Answer:*
1. Why must a prompt like `two words; and $(danger)` never be interpreted? Which tests show it isn't?
2. How does the agent adapter find a session's sandbox after the app restarts?
3. Sketch what happens on a turn for a session that has history in this app but no agent conversation yet.
4. A resumed run fails. What happens next, and which test proves it?
5. Why is `sbx move` called without `--cloud`, and what is the assumption behind that? Where is it written down as
   something to verify?
6. Both `SbxSandboxAgent` and `SbxSandboxTransfer` contain a loop choosing the highest generation. Is that duplication
   a problem? What would you do?

### Stop 11: the Graph adapter (`infrastructure/graph/`) — 30 min

Read `GraphDocumentCatalog` and `GraphTokenProvider`, then the two tests and `FakeGraphServer`.

*Look for:* the bearer token sent only to the configured host; `sameHostOrFail`; the download request without a token;
page and size limits; the client secret never in a message; percent-encoding of path segments.

*Answer:*
1. A paging link points to another port on the same host. What happens, and which test proves it?
2. Why is the download made without the `Authorization` header?
3. What happens when Graph answers 429? Where in the code?
4. What stops a never-ending `@odata.nextLink` chain?

### Stop 12: the tests as a design (`src/test/`) — 30 min

Read `testing.md`, then open `DomainLayeringTest`, `OpenApiContractTest`, `DocumentationTest` and `BusyApiTest`.

*Answer:*
1. Which tests fail if someone adds `import jakarta.ws.rs.*` to a domain class? To an application class?
2. Why does `BusyApiTest` need its own test profile?
3. Pick any test that *can't* fail (one whose assertion would pass on broken code). Is there one? (A good review finds
   at least one weak assertion; note where.)
4. What is deliberately **not** tested, and where is that written down?

### Stop 13: the other consumer (`../moby-bank-prototype/`) — 15 min

Skim `Bank Agent Harness.dc.html` (the `<script>` half) and `test/`. You do not need to read the template.

*Answer:* what does the UI do when the event stream drops, and what keeps the UI consistent with the server?
(Hint: `watch`, and the `ready` event.)

### After the tour

Compare your "I would ask about this" list with [section 6](#6-known-rough-edges). Anything on yours that isn't there
is a real contribution: open an issue or raise it with the team.

## 5. Reviewing a pull request here

Use this checklist for every change. It is deliberately ordered: architecture problems first, style last.

**Build and tests**
- [ ] `./mvnw test` is green on the branch. Read the *new* tests, not just the count.
- [ ] A bug fix comes with a test that fails without the fix.
- [ ] A new guard or rule has been proven to fail (the author says how: for example adding a bad import).

**Layering and structure**
- [ ] The layering tests still pass *unchanged*. A PR that edits a layering test to make it pass needs a strong reason.
- [ ] New domain code imports only the JDK and the domain package; no `@Inject`, no annotations from frameworks.
- [ ] A new external dependency is a **port** in the domain (or `application`) with an adapter in `infrastructure`,
      and a **fake** so the app still runs with nothing installed.
- [ ] Naming: `*ApplicationService`, `*DomainService`, `*Repository` (interface in `domain`), `*Command`, `*DTO`,
      `*Event` (past tense). No bare `*Service`.
- [ ] New adapter implementations are plain classes chosen in `Adapters`, not CDI beans that would be ambiguous.

**The domain**
- [ ] Rules live in the aggregate or value object, not in a service or resource. No setters, no public fields.
- [ ] Invariants are enforced in the factory or the transition method, before any state changes (no half-applied change).
- [ ] Time comes in as a parameter. No `Instant.now()` inside the domain.
- [ ] Anything returned is a copy or immutable.

**The application layer**
- [ ] Each use-case step is load, change, persist, publish. After a `persist`, the aggregate is **reloaded** before
      being changed again.
- [ ] Every failure path leaves the session usable (idle) and tells the client why.
- [ ] Domain events are drained with `pullPendingEvents()` after saving, never before.
- [ ] Long work goes through `BackgroundRunner`, never on the request thread.

**The API**
- [ ] DTOs use only strings, numbers, lists and DTOs; enumerations are lower-case strings with `@Schema` listing them.
- [ ] The build regenerated `openapi.yaml` and **the diff is committed**; the diff matches what the PR claims.
- [ ] The status codes fit [api.md](api.md): 400 bad input, 404 unknown, 409 not now, 502 a downstream failed.
- [ ] `api.md` is updated (the documentation test fails if an endpoint is missing; it cannot tell if the prose is right).
- [ ] The UI and the planned Python backend are not broken by the change (who else reads this field?).

**Security** (there is no authentication in this demo, so these matter more, not less)
- [ ] Any client-supplied name that reaches a file path or a command is reduced to a base name or validated
      (see `attachUpload`, and `SbxSandboxAgent.stage`).
- [ ] Commands are built as argument lists through `SbxCli`; nothing is concatenated into a shell string.
- [ ] Secrets and tokens never appear in logs, exception messages or API responses (see the Graph token tests).
- [ ] Anything that sends a credential checks where it is sending it (see `sameHostOrFail`).
- [ ] New input is size-bounded or the existing limit is enough (request body 50M; listings capped at 25 pages).

**Concurrency**
- [ ] No shared mutable state without a lock or a copy. Repositories return copies.
- [ ] A race is handled by the status machine or the version check, and the change says which.

**Tests**
- [ ] Asynchronous code is driven with `QueueRunner.drain()` or polled with a deadline, never a bare `sleep`.
- [ ] An adapter test asserts the *exact* command or request, not just that something happened.
- [ ] The failure path is tested next to the success path.

**Documentation and config**
- [ ] A new property is in `application.properties` (or has a default in code) **and** in
      [configuration.md](configuration.md): `DocumentationTest` checks both directions.
- [ ] Public types have Javadoc that says *why*, not what the name already says. Regenerate with `./mvnw javadoc:javadoc`
      if the PR changes public API, and include the result.
- [ ] Commit messages explain the reason, and a behaviour change says how it was verified.

**Style (last, and lightly)**
- [ ] Matches the surrounding code: records for values, small classes, comments only where the *why* isn't obvious.

### Giving a review

- Say what you verified, not just what you disliked: "ran it, sent a message and a move, events arrive in order".
- Prefer questions to verdicts when you are unsure ("what happens if `runner.run` throws here?").
- Distinguish *must fix* (correctness, layering, security) from *consider* (naming, structure).
- If you can't tell whether something is right, say so and ask for a test that would show it.

## 6. Known rough edges

An honest list of things in the current code that a good reviewer should notice. Each was checked against the code.
None is a secret; they are here so you learn to see them, and so you can decide which are worth fixing.

1. **A failed failure-handler leaves a session stuck.** `failTurn` and `failMove` (in `SessionApplicationService`) log
   and swallow their own errors. If recording the failure itself fails (for example the save throws), the session stays
   RUNNING or MOVING forever and accepts nothing. Nothing recovers a session stuck that way. *Consider:* a timeout or a
   startup sweep.
2. **`runner.run` failing after the save.** `sendMessage` persists the session as RUNNING *then* submits the background
   task. If submission threw, the session would be stuck as in (1). (The virtual-thread executor rarely rejects work, so
   this is unlikely today.)
3. **Events aren't replayed.** A client that connects late or drops misses events. The design answer is "read the
   session", and the UI does (`ready` triggers a refetch, plus a polling safety net), but any new client must know.
4. **Uploads are never freed.** `InMemoryUploadStore` holds every uploaded file in memory for the life of the process
   and there is no size budget beyond the 50M per-request limit.
5. **File lookup is by name.** `FileRef` has no folder id, so `DocumentCatalog.fetch(fileName)` assumes names are unique
   and takes the first match.
6. **The same loop twice.** "Pick the highest generation among listed sandboxes" is written in both `SbxSandboxAgent`
   (`findExisting`) and `SbxSandboxTransfer` (`source`).
7. **Stopped source sandboxes accumulate.** `sbx move` stops the source; nothing removes it.
8. **No authentication, and one user.** Every caller is the same analyst; anyone who can reach the port can read and
   start conversations. CORS limits browsers, not other clients.
9. **No way to delete a session, and the history is unpaged.** `GET /api/sessions` returns everything.
10. **Unverified against the real thing.** The `sbx` commands, the agent's headless flags and stream format, and a real
    Microsoft tenant have not been exercised (see [integrations.md](integrations.md)). These are assumptions with tests
    around them, not facts.
11. **English strings in the application layer.** The "Reading 2 files on local model…" label is built in
    `SessionApplicationService.thinkingLabel`, so presentation text lives below the UI.

## 7. Your first changes

Do these in order on a branch. Each has acceptance criteria; a reviewer will apply section 5 to your PR.

### Exercise A: add a field end to end (warm-up)

Add `messageCount` (an integer) to the conversation history rows (`SessionSummaryDTO`).

- Touch: `SessionSummaryDTO` (with a `@Schema`), `Dtos.summary`.
- Acceptance: a test in `SessionApplicationServiceTest`; `ApiTest` checks the field over HTTP; `openapi.yaml` regenerated
  and committed; `api.md` shows the field; the UI still loads (run it).
- Learn: the API-change loop from [api.md](api.md#changing-the-api).

### Exercise B: a new rule in the domain

Forbid attaching more than 10 files to one message.

- Touch: `Session.postUserMessage` (throw `IllegalArgumentException` with a clear message).
- Acceptance: tests for 10 (allowed) and 11 (rejected); the API answers 400 with that message; nothing is persisted on
  rejection; no changes outside the domain except the test and docs.
- Learn: the rule belongs in the aggregate, and the REST mapping needs no change.

### Exercise C: a new `sbx` command behind the port

After a successful move, remove the stopped source sandbox (rough edge 7) with `sbx [--cloud] rm --force <name>` (the
source is on the side the session moved *from*, so it is a cloud sandbox when moving to local), but only when
a new setting `harness.sbx.remove-source-after-move` is `true` (default `false`).

- Touch: `SbxCli` (a `remove` method), `SbxSandboxTransfer`, `SbxSettings`, `SbxAdapters`, `application.properties`.
- Acceptance: `SbxCliTest` asserts the exact command; `SbxSandboxTransferTest` covers enabled and disabled and a failing
  `rm` (which must *not* fail the move); the property is in `configuration.md`; `DocumentationTest` passes.
- Learn: the adapter pattern, `FakeRunner`, the configuration loop.

### Exercise D: find and fix a rough edge

Pick one of (1), (2) or (4) from section 6, write a failing test that demonstrates it, fix it, and describe the trade-off
in your commit message.

- Acceptance: the failing test first, then the fix; layering tests untouched; docs updated where behaviour changed.
- Learn: how this codebase wants a fix to look.

### Recipe: add an endpoint

1. Decide the use case; add (or extend) an `*ApplicationService` method and a `*Command`/`*DTO`.
2. Put any rule in the domain, with a test.
3. Add the resource method in `interfaces/rest/` with `@Operation` and `@APIResponse` for each status.
4. Add API tests (success and each error), run `./mvnw test`, commit the regenerated `openapi.yaml`.
5. Update `api.md`, and `ApiExceptionMappers` if there is a new exception.

### Recipe: add a port and an adapter

1. Define the interface in `domain` (or `application` if only the application needs it), with a Javadoc contract.
2. Write a **fake** implementation in `infrastructure`; add a producer or bean so the app boots.
3. Add a config mode in `Adapters` (with `@Startup`), and a test for the selection.
4. Only then write the real adapter, tested with a stand-in (a fake runner, an in-process HTTP server).
5. Document it in `configuration.md` and `integrations.md`.

## 8. Cheat sheet

```bash
./mvnw quarkus:dev                     # run with live reload (http://localhost:8080)
./mvnw test                            # all tests
./mvnw test -Dtest=SessionTest         # one class
./mvnw javadoc:javadoc                 # regenerate docs/apidocs
./mvnw package -DskipTests             # build target/quarkus-app/quarkus-run.jar
java -Dquarkus.http.port=8090 -jar target/quarkus-app/quarkus-run.jar    # options go BEFORE -jar
./mvnw quarkus:dev -Dharness.sandbox.mode=sbx -Dharness.documents.mode=graph   # real adapters
```

**The rules, on one screen**

1. `interfaces.rest → application → domain ← infrastructure`. Tests enforce it.
2. Domain: plain Java, records for values, aggregates guard their own rules, time is a parameter.
3. Application: load, change, persist, publish; reload before changing again.
4. Everything external is a port with a fake.
5. The API is lower-case strings; `openapi.yaml` is regenerated and committed.
6. Nothing client-supplied reaches a path or a shell unvalidated.

**Where things are**

| You want | Go to |
|---|---|
| The rules | `domain/Session.java`, [domain-model.md](domain-model.md) |
| A use case | `application/SessionApplicationService.java` |
| An endpoint or an error code | `interfaces/rest/`, [api.md](api.md) |
| A setting | `application.properties`, [configuration.md](configuration.md) |
| The `sbx` commands | `infrastructure/sbx/SbxCli.java` |
| Test doubles | [testing.md](testing.md) |
| Why a thing is the way it is | `../agent-os/specs/2026-09-30-1452-quarkus-mvp-backend/` (especially `shape.md`) and `git log` |

**Stuck?** Check the troubleshooting table below, then ask. If the docs were wrong or missing something, fix them in
your first PR: that is a welcome first contribution.

| Symptom | Likely cause |
|---|---|
| The UI shows "Cannot reach the harness API" | The backend isn't running, or the UI is pointed elsewhere (`?api=` in the page URL) |
| Browser console: a CORS error | The UI's origin isn't in `quarkus.http.cors.origins` |
| App won't start: `Unsupported harness.sandbox.mode=…` | A misspelt mode (allowed values are in the message) |
| App won't start: `needs either harness.graph.access-token…` | `documents.mode=graph` without credentials |
| A `-D` option seems ignored with `java -jar` | It came after `-jar`; put it before |
| Quarkus fails with `Unsatisfied dependency for type …` | A new port has no implementation or producer |
| A message answers 409 | The session is running or moving (wait for `idle`) |
| A test hangs or is flaky around events | It is sleeping instead of polling with a deadline, or forgot to drain the `QueueRunner` |
