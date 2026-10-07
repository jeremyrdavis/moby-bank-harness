# Plan: serve the htmx `index.html` from Quarkus and give it its API

## Context

The repo root now has `index.html`, a single-file htmx client for the Moby Bank analyst agent. It is the UI for the Quarkus app. It does not call the JSON `/api` that the backend exposes today. It calls **HTML-fragment endpoints** (`/api/conversations`, `/api/onedrive/...`) and swaps what comes back into the page. Its header comment says the contract is in `htmx/API.md`; that file does not exist, so this plan derives the contract from the page itself (see "The fragment contract").

So the work is two things:

1. Serve `index.html` at `/` from the Quarkus app.
2. Implement the fragment endpoints it calls, with Qute, on top of the existing application services.

The JSON API, `openapi.yaml` and `moby-bank-prototype/` stay as they are. They are the contract the Python backend must match.

## Decisions

- **Endpoint prefix is `/ui`, not `/api`.** `index.html` rewrites every `/api…` path to the prefix in `<meta name="api-base">`. Setting it to `/ui` keeps the JSON `/api` clean and keeps the HTML endpoints out of `openapi.yaml` (`mp.openapi.scan.exclude.packages=com.mobybank.harness.interfaces.web`). `OpenApiContractTest` must still pass unchanged.
- **One source for the page.** `index.html` stays at the repo root. A `maven-resources-plugin` `copy-resources` execution copies `../index.html` to `target/classes/META-INF/resources`, which serves it at `/`. The source stays single. In dev mode run `./mvnw resources:copy-resources@copy-index-page` after editing it. (The first attempt, a `<build><resources>` entry for `..`, broke dev mode: see "What was built".)
- **New package `com.mobybank.harness.interfaces.web`** (Qute `@CheckedTemplate` templates plus JAX-RS resources). It depends only on `application` services and `SessionEvent`. A `WebLayeringTest` mirrors `RestLayeringTest` and also forbids importing `interfaces.rest`.
- **The page has no live channel, so requests wait.** The page does not load the SSE extension, so a reply has to arrive in the response to `POST …/messages`, and a move has to finish in the response to `POST …/move`. The handler subscribes to the session's `SessionEventStream`, calls the application service, then blocks (virtual thread) until the assistant `MessageAdded`, `MoveCompleted` or `MoveFailed` arrives, or until `harness.ui.wait-timeout` (default 180s). The application layer is unchanged and the JSON API still returns 202.
- **"Current" conversation is a cookie.** The server stays stateless. `GET …/conversations/{id}` sets `moby-current={id}`; `GET …/conversations/current` reads it and falls back to the most recent session, creating one when there are none. The history fragment reads the cookie to mark `aria-current`.
- **New conversation always creates a session** (`POST /ui/conversations`), as `POST /api/sessions` does. The `/conversations/new/messages` URL in the static shell is only the pre-load placeholder; every `#main` fragment the server returns carries the real id.
- **Escaping:** Qute's HTML auto-escape stays on. No `.raw` on agent or user text.

## The fragment contract

Paths below are as the page writes them (`/api/…`); with `api-base=/ui` they are served under `/ui/…`. All answer HTML; the page sends `Accept: text/html`.

