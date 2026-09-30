# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

The application lives in `moby-bank-prototype/`; all paths below are relative to that folder, and commands must be run from it. The repo root holds only this file, `.claude/`, and `agent-os/` (Claude tooling).

## What this is

A front-end prototype of a bank credit-analyst agent chat UI ("Moby Bank — Analyst Agent Harness"), built on Docker's Trident design system. Agent replies, file reads, and the "move to cloud" action are all simulated client-side with `setTimeout`; there is no backend. The product goal (see `agent-os/product/`) is moving sessions in both directions between local and cloud Docker Sandboxes; only local → cloud exists in the prototype, and cloud → local is a planned MVP feature. See `README.md` for the feature tour.

## Commands

```bash
cd moby-bank-prototype
npm start            # or: npm run dev — serves http://localhost:4173/ (override with PORT=3000)
```

- There is no build step, bundler, linter, or test suite, and nothing to `npm install`. `serve.mjs` is a zero-dependency Node 18+ static server.
- The app **must** be served over HTTP. The runtime uses `fetch` and dynamic script loading, so opening the `.dc.html` via `file://` fails.

## Architecture

This is a **Claude Design "dc" component**, not a normal JS app. Nearly all application code lives in one file, `Bank Agent Harness.dc.html`, which has two halves:

1. **`<x-dc>` template** (markup + Trident component usage).
2. **`<script type="text/x-dc" data-dc-script>`** at the bottom: seed data (`FOLDER_LIB`, `SEED`) and `class Component extends DCLogic` holding all state and handlers.

`support.js` is the dc-runtime. It parses the template, `new Function`-evals the logic script (which must define `class Component extends DCLogic`), and mounts the result with React into `#dc-root`. React/ReactDOM are vendored in `_vendor/` and loaded before `support.js`, which skips its CDN loader when they already exist.

### Things that aren't obvious

- **`support.js` is generated** (header: "GENERATED from dc-runtime/src/*.ts — do not edit"). The source isn't in this repo; don't hand-edit it.
- **`_ds/trident-…/` is a vendored design-system bundle** exposed as the `window.Trident.*` global (Button, Dialog, Checkbox, Spinner, Toaster, `toast`). Treat it as read-only too.
- **Template data flow:** the template renders against the flat object returned by `renderVals()`. Every value, handler, and precomputed style the template uses must be a key of that object. Per-item handlers and styles are built inside `renderVals()` (e.g. `toggleSteps`, `select`, `toggle`, `remove`) rather than in the template.
- **`{{ … }}` is not JavaScript.** The runtime's expression resolver (`resolve`/`resolvePath` in `support.js`) supports only property paths, literals, `!`, and `==`/`===`/`!=`/`!==`. Put any other logic (ternaries, `&&`, string concatenation, calls) in `renderVals()` and expose a boolean or string, which is why the code has flags like `isDark`/`isLight`, `isCloud`/`isLocal`, and `noSessionFiles`.
- **Template tags:** `<sc-if value>` and `<sc-for list as>` for control flow (there's no else, so use paired inverse flags). `<x-import component-from-global-scope="Trident.X">` pulls in a Trident component, with props kebab-cased (`class-name`, `on-click`, `on-open-change`). The `hint-size` / `hint-placeholder-*` attributes are design-tool layout hints, so keep them when adding elements.
- **Props:** `layout` (`sidebar` | `panel`) and `theme` (`light` | `dark`) are declared in the `data-props` attribute on the script tag and read via `this.props.*`. Theme is copied into `state.dark`, and the in-app toggle only changes state. `rootClass` and `dialogClass` add the `product` and `dark` classes that switch Trident's token set, and the Dialog needs its own copy because it portals outside the root.
- **Styling:** use Trident semantic tokens (`var(--tri-…)` or Tailwind utilities like `bg-background`) instead of hardcoded colors so light/dark both work. The fonts (Manrope, JetBrains Mono) are vendored under `_ds/…/fonts/` and cover the Latin range only.
- **Conversation model:** `state.convos` is seeded from `SEED`. Messages carry `steps` (`{kind: 'read' | 'compute', label}`), `paras`, and an optional `table`. `renderVals()` derives the step summary ("Read N files, ran N calculations") and row shapes from these. `updateActive(fn)` is the helper for mutating the active conversation immutably.
- **Mock "agent":** `send` appends a user message, sets `thinking`, and after a 1.8s `setTimeout` appends a canned reply (and a canned table when files were attached). `moveToCloud` is a scripted two-stage toast sequence that sets `cloud: true` on the conversation. Nothing sets it back to `false` yet, and the header shows a disabled "Running in cloud" button for cloud sessions (the `isCloud` block), which is where a cloud → local action would go.
