# API reference

The HTTP and server-sent-events API the chat UI uses. The machine-readable contract is
[`openapi.yaml`](../openapi.yaml): it is generated at build time from the code and committed, and **any other backend
(the planned Python one) must match it**. This page explains the behaviour around it, with real responses captured
from the running app. Live documentation is also served at `/q/swagger-ui` and `/q/openapi`.

(The htmx page `index.html` has its own HTML endpoints under `/ui`; they are not part of this contract. See
[ui-fragments.md](ui-fragments.md).)

Base URL in development: `http://localhost:8080`. Bodies are JSON (`Content-Type: application/json`) unless noted.
All values the API speaks are lower-case strings: `"local"`, `"assistant"`, `"idle"`.

## Endpoints

| Method and path | What it does | Success | Errors |
|---|---|---|---|
| `GET /api/me` | The signed-in analyst | 200 | |
| `GET /api/sessions` | The conversation history, most recent first | 200 | |
| `POST /api/sessions` | Start a conversation | 201 + `Location` header | 400 |
| `GET /api/sessions/{id}` | Open a conversation (messages, files) | 200 | 400, 404 |
| `POST /api/sessions/{id}/messages` | Send a message; the agent starts on it | 202 | 400, 404, 409 |
| `POST /api/sessions/{id}/uploads` | Upload a file (multipart) | 201 | 400, 404 |
| `POST /api/sessions/{id}/move` | Move the session to the other location | 202 | 400, 404, 409 |
| `GET /api/sessions/{id}/events` | Follow the session live (SSE) | 200 stream | 404 |
| `GET /api/folders` | The folder library, each flagged connected or not | 200 | 502 |
| `POST /api/folders/connect` | Connect folders; returns the updated library | 200 | 400, 404, 502 |
| `GET /api/folders/connected/files` | Documents in the connected folders | 200 | 502 |

Operational endpoints: `GET /q/health`, `GET /q/openapi`, `GET /q/swagger-ui`.

## Conventions

- **Ids** are strings. Session and message ids are UUIDs; a session id in a path is accepted in any letter case and
  is returned in its canonical (lower-case) form. A malformed id is a 400, an unknown one a 404.
- **Times** are ISO-8601 instants in UTC, for example `2026-09-30T21:43:46.990013703Z`.
- **Enumerations** (all lower case):

  | Field | Values |
  |---|---|
  | `location`, `moveTarget`, move `target` | `local`, `cloud` |
  | `status` | `idle`, `running` (the agent is working), `moving` |
  | message `role` | `user`, `assistant` |
  | file `source` | `upload`, `onedrive` |
  | step `kind` | `read`, `compute` |
  | `group` | `Today`, `Previous 7 days`, `Earlier` |
  | move `stage` | `packaging`, `transferring` |

- **Nullable fields:** `moveTarget` (null unless `status` is `moving`) and a message's `table` (null when the agent
  returned none).
- **Errors** are always `{"error": "<code>", "message": "<text>"}`. Codes: `bad_request` (400), `not_found` (404),
  `conflict` (409), `sandbox_failure` and `document_source_failure` (502).
- **Cross-origin:** the app allows `http://localhost:4173` and `http://127.0.0.1:4173` (`quarkus.http.cors.*` in
  `application.properties`).
- **Asynchronous work:** sending a message and moving a session return **202 at once**. The agent's steps and reply,
  and a move's progress and outcome, arrive on the event stream, and are also visible by reading the session.

## Users

### `GET /api/me`

```json
{"id": "demo-analyst", "name": "Hermione Granger", "role": "Credit Research", "initials": "HG"}
```

Configured by `harness.user.name` and `harness.user.role`. There is no sign-in; every caller is the same analyst.

## Sessions

### `GET /api/sessions`

