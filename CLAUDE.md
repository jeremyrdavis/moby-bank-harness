# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

| Folder | What it is |
|---|---|
| `moby-bank-prototype/` | The chat UI: a Claude Design "dc" component served by a tiny Node server. Front end only. |
| `moby-bank-quarkus/` | Backend implementation #1 (Java/Quarkus). A Python implementation is planned and must match the same API. |
| `agent-os/` | Product docs (`product/`: mission, roadmap, tech stack) and the spec for the Quarkus backend (`specs/`). |
| `.claude/` | Claude tooling: agent-os commands and the Python, Quarkus and DDD skills used to build the backends. |

Commands run from inside the module folder. Paths below are relative to it unless they start with a module name.

## What this is

A demo for engineering/platform teams of a custom agent harness: a chat UI for a fictional bank credit analyst, backed by an agent that runs in a Docker Sandbox, **locally or in the cloud**, where a session can move between the two in either direction. The agent, the sandboxes and OneDrive are simulated by default so everything runs with nothing installed; real ones are switched on by config. See `agent-os/product/` for the goals and `agent-os/specs/` for the backend spec.

## Commands

```bash
# Backend (Java 25, Maven wrapper; no database, state is in memory)
cd moby-bank-quarkus
./mvnw quarkus:dev                      # http://localhost:8080, Dev UI at /q/dev
./mvnw test                             # ~270 tests
./mvnw test -Dtest=SessionTest          # one class (or -Dtest='SessionTest#methodName')

# UI (Node 22; nothing to install)
cd moby-bank-prototype
npm start                               # http://localhost:4173 (PORT=3000 to change)
npm test                                # template + logic tests, no backend needed
HARNESS_API=http://localhost:8080 npm run test:live   # end to end against a running backend
```

- In this sandbox the tools are installed under `~/tools` and `~/.jbang` and put on the `PATH` through `/etc/sandbox-persistent.sh` (see the sandbox notes in the parent `CLAUDE.md`). The Bash tool's snapshot may not have them, so run Maven and the Quarkus CLI through `bash -c '…'`.
- Don't `pkill -f` with a pattern that also appears in your own command line (such as the project folder name): it kills the shell running it. Start a dev server with `setsid` and kill its process group by PID instead.
- Run the live UI test against a backend started with short fake delays (`-Dharness.fake.agent-delay-ms=300 -Dharness.fake.move-packaging-ms=300 -Dharness.fake.move-transfer-ms=300`); it asserts the reply arrives over the event stream, which a 1.8s default delay would defeat.
- The UI must be served over HTTP (the runtime uses `fetch`); `file://` fails. It finds the API at the page's host on port 8080, or `?api=…`, or `window.HARNESS_API`.

## Backend architecture (`moby-bank-quarkus`)

Packages under `com.mobybank.harness` follow the `ddd-foundations` skill. Dependencies point one way: `interfaces.rest → application → domain ← infrastructure`. Layering tests (`*LayeringTest`) fail the build if a layer imports something it shouldn't, so keep domain free of Jakarta/Quarkus and REST free of domain types (except `ApiExceptionMappers`, which names the domain's exception types).

- **`domain`** (flat package, plain Java): `Session` aggregate (owns `Message`s; a status machine IDLE/RUNNING/MOVING; moves to the *other* location, so both directions work), `ConnectedFolders` aggregate, value-object records, events, and the **ports**: `SessionRepository`, `ConnectedFoldersRepository`, `SandboxAgent`, `SandboxTransfer`, `DocumentCatalog`. Aggregates have private constructors, a factory, and `rehydrate()` for the persistence side; they carry a plain `long version`.
- **`application`**: `*ApplicationService` classes plus DTOs/commands (API types only: lower-case strings like `"local"`, `"assistant"`). Agent turns and moves run in the background (`BackgroundRunner`) and report through `SessionEventStream`; `SessionEvent` is the list of events the UI receives.
- **`infrastructure`**: in-memory repositories (copy on read, optimistic version check → `StaleAggregateException`), the fakes, the demo-data seeder, and two real adapter families: `sbx/` (Docker Sandboxes via the `sbx` CLI) and `graph/` (OneDrive via Microsoft Graph). `Adapters` picks each port's implementation from `harness.sandbox.mode` (`fake|sbx`) and `harness.documents.mode` (`fake|graph`) and fails fast on anything else.
- **`interfaces.rest`**: resources, `ApiExceptionMappers` (400 bad input, 404, 409 busy/invalid move/stale, 502 sandbox or document source), and an SSE stream per session.

### Things that aren't obvious

