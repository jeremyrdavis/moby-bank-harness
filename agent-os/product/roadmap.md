# Product Roadmap

## Phase 1: MVP

Status of each item in the Quarkus implementation (`moby-bank-quarkus/` with the UI in `moby-bank-prototype/`).

| Item | Status |
|---|---|
| Chat UI with agent traces | Done. Live over server-sent events; the agent's steps show as they happen. |
| Real local sandbox backend | Built (`harness.sandbox.mode=sbx`). Commands are tested against a fake runner; **not yet run against a real `sbx`**. |
| Real cloud sandbox backend | Built with the same adapter (`sbx --cloud`). **Not yet run against a real `sbx`.** |
| Move session local → cloud | Done in the app and API (`sbx move --to cloud`). Real `sbx move` unverified. |
| Move session cloud → local | Done in the app and API (`sbx move --to local`). Real `sbx move` unverified. |
| Integration with Microsoft OneDrive | Built (`harness.documents.mode=graph`, Microsoft Graph). Tested against a stand-in for Graph; **not yet run against a real tenant**. |

Everything runs with simulated sandboxes and OneDrive by default, so the demo needs nothing installed.

Backend delivery: two implementations of the backend, built sequentially, one in Java/Quarkus and one in Python (see `tech-stack.md`). The Quarkus one is done; the Python one is next and must match `moby-bank-quarkus/openapi.yaml`.

### Verify on a real host before relying on it

- the `sbx` commands and the agent's headless flags and stream format (`moby-bank-quarkus/README.md`)
- a real OneDrive drive with the intended Entra app permissions
- the UI in a browser: the page was tested through its logic and template, not rendered

## Phase 2: Post-Launch

To be determined

Known gaps, not committed work: per-user sign-in (every analyst sees the one configured drive), persistence across restarts (state is in memory), cleaning up the stopped source sandbox after a move, and showing the agent's text as it streams.