The history rows, most recently updated first. `group` is the label the sidebar groups by (calendar days in the
server's time zone).

```json
[
  {"id": "c08cdbec-aade-3b8f-ac14-926ce00dbcde", "title": "Fathom Industrial — Q2 2026 earnings",
   "location": "local", "status": "idle", "group": "Today", "updatedAt": "2026-09-30T21:43:46.104503786Z"},
  {"id": "34849100-4927-3785-84a5-33ee31ccbccc", "title": "Kestrel Foods — earnings release review",
   "location": "cloud", "status": "idle", "group": "Today", "updatedAt": "2026-09-30T12:51:53.052251893Z"}
]
```

### `POST /api/sessions`

Body (optional fields): `{"location": "local"}`. Omit the body's `location`, or send `{}`, for a local session.
Returns **201** with a `Location` header and the new, empty session:

```json
{"id": "00ca3b14-9fca-4055-badc-a95d668422a8", "title": "New conversation", "location": "local",
 "status": "idle", "moveTarget": null, "group": "Today",
 "createdAt": "2026-09-30T21:43:46.990013703Z", "updatedAt": "2026-09-30T21:43:46.990013703Z",
 "messages": [], "files": []}
```

An unknown location is a 400: `{"error":"bad_request","message":"unknown location: mars (expected local or cloud)"}`.

### `GET /api/sessions/{id}`

The whole conversation. After one exchange (trimmed):

```json
{
  "id": "00ca3b14-9fca-4055-badc-a95d668422a8",
  "title": "Summarize these for me",
  "location": "local", "status": "idle", "moveTarget": null, "group": "Today",
  "messages": [
    {"id": "0427ca0a-…", "role": "user", "text": "Summarize these for me",
     "files": [{"name": "notes.txt", "source": "upload"},
               {"name": "Fathom_Q2_2026_10-Q.pdf", "source": "onedrive"}],
     "steps": [], "paragraphs": [], "table": null, "createdAt": "2026-09-30T21:43:48.042802662Z"},
    {"id": "f6e1ed37-…", "role": "assistant", "text": "", "files": [],
     "steps": [{"kind": "read", "label": "notes.txt"},
               {"kind": "read", "label": "Fathom_Q2_2026_10-Q.pdf"},
               {"kind": "compute", "label": "extract_financials(income_statement, balance_sheet, cash_flow)"}],
     "paragraphs": ["I read notes.txt, Fathom_Q2_2026_10-Q.pdf and extracted the income statement, …"],
     "table": {"cols": ["Metric", "Current", "Prior year", "Change"],
               "rows": [["Revenue", "$2.46B", "$2.31B", "+6.5%"], …]},
     "createdAt": "2026-09-30T21:43:48.486489829Z"}
  ],
  "files": [{"name": "notes.txt", "source": "upload"}, {"name": "Fathom_Q2_2026_10-Q.pdf", "source": "onedrive"}]
}
```

- A user message has `text` and `files`; an assistant message has `steps`, `paragraphs` and maybe a `table`. Fields
  that don't apply are empty, not absent.
- `title` is derived from the first message (its first 48 characters, or the first file's name if it had no text).
- `files` is the distinct files shared anywhere in the conversation, in the order they first appeared.

### `POST /api/sessions/{id}/messages`

```json
{"text": "Summarize these for me",
 "files": [{"name": "notes.txt", "source": "upload"}, {"name": "Fathom_Q2_2026_10-Q.pdf", "source": "onedrive"}]}
```

Returns **202 Accepted** with the stored user message (its `steps`, `paragraphs` and `table` are empty and null yet).
The agent is now working; follow it on the event stream.

- `text` may be empty if `files` is not; the message text then becomes "Review the attached files.".
- An `upload` file must already have been uploaded to this session (next endpoint); a `onedrive` file is found in the
  catalog by exact name when the agent needs it.
- **400** if there is no text and no file, or an attachment lacks a name or a valid `source`.
- **409** if the session is running or moving:
  `{"error":"conflict","message":"Session … is RUNNING and cannot accept this operation"}`.

### `POST /api/sessions/{id}/uploads`

`multipart/form-data` with one part named `file`. Returns **201**:

```json
{"name": "notes.txt", "source": "upload"}
```

Send that object in a message's `files` to attach it. The stored name is the **base name only**: a submitted name
such as `../../etc/passwd` is stored as `passwd`, and names that are not files (`..`, `dir/`) are a 400. The request
size limit is `quarkus.http.limits.max-body-size` (50M).

### `POST /api/sessions/{id}/move`

```json
{"target": "cloud"}
```

Returns **202** with the session in the moving state (`"status": "moving", "moveTarget": "cloud"`, and `location`
still the old one). The location changes when the move finishes.

- **400** for a missing or unknown target.
- **409** if the session is running or moving, or if `target` is where it already runs
  (`…from CLOUD to CLOUD: the session already runs there`).

## Live events

### `GET /api/sessions/{id}/events`

A server-sent-events stream (`text/event-stream`) for one session. Open it **before** sending a message or starting a
move, and wait for `ready`, then nothing you trigger can be missed. Only events published after you subscribed are
delivered: read the session to catch up.

Each event has a name (`event:`) and JSON data (`data:`). Captured from a real message followed by a move (the lines
are shortened):

```
event:ready
data:{"sessionId":"00ca3b14-…"}

event:message
data:{"sessionId":"00ca3b14-…","message":{"id":"0427ca0a-…","role":"user","text":"Summarize these for me","files":[…]}}

event:thinking
data:{"sessionId":"00ca3b14-…","label":"Reading 2 files on local model…"}

event:step
data:{"sessionId":"00ca3b14-…","step":{"kind":"read","label":"notes.txt"}}

event:step
data:{"sessionId":"00ca3b14-…","step":{"kind":"compute","label":"extract_financials(income_statement, balance_sheet, cash_flow)"}}

event:message
data:{"sessionId":"00ca3b14-…","message":{"id":"f6e1ed37-…","role":"assistant","steps":[…],"paragraphs":[…],"table":{…}}}

event:move-progress
data:{"sessionId":"00ca3b14-…","stage":"packaging","detail":"Packaging context · 2 messages · 2 files"}

event:move-progress
data:{"sessionId":"00ca3b14-…","stage":"transferring","detail":"Transferring files over private link · 2 messages · 2 files"}

event:moved
data:{"sessionId":"00ca3b14-…","location":"cloud"}
```

| Event | Data | Meaning |
|---|---|---|
| `ready` | `{sessionId}` | The subscription is active. Also sent on every reconnect |
| `thinking` | `{sessionId, label}` | The agent started; the label is for display ("Reading 2 files on cloud model…") |
| `step` | `{sessionId, step:{kind,label}}` | The agent read a file or ran a calculation, as it happens |
| `message` | `{sessionId, message}` | A message joined the conversation: first the user's, later the agent's (or a failure notice) |
| `move-progress` | `{sessionId, stage, detail}` | A move advanced; `detail` is for display |
| `moved` | `{sessionId, location}` | The move finished and the session now runs at `location` |
| `move-failed` | `{sessionId, reason}` | The move failed and the session stayed where it was |

The typical order for a message is `message` (user), `thinking`, zero or more `step`, `message` (assistant). When the
agent fails, the last `message` is an assistant message whose text starts "The agent could not complete this
request:". A line `: keep-alive` is sent every 20 seconds. The `content-type:application/json` line the server puts in
each event is an unknown SSE field and is ignored by clients.

The same message can be seen twice by a client that also reads the `202` response, so de-duplicate by message `id`.

## Folders

### `GET /api/folders`

```json
[
  {"id": "f1", "name": "Earnings 2026", "path": "Credit Research / Coverage / Earnings 2026",
   "fileCount": 24, "connected": true},
  {"id": "f2", "name": "Client Financials", "path": "Corporate Banking / Clients / Financials",
   "fileCount": 58, "connected": true}
]
```

Every folder that can be connected. The sidebar shows the ones with `connected: true`. With the default fake catalog
there are six folders and the first three start connected; with OneDrive nothing starts connected.

### `POST /api/folders/connect`

```json
{"folderIds": ["f4"]}
```

Connects the folders (ones already connected are ignored) and returns the **updated library** in the shape above.
**400** if `folderIds` is missing or empty; **404** (`not_found`) if any id is not in the catalog, in which case
nothing is connected; **502** if OneDrive can't be reached.

### `GET /api/folders/connected/files`

The documents in the connected folders, with their folder's details, for the attach dialog:

```json
[{"folderId": "f1", "folderName": "Earnings 2026", "folderPath": "Credit Research / Coverage / Earnings 2026",
  "name": "Fathom_Q2_2026_10-Q.pdf"}]
```

## Changing the API

1. Change the DTOs or the resource. DTOs live in `application` and carry `@Schema` annotations with the allowed
   values; keep them lower-case strings.
2. Run `./mvnw test`. The build regenerates `openapi.yaml`; **commit the change**.
3. `OpenApiContractTest` fails if an endpoint disappears or an enumeration loses a value, and `DocumentationTest`
   fails if this page stops mentioning an endpoint. Update this page.
4. The UI (`moby-bank-prototype`) and the Python backend are the other two consumers: check both.