- **`openapi.yaml` is generated at build time and committed.** It is the contract the Python backend must match, so commit changes to it when an endpoint or DTO changes. `OpenApiContractTest` guards the endpoint list and allowed values. The SSE event payloads are described in the `/events` operation's description.
- **Quarkus validates every bean's injection points at startup**, including unused ones, so a new port needs an implementation (or a producer) before the app boots.
- **Ports with more than one implementation are chosen by producers in `Adapters`**, and the implementations are deliberately *not* CDI beans (they'd be ambiguous). The fakes and the `sbx`/`graph` adapters are plain classes built by the producers.
- **`sbx` assumptions.** The adapters issue exactly the commands in `SbxCli` (`create`, `exec`, `cp`, `ls -q`, `move --to … --force`; `--cloud` for the cloud). The automated tests assert those argument lists through a fake `CommandRunner`. They cannot prove the real CLI accepts them, nor the agent's headless flags or stream format, so `moby-bank-quarkus/README.md` lists what to check on a host with `sbx`. `sbx` isn't installed in this sandbox.
- **Sandbox names** are `harness-<12 hex of session id>-g<generation>`; every move creates the next generation, and a session's sandbox is found again after a restart by listing names. A cloud sandbox may be listed as `agent/name` with a suffix; the registry accepts both.
- **Graph token hygiene:** the bearer token goes only to the configured Graph host (a paging link to another host is refused), and downloads use the pre-authenticated URL without it.
- **Tests that need a server** use embedded stand-ins: `FakeGraphServer` (Graph, the token endpoint, downloads) and `FakeRunner` (for `sbx`). Slow fakes (`SlowFakesProfile`) make a session observably busy for the 409 tests.
- Reading the skills: `ddd-foundations`, `ddd-value-objects`, `ddd-aggregates` and `ddd-services` in `.claude/skills/` govern new domain and application code. Their companion skills (`ddd-repositories`, `quarkus-rest`, …) are not installed. The `quarkus-ddd` layout is deliberately **not** used (it conflicts with `ddd-foundations`).

## UI architecture (`moby-bank-prototype`)

This is a **Claude Design "dc" component**, not a normal JS app. Nearly all application code lives in one file, `Bank Agent Harness.dc.html`, which has two halves:

1. **`<x-dc>` template** (markup + Trident component usage).
2. **`<script type="text/x-dc" data-dc-script>`** at the bottom: `class Component extends DCLogic` holding all state and handlers. It holds no data of its own: conversations, folders and the user come from the backend.

`support.js` is the dc-runtime. It parses the template, `new Function`-evals the logic script (which must define `class Component extends DCLogic`), and mounts the result with React into `#dc-root`. React/ReactDOM are vendored in `_vendor/` and loaded before `support.js`, which skips its CDN loader when they already exist.

### Things that aren't obvious

- **`support.js` is generated** (header: "GENERATED from dc-runtime/src/*.ts — do not edit"). The source isn't in this repo; don't hand-edit it.
- **`_ds/trident-…/` is a vendored design-system bundle** exposed as the `window.Trident.*` global (Button, Dialog, Checkbox, Spinner, Toaster, `toast` which is sonner-based: `loading`/`success`/`error`, updated in place by passing `id`). Treat it as read-only too.
- **Template data flow:** the template renders against the flat object returned by `renderVals()`. Every value, handler, and precomputed style the template uses must be a key of that object. Per-item handlers and styles are built inside `renderVals()` (e.g. `toggleSteps`, `select`, `toggle`, `remove`) rather than in the template. `test/template.test.mjs` fails on a typo, because the runtime would otherwise render it as silently empty.
- **`{{ … }}` is not JavaScript.** The runtime's expression resolver (`resolve`/`resolvePath` in `support.js`) supports only property paths, literals, `!`, and `==`/`===`/`!=`/`!==`. Put any other logic (ternaries, `&&`, string concatenation, calls) in `renderVals()` and expose a boolean or string, which is why the code has flags like `isDark`/`isLight`, `isCloud`/`isLocal`, and `noSessionFiles`. The template test enforces this too.
- **Template tags:** `<sc-if value>` and `<sc-for list as>` for control flow (there's no else, so use paired inverse flags). `<x-import component-from-global-scope="Trident.X">` pulls in a Trident component, with props kebab-cased (`class-name`, `on-click`, `on-open-change`). The `hint-size` / `hint-placeholder-*` attributes are design-tool layout hints, so keep them when adding elements.
- **Props:** `layout` (`sidebar` | `panel`) and `theme` (`light` | `dark`) are declared in the `data-props` attribute on the script tag and read via `this.props.*`. Theme is copied into `state.dark`, and the in-app toggle only changes state. `rootClass` and `dialogClass` add the `product` and `dark` classes that switch Trident's token set, and the Dialog needs its own copy because it portals outside the root.
- **Styling:** use Trident semantic tokens (`var(--tri-…)` or Tailwind utilities like `bg-background`) instead of hardcoded colors so light/dark both work. The fonts (Manrope, JetBrains Mono) are vendored under `_ds/…/fonts/` and cover the Latin range only.
- **State model:** `state.convos` is the history list (`GET /api/sessions`); `state.details[id]` is a whole conversation (`GET /api/sessions/{id}`); `state.working[id]` / `state.moving[id]` mean the agent is working / the session is moving. The server's session `status` is the source of truth, and `applyStatus` reconciles the flags with it.
- **Live updates:** `openStream(id)` opens one `EventSource` for the open conversation, and `handleEvent` applies `thinking`, `step`, `message`, `move-progress`, `moved`, `move-failed`. A `ready` event (sent on every (re)connect) re-reads the conversation to catch up; `watch(id)` polls every 2s until a busy session is idle, as a safety net. A message can arrive both in the POST response and on the stream, so `addMessage` de-duplicates by id.
- **Derive, don't cache:** the shared-files list and the title come from messages and the history refresh, not from copies in `details` that go stale as events arrive (two real bugs the live test caught).
- **The test harness** (`test/harness.mjs`) evals the real `<script data-dc-script>` with a stub `DCLogic`, so the logic class is testable without a browser. It can't render the template, so check the page in a browser after large template changes.
