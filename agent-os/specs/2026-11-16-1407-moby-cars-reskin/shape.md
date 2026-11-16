# moby-cars re-skin — Shaping Notes

## Scope

Re-skin the htmx page and Quarkus demo for a meeting with Cox Automotive (owner of autotrader.com):

1. Remove the "Sign in with Microsoft" option from the login page.
2. Build a used-car quarterly-report story: the previous quarter's report sits in the conversation history, and the next quarter's source documents are in the fake OneDrive so a new chat can produce the next report.
3. Rename the site to **moby-cars** and re-theme the persona from bank credit analyst to used-vehicle analyst.

## Decisions

- Report form: a seeded conversation (Q2 2026 report) plus Q3 source files in connected folders; the fake agent answers with a used-car Q3 report.
- Persona: full re-theme (folders, files, conversations, role, fake agent replies, real-agent preamble).
- Rename depth: user-visible only. The Java package `com.mobybank.harness`, the `moby-bank-<id>-g<n>` sandbox prefix, module folders, `artifactId`, cookies and the theme key stay.
- Microsoft: UI only. The OIDC and Graph backend stays, off by config (`harness.auth.microsoft.enabled=false` is the default).
- Visuals: none. Keep the Docker/Trident look; the logo mark "MB" becomes "MC".
- Logins: seeded users move to `@mobycars.com`; the developer account and passwords are unchanged.
- Out of scope: downloadable report files, multi-table messages, new UI components, the React prototype, renaming Java packages or the sandbox prefix.

## Context

- **Visuals:** None.
- **References:** `DemoData.java` session c1, `FakeSandboxAgent.answer`, `login.html`, the earlier htmx UI spec.
- **Product alignment:** The mission (engineering teams evaluating a custom agent harness) is unchanged; this is a cosmetic skin for one audience.

## Standards Applied

None. `agent-os/standards/index.yml` is empty.
