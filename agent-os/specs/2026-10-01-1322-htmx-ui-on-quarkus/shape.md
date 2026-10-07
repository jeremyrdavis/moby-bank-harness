# htmx UI on Quarkus — Shaping Notes

## Scope

Serve the root `index.html` (a single-file htmx client) from the Quarkus app and implement the HTML-fragment endpoints it calls, using Quarkus Qute, on top of the existing application services. The JSON `/api`, `openapi.yaml` and `moby-bank-prototype/` are unchanged.

## Decisions

- UI implemented in Quarkus with Qute and htmx (requested), using the page the user supplied.
- HTML endpoints live under `/ui` (set through `<meta name="api-base">`), so the JSON contract for the Python backend stays clean.
- New `interfaces.web` package that depends only on `application`; layering test added.
- Reply and move requests wait for completion (the page has no SSE), with a configurable timeout.
- "Current conversation" is a cookie; the server holds no UI state.
- htmx and the Manrope font are served from the classpath (WebJar and mvnpm jar), not from CDNs. Hardcoded user card uses Harry Potter names.
- Move to cloud only for now; error handling is added later.
- The contract in `index.html` comments (`htmx/API.md`) is missing, so it is derived from the page and documented in `moby-bank-quarkus/docs/ui-fragments.md`.

## Context

- **Visuals:** `index.html` at the repo root is the visual and behavioral reference (see `visuals/` pointer in `references.md`).
- **References:** `index.html`; the existing `SessionsResource` (SSE) and application services. See `references.md`.
- **Product alignment:** Consistent with `agent-os/product/`: same agent local or cloud, both move directions, OneDrive. Quarkus first; Python later reuses the JSON API.

## Standards Applied

None (`agent-os/standards/index.yml` is empty).

## Open Items

None. See "What was built" at the end of `plan.md`.
