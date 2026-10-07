# Testing

About 320 backend tests (JUnit 5), and 44 more for the UI in `moby-bank-prototype` (Node's built-in runner). This page says
what kinds of test there are, what each proves, how to run them, and how to write the next one.

```bash
./mvnw test                                   # everything, about half a minute
./mvnw test -Dtest=SessionTest                # one class
./mvnw test -Dtest='SessionTest#movesCloudToLocal'   # one method
./mvnw test -Dtest='com.mobybank.harness.infrastructure.sbx.*Test'   # one package
```

Tests run in the `test` profile, where the fakes have no delays (`%test.harness.fake.*=0`).

## The kinds of test

| Kind | Where | Starts Quarkus? | What it proves |
|---|---|---|---|
| **Domain unit tests** | `domain/` | no | The rules: state machine, invariants, value-object validation, events |
| **Application tests** | `application/` | no | Use-case orchestration against fakes of every port, including failure and busy paths |
| **Adapter tests** | `infrastructure/` and its `sbx/`, `graph/` | no | Each adapter in isolation: commands issued, parsing, HTTP behaviour, security limits |
| **Wiring tests** | `WiringTest`, `SbxModeTest`, `GraphModeTest` | yes | The real beans together under CDI with configuration-selected adapters |
| **API tests** | `interfaces/rest/ApiTest`, `BusyApiTest` | yes | Every endpoint over real HTTP, real server-sent events, CORS, 409 paths |
| **Web tests** | `interfaces/web/WebTest`, `WebWaitTest`, `HxTest` | yes (not `HxTest`) | The HTML endpoints behind `index.html`: fragments carry the ids and attributes the page relies on, text is escaped, requests wait for the reply or move, the wait times out cleanly, the JSON API is unaffected |
| **Contract tests** | `OpenApiContractTest` | yes | The endpoints and allowed values the other backend must match |
| **Build layout test** | `BuildLayoutTest` | no | `pom.xml` does not use the repository root as a resource directory (which broke dev mode) |
| **Layering tests** | `*LayeringTest` | no | No layer imports something it should not |
| **Documentation tests** | `DocumentationTest` | no | The docs still match the code (links, class names, config keys, endpoints) |

The fast tests are the bulk on purpose: the domain and application layers have no framework, so `SessionTest` and
`SessionApplicationServiceTest` (51 tests between them) run in milliseconds and cover the rules exhaustively. The
Quarkus tests then prove the pieces are wired and the HTTP surface behaves.

## What is where

| Area | Classes (test counts) |
|---|---|
| Domain | `SessionTest` (23), `ValueObjectsTest` (14), `ConnectedFoldersTest` (6), `RecencyGroupTest` (6), `DomainLayeringTest` (2) |
| Application | `SessionApplicationServiceTest` (28), `FolderApplicationServiceTest` (7), `CurrentUserApplicationServiceTest` (2), `ApplicationLayeringTest` (2) |
| Infrastructure | `InMemoryRepositoriesTest` (7), `EventsUploadsAndRunnerTest` (9), `FakeAdaptersTest` (12), `DemoDataTest` (6), `InfrastructureLayeringTest` (1) |
| `sbx` adapter | `SbxSandboxAgentTest` (16), `SbxCliTest` (11), `SbxSandboxTransferTest` (7), `ClaudeStreamParserTest` (8), `ReplyFormatterTest` (8), `ProcessCommandRunnerTest` (8), `SandboxRegistryTest` (4) |
| Graph adapter | `GraphDocumentCatalogTest` (23), `GraphTokenProviderTest` (6) |
| Documentation | `DocumentationTest` (8) |
| Web | `WebTest` (34), `WebWaitTest` (3), `HxTest` (3), `WebLayeringTest` (2) |
| REST and wiring | `ApiTest` (31), `GraphModeTest` (5), `SbxModeTest` (3), `WiringTest` (5), `OpenApiContractTest` (4), `BusyApiTest` (2), `RestLayeringTest` (2), `HealthTest` (2) |

## Test doubles you will meet

| Double | Where | What it does |
|---|---|---|
| `Fakes` (nested classes) | `application/Fakes.java` | A fake for every port the application layer uses. `SessionRepo` copies on read and checks versions like the real repository; `QueueRunner` holds background tasks until the test calls `drain()`, so asynchronous flows are deterministic |
| `FakeRunner` | `infrastructure/sbx/` | A scripted `CommandRunner`. Records every command and answers from the first matching rule; this is how the exact `sbx` commands are asserted with no `sbx` installed |
| `FakeGraphServer` | `infrastructure/graph/` | A small in-process HTTP server that plays Graph, the token endpoint and the download host, serving a fixed drive with paging. Public, so the REST-level `GraphModeTest` reuses it |
| `SlowFakesProfile` | `interfaces/rest/` | A Quarkus test profile that makes the fake agent and transfer slow, so a session is observably busy for the 409 tests |
| `SbxModeProfile`, `FakeGraphResource` | `interfaces/rest/` | Start the app with `harness.sandbox.mode=sbx` (pointing at a program that does not exist) or with `graph` mode against `FakeGraphServer` |
| `SseReader` | `interfaces/rest/` | A minimal server-sent-events client that reads a real stream on a thread |

## Patterns worth copying

- **Drive asynchronous code with a queue, not sleeps.** In application tests, `service.sendMessage(...)` only queues the
  agent run; the test calls `runner.drain()` to run it, then asserts. The events it published are in
  `RecordingEvents.published`.
- **Assert the exact command.** Adapter tests compare the whole argument list (`assertEquals(List.of("sbx move …"), runner.lines())`),
  not just that "something ran".
- **Poll with a deadline in Quarkus tests.** Where a real background thread is involved (`ApiTest.awaitIdle`,
  `WiringTest.awaitEvent`), wait up to a timeout and fail with a description; never a bare `sleep`.
- **Test the failure path next to the success path.** Every adapter and use case has tests for a failed agent, a failed
  move, an unreachable service, a refused input.
- **Prove a guard can fail.** The layering tests were checked by adding a forbidden import and watching them fail; do
  the same for any new guard.

## Guards that keep the design honest

| Test | What breaks it |
|---|---|
| `DomainLayeringTest` | Any import in `domain` other than `java.*` or another domain class; a bare `*Service` class name |
| `ApplicationLayeringTest` | An import of `infrastructure`, `interfaces`, JAX-RS or JPA; a service not named `*ApplicationService` |
| `InfrastructureLayeringTest` | An import of the REST layer |
| `RestLayeringTest` | An import of `infrastructure` or persistence, a domain type other than an exception in the mapper, or the word `Repository` anywhere in the REST package |
| `BuildLayoutTest` | A `<build><resources>` entry that points above the module, which makes Quarkus copy the repository into `target/classes` |
| `WebLayeringTest` | An import of `domain`, `infrastructure`, `interfaces.rest` or persistence in the web package, or the word `Repository` there |
| `WebTest.theHtmlEndpointsAreNotPartOfTheOpenApiContract` | The HTML endpoints leaking into `openapi.yaml`, the contract the Python backend must match |
| `OpenApiContractTest` | An endpoint removed, an enumeration losing a value, `openapi.yaml` missing a path |
| `FakeAdaptersTest.theAdapterProducersAreCreatedAtStartupSoABadConfigurationStopsTheApp` | Removing `@Startup` from an adapter producer, which would let a bad configuration go unnoticed until first use |
| `DocumentationTest` | A doc that links to a missing file, names a class or test that no longer exists, lists a config property the code does not read (or omits one it does), or omits an endpoint. For a **plan** document such as `ONEDRIVE_INTEGRATION.md` (which names things that do not exist yet) only its "Current state" section is checked against the code; links are checked everywhere |

## What the tests cannot tell you

- **The real `sbx` CLI.** The adapters' commands are asserted exactly, but only a host with `sbx` shows that the CLI
  accepts them, that `sbx ls -q` prints names as assumed, and that the agent's headless flags and stream format are as
  assumed. See [integrations.md](integrations.md).
- **A real Microsoft tenant.** Graph is an in-process stand-in; permissions and tenant policy are not exercised.
- **Load or long-running behaviour.** Nothing tests many concurrent sessions, or an agent turn that takes minutes.
- **The pages in a browser.** The prototype's tests cover its logic and template statically (see
  `moby-bank-prototype/README.md`). For `index.html`, `WebTest` asserts the HTML and the `HX-Trigger` headers, but not
  that htmx swaps them correctly, that the dialog opens or that toasts show: look at it in a browser.

## Adding a test: where does it go?

| You changed… | Add a test in… |
|---|---|
| A rule in `Session` or a value object | `domain/` (plain JUnit, no Quarkus) |
| A use case's orchestration | `application/SessionApplicationServiceTest` using `Fakes` |
| An `sbx` command or how its output is read | the `sbx/` test for that class, asserting the exact command or parsed result |
| A Graph request | `GraphDocumentCatalogTest`, extending `FakeGraphServer` if it needs new data |
| A fragment or header the htmx page uses | `interfaces/web/WebTest` (add to `WebWaitTest` if it depends on the wait) |
| An endpoint or an error status | `interfaces/rest/ApiTest` (add to `BusyApiTest` if it needs a busy session) |
| A DTO or its allowed values | `OpenApiContractTest` |
