# Documentation

Everything about the Quarkus backend beyond the [module README](../README.md), written for the people who will work on
it. **New here? Start with [ONBOARDING.md](ONBOARDING.md).**

| Document | What it is for |
|---|---|
| [ONBOARDING.md](ONBOARDING.md) | A guided path for a new developer: set up, reading order, a 13-stop code review, the pull-request checklist, known rough edges, first exercises |
| [architecture.md](architecture.md) | The layers, the three main flows (send a message, move a session, follow events), consistency and concurrency, design decisions |
| [domain-model.md](domain-model.md) | The aggregates, the session state machine, every invariant and the test that proves it, value objects, events, ports |
| [api.md](api.md) | Every endpoint and the event stream, with real captured responses, and how to change the API |
| [ui-fragments.md](ui-fragments.md) | The htmx page `index.html` and the HTML endpoints behind it: how it is served, the fragment contract, how requests wait for the agent |
| [configuration.md](configuration.md) | Every `harness.*` property, what real vs fake means, and how to override settings |
| [integrations.md](integrations.md) | How the real Docker Sandboxes (`sbx`) and OneDrive (Microsoft Graph) adapters work, and what to verify on a real system |
| [ONEDRIVE_INTEGRATION.md](ONEDRIVE_INTEGRATION.md) | **A plan** (not built yet) for taking OneDrive from the stand-in to a real tenant with per-user access: decisions, target architecture, Microsoft setup, phases, API changes, security, tests, rollout and a backlog |
| [testing.md](testing.md) | The kinds of test, the test doubles, the guards, and where a new test goes |
| [apidocs/index.html](apidocs/index.html) | The generated Javadoc |

## The Javadoc

`apidocs/` is generated from the source by the Maven Javadoc plugin and checked in, so it can be read without building.
It covers the public and package-level API, including package-private classes such as `Dtos` and `ReplyFormatter`, and
opens on a landing page that maps the layers.

```bash
./mvnw javadoc:javadoc                     # regenerate into docs/apidocs (commit the result)
open docs/apidocs/index.html               # or: python3 -m http.server -d docs/apidocs 8000
```

GitHub shows `.html` files as source, so to read it, clone the repository and open the file, or serve the folder.
Regenerate it whenever public API or Javadoc changes. The output has no timestamps, so an unchanged source gives an
unchanged diff.

## Keeping the docs honest

[`DocumentationTest`](../src/test/java/com/mobybank/harness/DocumentationTest.java) runs with the normal tests and
fails when a document drifts from the code:

- every relative link in these documents resolves to a file;
- every class, method or test name written in backticks exists in the source;
- plan documents ([ONEDRIVE_INTEGRATION.md](ONEDRIVE_INTEGRATION.md)) describe work that does not exist yet, so only their
  "Current state" section is checked against the code; links are checked everywhere;
- every `harness.*` property the code reads is in [configuration.md](configuration.md), and every one documented there
  is read by the code;
- every endpoint in `openapi.yaml` appears in [api.md](api.md).

It cannot tell whether the *prose* is right. When you change behaviour, reread the page that describes it.

## Conventions

- Diagrams are [Mermaid](https://mermaid.js.org/), which GitHub renders in place.
- Class and method names are in backticks so the test above can check them; paths are relative to
  `src/main/java/com/mobybank/harness/` unless they start with `src/`.
- The repository root also has [`SANDBOX-KIT.md`](../../SANDBOX-KIT.md) (building a Docker Sandbox kit with the tools
  this module needs) and [`CLAUDE.md`](../../CLAUDE.md) (conventions for both modules).
