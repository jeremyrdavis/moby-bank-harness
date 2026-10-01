# Moby Bank — Analyst Agent Harness

A runnable implementation of the **Bank Agent Harness** design: a chat UI for a bank
credit-analyst agent, built on Docker's **Trident** design system. A fictional analyst
("Hermione Granger", Credit Research) works filings and OneDrive documents through an agent
that reads files, runs calculations, and returns prose + financial tables — with the
ability to move a session between the on-prem **local model** and **Moby Private Cloud**
(in both directions).

This is the front end only. It talks to a harness backend over the HTTP + server-sent-events
API described in [`../moby-bank-quarkus/openapi.yaml`](../moby-bank-quarkus/openapi.yaml); the
Quarkus backend in `../moby-bank-quarkus/` implements it (a Python one will follow).

## Running it

The app must be served over HTTP (the runtime uses `fetch` and dynamic script loading —
opening the `.dc.html` directly via `file://` will not work), and it needs the backend running.

```bash
# 1. the backend (from ../moby-bank-quarkus): simulated sandboxes and demo data by default
./mvnw quarkus:dev                  # http://localhost:8080

# 2. this UI
npm start                           # serves at http://localhost:4173/  (or: PORT=3000 npm start)
```

Then open <http://localhost:4173/>. No dependencies to install — `serve.mjs` is a
zero-dependency Node static server (Node 18+).

**Where the backend is:** the UI looks for it on the same host as the page, port 8080. To point it
elsewhere, add `?api=http://host:port` to the page URL, or set `window.HARNESS_API`. The backend
allows the origin `http://localhost:4173` (and `127.0.0.1`) for cross-origin calls. If it cannot be
reached, the UI shows a banner with a Retry button.

## What it demonstrates

- **Conversation history** grouped by recency (Today / Previous 7 days / Earlier).
- **OneDrive folder sources** — connect folders and attach documents via a picker dialog, plus
  uploads from the analyst's machine.
- **Agent step traces** — collapsible "Read N files, ran N calculations" with per-step
  `read` / `compute` detail. While the agent works, the latest step shows beside the spinner.
- **Financial tables** and multi-turn analysis, seeded with realistic credit-research data.
- **Live updates** — each open conversation has a server-sent-events stream, so the agent's
  steps and reply, and a move's progress, appear as they happen. A slow poll is the safety net
  if the stream drops.
- **Move to cloud / Move to local** — moves a session between the on-prem local model and
  Moby Private Cloud, in either direction, with progress toasts. A session can't take a new
  message while it is moving, or the other way round.
- **Light / dark themes** (toggle, bottom-left) and two layouts (`sidebar` / `panel`).

Layout and theme are component props (defaults: `sidebar`, `light`), configured in the
`data-props` block at the bottom of the `.dc.html`.

## How it's built

This is a **Claude Design** component (`.dc.html`) rendered by its runtime, not a bundler
project:

| Path | Role |
|------|------|
| `Bank Agent Harness.dc.html` | The component: an `x-dc` template + a `DCLogic` (React-style) class holding all state and behavior. |
| `support.js` | The dc-runtime: parses the template, evaluates the logic class, and mounts it with React. |
| `_vendor/react*.js` | React 18.3.1 + ReactDOM (UMD), vendored so the harness runs fully offline. |
| `_ds/trident-…/_ds_bundle.js` | The Docker **Trident** design system (`@docker/trident`) as a `window.Trident.*` browser global. |
| `_ds/trident-…/_ds_bundle.css` | Trident's compiled Tailwind v4 + semantic design tokens. |
| `_ds/trident-…/fonts/` | `fonts.css` (`@font-face` for Manrope + JetBrains Mono). |
| `serve.mjs` | Local static server. |
| `test/` | Node tests (`node:test`, no dependencies): see [Tests](#tests). |

The page loads React first, then `support.js`. On `DOMContentLoaded` the runtime parses
the `<x-dc>` template, compiles it, evaluates the `DCLogic` subclass, and renders into
`#dc-root`, consuming Trident components (Button, Dialog, Checkbox, Spinner, Toaster) from
`window.Trident`.

### Editing

- **Behavior** — edit the `<script data-dc-script>` block (the `DCLogic` class) at
  the bottom of the `.dc.html`. It holds no data of its own: conversations, folders and the
  user come from the backend. `api()` makes the calls, `handleEvent()` applies stream events.
- **Markup / styling** — edit the `x-dc` template. `{{ … }}` interpolates values returned
  by `renderVals()`; `<sc-if>` / `<sc-for>` are the conditional/loop tags;
  `<x-import component-from-global-scope="Trident.X">` pulls in a Trident component.
- Prefer Trident **semantic tokens** (`var(--tri-…)`, or Tailwind utilities like
  `bg-background`, `text-muted-foreground`) over hardcoded colors.

## Tests

```bash
npm test               # template + logic tests, no backend needed
npm run test:live      # end to end against a real backend (needs HARNESS_API, see below)
```

- **`test/template.test.mjs`** checks the template statically, since there is no browser in CI:
  every `{{ value }}` must be something `renderVals()` returns, every property read off a loop
  variable must exist on that list's items, every tag must be closed, and no expression may use
  anything the dc-runtime can't resolve. It is tested against deliberately broken templates too.
- **`test/logic.test.mjs`** runs the real component class in Node with a fake API: startup, what the
  template is given, sending, the event stream, moves in both directions, uploads and folders.
- **`test/live.test.mjs`** runs the same class against a real backend with real server-sent
  events (it checks that a reply arrives over the stream, not via the fallback poll). Start the
  backend with short fake delays, then:

  ```bash
  (cd ../moby-bank-quarkus && ./mvnw quarkus:dev -Dharness.fake.agent-delay-ms=300 \
      -Dharness.fake.move-packaging-ms=300 -Dharness.fake.move-transfer-ms=300)
  HARNESS_API=http://localhost:8080 npm run test:live
  ```

What they can't do is render the page: check the real UI in a browser once after larger template changes.

## Notes

- **Fonts**: the brand fonts are vendored for the Latin range this demo renders —
  **Manrope** (UI text) and **JetBrains Mono** (step traces / file paths), both as variable
  `.woff2` from the same `@fontsource` source Trident uses. Non-Latin ranges (Cyrillic,
  Greek, Vietnamese, Latin-ext) fall back through the design system's stacks; add the
  matching `.woff2` files to `_ds/trident-…/fonts/` and `fonts.css` if you need them.
- With the backend's default settings the agent, the sandboxes and OneDrive are simulated on the
  server (`harness.sandbox.mode=fake`, `harness.documents.mode=fake`), so the whole demo runs with
  nothing installed. See `../moby-bank-quarkus/README.md` to use real Docker Sandboxes and OneDrive.
