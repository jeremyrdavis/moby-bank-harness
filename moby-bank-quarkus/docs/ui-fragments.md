# The htmx page and its HTML endpoints

`index.html` at the repository root is a single-file [htmx](https://htmx.org/) client for the analyst agent. The
Quarkus app serves it at `/` and answers the page's calls with **fragments of HTML** rendered by Qute. This is a second
front end beside the JSON API in [api.md](api.md): same application services, a different shape of answer. The JSON API
and `openapi.yaml` (the contract the Python backend must match) are untouched by it.

## How it is wired

- **Serving.** The page is not kept in the module. A `maven-resources-plugin` execution in `pom.xml` copies
  `../index.html` to `target/classes/META-INF/resources` at build time, so Quarkus serves it at `/`. After editing it in
  dev mode run `./mvnw resources:copy-resources@copy-index-page`. (The parent directory must not be a `<build><resources>` entry: dev mode and tests copy a
  resource directory whole, ignoring `<includes>`, which copied the whole repository into `target/classes`.
  `BuildLayoutTest` fails if it comes back.)
  htmx comes from the `org.webjars.npm:htmx.org` jar (`/webjars/htmx.org/2.0.11/dist/htmx.min.js`) and the Manrope font
  from the `org.mvnpm.at.fontsource:manrope` jar (`/_static/at/fontsource/manrope/5.3.0/`), so the page needs no CDN.
- **The `/ui` prefix.** Every path in the page starts with `/api`. A script in the page rewrites that prefix to the
  `api-base` meta tag, which is `/ui`, so the page's `/api/conversations` reaches `/ui/conversations`. The HTML
  endpoints therefore stay out of the JSON API's namespace, and `mp.openapi.scan.exclude.packages` keeps them out of
  `openapi.yaml`. Fragments the server renders also write `/api/...` paths, so `api-base` stays the one place that says
  where the endpoints live.
- **The code.** `interfaces/web/`: `ConversationsResource` and `OneDriveResource` (the endpoints), `Templates` (a
  `@CheckedTemplate` method per fragment; the build fails if a template names a property that does not exist),
  `TemplateExtensions` (view helpers such as `stepSummary`), `Hx` (answering htmx: fragments, events, toasts),
  `SessionWaiter` (waiting for background work), and `HistoryGroup`. The templates are in `src/main/resources/templates/`.
  `WebLayeringTest` keeps this package to the application layer.
- **Stateless.** The server remembers nothing about the page. Which conversation is open is the `moby-current`
  cookie, so a reload returns to it. Files waiting to be sent are chips with hidden inputs in the page's form.

## Requests wait for the work

The page has no live channel, so it cannot be told later that the agent has answered. Instead the request that sends a
message (or starts a move) stays open until the outcome. `SessionWaiter` subscribes to the session's events, starts the
work through the application service, and waits for the agent's reply, or for `moved` or `move-failed`, for at most
`harness.ui.wait-timeout` (default `180s`, see [configuration.md](configuration.md)). The JSON API is unaffected: it
still answers `202` and streams the events.

If the wait runs out, the page gets what it can (the user's own message) and a toast saying the agent is still working.
The work carries on; opening the conversation again later shows the reply.

The result is that a message and the agent's steps appear together after the reply, not as the agent works.

## The endpoints

Paths are as the page writes them. Served under `/ui`.

| Page call | Swapped into | Answer |
|---|---|---|
| `GET /api/conversations/current` (on load) | `#main` | The conversation the cookie names, else the most recent, else a new one |
| `GET /api/conversations/{id}` | `#main` | That conversation's pane. Sets the cookie; raises `conversations-changed` |
| `POST /api/conversations` | `#main` | A new empty conversation (always a new session). Raises `conversations-changed` |
| `GET /api/conversations` (on load, `conversations-changed`) | `#history` | The history grouped Today, Previous 7 days, Earlier; the open one has `aria-current="true"` |
| `POST /api/conversations/{id}/messages` (multipart: `text`, `files`, `onedrive`) | `#messages`, beforeend | The user's message and the agent's reply; removes `#empty-state` out of band; raises `conversations-changed` |
| `POST /api/conversations/{id}/move?to=cloud` | `#main` | The pane at the new location, and a success toast replacing the page's loading toast `move-cloud` |
| `GET /api/onedrive/folders` (on load, `folders-changed`) | `#folders` | The connected folders with their file counts |
| `GET /api/onedrive/picker?mode=connect` or `mode=attach` | `#dialog` | A dialog: every folder, or the files in the connected folders |
| `POST /api/onedrive/connect` (form field `folder`) | nothing | Connects them; raises `folders-changed`, `close-dialog` and a toast |
| `POST /api/onedrive/attach` (form field `file`) | `#pending`, beforeend | A chip per file, each with a hidden `onedrive` input. Only files in connected folders are accepted |

Status codes follow the JSON API: `400` bad input, `404` unknown conversation or folder, `409` busy.

## What the page listens for

Fragments tell the page what to do through `HX-Trigger` response headers (JSON, ASCII only):

| Event | Effect in the page |
|---|---|
| `toast` | Shows a toast from `{id, variant, title, description}`; an `id` updates that toast in place |
| `close-dialog` | Closes the dialog |
| `folders-changed` | Reloads the folder list |
| `conversations-changed` | Reloads the history |

A move that cannot start, fails, or outlasts the wait answers `200` with `HX-Reswap: none` and a `toast` using the id
`move-cloud` and variant `error`, so the page's loading toast does not spin forever.

## Known limits

- The page offers **Move to cloud** only. A cloud session shows a disabled "Running in cloud" button. Moving back is
  available through the JSON API.
- The page has no error styling for a toast; the variant `error` currently shows with the success icon.
- Errors other than moves show the page's generic "Request failed" toast.
- Not checked in a browser: swaps, the dialog and toasts need a look on a machine with one (the tests assert the HTML
  and headers, not the rendering).