| Page call | Target / swap | Response |
|---|---|---|
| `GET /api/conversations/current` (on load) | `#main` innerHTML | The `#main` content for the current conversation: `header.topbar` (title, location status dot, **Move to cloud** button (disabled "Running in cloud" for a cloud session)), `#thread > #messages`, and the composer form with `hx-post=/api/conversations/{id}/messages`, `hx-target=#messages`, `hx-indicator=#thinking`, plus an `#thinking` element |
| `GET /api/conversations/{id}` (history row) | `#main` | Same fragment for that session. Sets the cookie |
| `POST /api/conversations` (New conversation) | `#main` | Same fragment for a new, empty session. `HX-Trigger: conversations-changed` |
| `GET /api/conversations` (on load, `conversations-changed`) | `#history` | Groups with `.eyebrow` headings (Today / Previous 7 days / Earlier, from `RecencyGroup`) and a `.row` per session with `hx-get`, `hx-target=#main` and `aria-current` |
| `POST /api/conversations/{id}/messages` (multipart: `text`, `files`, repeated `onedrive`) | `#messages` beforeend | The user bubble (`.msg-user`: text, `.chip` per file) and the agent reply (`.msg-agent`: `details.steps`, paragraphs, `.tbl`), after the agent finishes. `HX-Trigger: conversations-changed` (the first message sets the title) |
| `GET /api/onedrive/folders` (on load, `folders-changed`) | `#folders` | `.side-head` with the connect button (`hx-get=/api/onedrive/picker?mode=connect`, `hx-target=#dialog`) plus a `.row` with `.count` per connected folder |
| `GET /api/onedrive/picker?mode=connect` or `mode=attach` | `#dialog` | A `.dlg` with a form and `.pick` checkboxes: all folders (already-connected ones disabled) for `connect`; files from connected folders for `attach` |
| `POST /api/onedrive/connect` (picker form) | `#dialog` | `HX-Trigger: folders-changed, close-dialog` and a toast |
| `POST /api/onedrive/attach` (picker form) | `#pending` beforeend | One `.chip` per file with a hidden `onedrive` input; `HX-Trigger: close-dialog`. The page removes a chip, hidden input included, on click |
| `POST /api/conversations/{id}/move` (`data-move-cloud` button) | `#main` | The `#main` fragment with the flipped location, and `HX-Trigger: {"toast":{"id":"move-cloud","title":…,"variant":"success"}}`. Failure handling comes later |

Errors: for now an error status shows the page's generic "Request failed" toast (htmx does not swap 4xx/5xx). Proper error toasts and a Move to local button are planned later.

Mapping to the application layer: conversation = session; `sendMessage` after `attachUpload` for each uploaded file; OneDrive files go in as `FileSource.ONEDRIVE` names; `move` is always `target=cloud` from this page.

## Work

1. **Serve the page.** The copy step, the two jars (htmx WebJar and mvnpm Manrope), `quarkus.http.enable-compression=true`, `GET /` returns `index.html`; remove or keep the 4173 CORS entry (see open items). Test: `/` returns the page; `/q/health` and `/api/sessions` unaffected.
2. **Skeleton and read-only.** Add `quarkus-rest-qute`; `interfaces.web` with `Templates`; `conversations`, `conversations/current`, `conversations/{id}`, `onedrive/folders`. `WebLayeringTest`, OpenAPI exclusion and a check that `openapi.yaml` is unchanged.
3. **Send.** The multipart message endpoint with the wait, the agent-failure path (the failure message is still a message) and the busy toast.
4. **Move.** Local→cloud, the wait, and the success toast (failure toasts come with the error work).
5. **OneDrive dialogs.** Picker, connect, attach.
6. **Docs.** `moby-bank-quarkus/docs/ui-fragments.md` (the contract above; this is the missing `htmx/API.md`), and updates to `architecture.md`, `configuration.md`, `testing.md`. `DocumentationTest` checks them. Update `CLAUDE.md` and `README.md` to say there are two front ends, and add `.DS_Store` to `.gitignore`.

## Tests

- `@QuarkusTest` + RestAssured per endpoint: the fragment has the ids and classes the page relies on (`#messages`, `#thinking`, `.chip`, `data-move-cloud`), only the fragment is returned (no `<html>`), and text is escaped (`<script>` in a message comes back as `&lt;script&gt;`).
- Wait behavior with `SlowFakesProfile` (reply arrives; 409 while busy) and a short `harness.ui.wait-timeout` for the timeout path.
- `HX-Trigger` header values parse as JSON and use event names the page listens for (`toast`, `close-dialog`, `folders-changed`, `conversations-changed`).
- `@CheckedTemplate` build-time validation and `WebLayeringTest`.
- No browser is available in this sandbox, so swaps, the dialog and the toasts are checked by hand on your host (checklist in the docs).

## Changes to `index.html`

Decided with the user:

- **Done:** htmx and the Manrope font are served from the classpath, not from `unpkg.com` and Google Fonts, so the page works behind a firewall. htmx comes from `org.webjars.npm:htmx.org:2.0.11` (`/webjars/htmx.org/2.0.11/dist/htmx.min.js`). Manrope 400, 500 and 700 come from `org.mvnpm.at.fontsource:manrope:5.3.0` (`/_static/at/fontsource/manrope/5.3.0/{400,500,700}.css`); there is no WebJar for it on Maven Central, so this is the mvnpm build of the same npm package. Both jars go in the `pom.xml`.
- **Done:** the hardcoded user card now reads "Hermione Granger" (avatar "HG"), matching `harness.user.name`. Harry Potter names only, as the repo convention requires.
- **Still to do in step 1:** set `<meta name="api-base" content="/ui">`.
- **Left as is for now:** only "Move to cloud" (no "Move to local" button or move wording); no error toast variant (errors are added later).

Known consequences, accepted for now:

- With requests that wait, the user's message and the agent's steps appear together after the reply, not as the agent works. Showing the user's bubble on `htmx:beforeRequest`, or loading `htmx-ext-sse` (`org.webjars.npm:htmx-ext-sse:2.2.4`), would fix it later.
- A failed move or a 409 shows the check-mark toast until an error variant exists.
- `#thinking` is missing from the static shell (the form's `hx-indicator` points at it); the server fragment includes it.
- A cloud session's header shows a disabled "Running in cloud" button, as the old prototype did, so cloud→local moves stay available through the JSON API only until the page gets a Move to local button.

## Settled with the user

- "New conversation" always creates a new session (the JSON API does the same).

- Keep the CORS entry for `http://localhost:4173` and keep the Node `serve.mjs`. Both belong to the old React prototype, which stays the JSON client until the Python backend exists. The htmx page is served same-origin and needs neither.
- `harness.ui.wait-timeout` defaults to 180s.

## What was built

All six steps are done on the branch `feature/serve-ui-from-quarkus` (not committed yet). 320 backend tests pass, including 42 new ones for the web layer, and `openapi.yaml` is unchanged.

- **Served:** `pom.xml` maps `../index.html` to `META-INF/resources`; htmx 2.0.11 and Manrope 5.3.0 come from jars; gzip is on. `index.html` has `api-base=/ui`.
- **Code:** `interfaces/web/` holds `ConversationsResource`, `OneDriveResource`, `Templates`, `TemplateExtensions`, `Hx`, `SessionWaiter`, `HistoryGroup`; the Qute templates are in `src/main/resources/templates/`.
- **Tests:** `WebTest` (34), `WebWaitTest` (3), `HxTest` (3), `WebLayeringTest` (2). Checked that the escaping test fails when `.raw` is used, and that a template typo fails the build.
- **Docs:** new `moby-bank-quarkus/docs/ui-fragments.md`; updated the architecture, configuration, testing and API pages, both READMEs, `CLAUDE.md`; `.DS_Store` is ignored.
- **Run for real:** started the packaged app and drove it with curl: send (waits for the reply), upload and OneDrive chips, move, history, compression.

Departures from the plan above:

- A failed, refused or slow move answers `200` with `HX-Reswap: none` and an `error` toast with the id `move-cloud`. Without it the page's loading toast would spin forever. Other errors still show the page's generic "Request failed".
- `sent.html` is a separate template: it carries the new messages plus the out-of-band removal of the empty-thread prompt.
- A running or moving session's pane shows the `#thinking` row and a disabled move button.

Not checked: the page in a browser (swaps, the dialog and toasts).

## Correction: the first serving setup broke dev mode

The first version listed the repository root (`..`, with `includes=index.html`) as a Maven `<resource>`. The tests and a packaged run passed, but `./mvnw quarkus:dev` failed with `File name too long`: Quarkus dev mode and the test bootstrap copy a resource directory whole, ignoring `includes`, so the whole repository (`.git` included, 305 MB) was copied into `target/classes`, and it contained `moby-bank-quarkus/target/classes`, which was copied again, without end. The packaged jar was affected too. Fixed by a `copy-resources` execution that copies only `index.html`, plus `BuildLayoutTest`, which fails if a resource directory points above the module. Verified by running dev mode: it starts, serves `/`, and a refreshed `index.html` is served; `target/classes` stays 752 KB.
