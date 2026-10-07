# References for htmx UI on Quarkus

## Similar Implementations

### `index.html` (repo root)

- **Relevance:** the UI being served. Its `hx-*` attributes, element ids (`#main`, `#messages`, `#history`, `#folders`, `#dialog`, `#toasts`, `#pending`, `#thinking`) and the `HX-Trigger` events it listens for (`toast`, `close-dialog`, `folders-changed`, `conversations-changed`) are the contract.
- **Key patterns:** server returns fragments; `api-base` meta rewrites `/api` paths; `HX-Trigger: {"toast": {...}}` for toasts; a fragment containing `[data-close]` closes the dialog.

### Existing JSON API and events

- **Location:** `moby-bank-quarkus/src/main/java/com/mobybank/harness/interfaces/rest/SessionsResource.java`, `application/SessionEvent.java`, `application/*ApplicationService.java`
- **Relevance:** the web handlers call the same application services and subscribe to the same event stream to wait for a reply or a move.

### Moby Bank prototype (React/dc UI)

- **Location:** `moby-bank-prototype/`
- **Relevance:** the JSON client that stays available, and the model for grouping, step summaries and move wording.

## Tools

- Qute (`quarkus-rest-qute`): `@CheckedTemplate`, fragments (`{#fragment id=…}`, `template.getFragment(id)`), HTML auto-escape.
- htmx 2.x (`org.webjars.npm:htmx.org:2.0.11` if served locally).
