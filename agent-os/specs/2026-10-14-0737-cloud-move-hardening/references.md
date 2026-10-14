# References for Cloud move hardening

## Similar Implementations

### sbx adapters

- **Location:** `moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/sbx/` (`SbxCli`, `SbxSandboxAgent`, `SbxSandboxTransfer`, `SandboxRegistry`, `SbxSettings`, `SbxAdapters`, `ProcessCommandRunner`)
- **Relevance:** where expiry detection, recovery, lifetime flags, preflight and error mapping go.
- **Key patterns:** `SbxCli` is the only place `sbx` arguments are built; `SandboxRegistry.nameFor` and `generationOf` give the generation naming; `prompt(turn, includeHistory)` already replays history when there is no agent session to resume.

### Move flow

- **Location:** `application/SessionApplicationService.java` (`move`, `runMove`, `failMove`, `runTurn`, `failTurn`), `domain/Session.java` (`beginMove`, `completeMove`, `abortMove`, `recordAgentReply`), `interfaces/web/ConversationsResource.java` (`move`), `interfaces/web/SessionWaiter.java`
- **Relevance:** how a reply, a failure and a move reach the page.
- **Key patterns:** a failed turn becomes an assistant message; a failed move is only a `move-failed` event and an error toast.

### Fakes and test doubles

- **Location:** `infrastructure/FakeSandboxAgent.java`, `FakeSandboxTransfer.java`, `Adapters.java`; tests `FakeRunner`, `SlowFakesProfile`, `application/Fakes.java` (`ScriptedAgent`, `ScriptedTransfer`)
- **Relevance:** how to add expiry and failure simulation.

### The page

- **Location:** `src/main/resources/templates/main.html`, `message.html`; `index.html` (toast script, `htmx:beforeRequest`); `docs/ui-fragments.md`
- **Relevance:** the Move to local button, the notice, the toast error icon.

### Earlier specs

- `agent-os/specs/2026-09-30-1452-quarkus-mvp-backend/` and `2026-10-01-1322-htmx-ui-on-quarkus/`.

### External

- Docker `sbx move` reference: https://docs.docker.com/ai/sandboxes/cloud/move/
- Cloud sandboxes: https://docs.docker.com/ai/sandboxes/cloud/
