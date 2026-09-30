# Moby Bank — Analyst Agent Harness

A runnable implementation of the **Bank Agent Harness** design: a chat UI for a bank
credit-analyst agent, built on Docker's **Trident** design system. A fictional analyst
("Hermione Granger", Credit Research) works filings and OneDrive documents through an agent
that reads files, runs calculations, and returns prose + financial tables — with the
ability to move a session between the on-prem **local model** and **Moby Private Cloud**
(in both directions).

## Running it

The app must be served over HTTP (the runtime uses `fetch` and dynamic script loading —
opening the `.dc.html` directly via `file://` will not work).

```bash
npm start           # serves at http://localhost:4173/
# or: PORT=3000 npm start
```

Then open <http://localhost:4173/>. No dependencies to install — `serve.mjs` is a
zero-dependency Node static server (Node 18+).

## What it demonstrates

- **Conversation history** grouped by recency (Today / Previous 7 days / Earlier).
- **OneDrive folder sources** — connect folders and attach documents via a picker dialog.
- **Agent step traces** — collapsible "Read N files, ran N calculations" with per-step
  `read` / `compute` detail.
- **Financial tables** and multi-turn analysis, seeded with realistic credit-research data.
- **Move to cloud** — promotes a local (on-prem) session to Moby Private Cloud, with
  progress toasts.
- **Move to local** *(planned, MVP)* — brings a cloud session back to the local model.
  Not implemented yet: today the cloud state is one-way and the header only offers
  "Move to cloud".
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

The page loads React first, then `support.js`. On `DOMContentLoaded` the runtime parses
the `<x-dc>` template, compiles it, evaluates the `DCLogic` subclass, and renders into
`#dc-root`, consuming Trident components (Button, Dialog, Checkbox, Spinner, Toaster) from
`window.Trident`.

### Editing

- **Behavior / data** — edit the `<script data-dc-script>` block (the `DCLogic` class) at
  the bottom of the `.dc.html`. Seed conversations live in `SEED`, folders in `FOLDER_LIB`.
- **Markup / styling** — edit the `x-dc` template. `{{ … }}` interpolates values returned
  by `renderVals()`; `<sc-if>` / `<sc-for>` are the conditional/loop tags;
  `<x-import component-from-global-scope="Trident.X">` pulls in a Trident component.
- Prefer Trident **semantic tokens** (`var(--tri-…)`, or Tailwind utilities like
  `bg-background`, `text-muted-foreground`) over hardcoded colors.

## Notes

- **Fonts**: the brand fonts are vendored for the Latin range this demo renders —
  **Manrope** (UI text) and **JetBrains Mono** (step traces / file paths), both as variable
  `.woff2` from the same `@fontsource` source Trident uses. Non-Latin ranges (Cyrillic,
  Greek, Vietnamese, Latin-ext) fall back through the design system's stacks; add the
  matching `.woff2` files to `_ds/trident-…/fonts/` and `fonts.css` if you need them.
- This is a **front-end prototype** — agent responses, file reads, and the cloud move are
  simulated in the client (`setTimeout`; the cloud → local move isn't built yet), not wired to a backend.
