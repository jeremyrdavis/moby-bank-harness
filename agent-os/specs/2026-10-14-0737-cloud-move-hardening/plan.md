# Plan: Verify and harden local ↔ cloud session moves

## Context

The roadmap marks local→cloud, cloud→local and the cloud adapter as built, but none of it has run against a real `sbx`
(a local-sandbox run has now been done; findings still to be shared). Docker's `sbx move` / cloud docs show gaps in the
adapters:

- Cloud sandboxes expire (1h default, deleted on expiry). `SbxSandboxAgent.sandboxFor` trusts the registry handle and
  never checks it still exists, so a demo idle for an hour fails with a raw `sbx exec` error.
- `--on-timeout stop|delete|restart` is never passed; `harness.sbx.cloud-ttl` has no default.
- Managed secrets don't travel on a move (needs `sbx --cloud secret set anthropic`); there is no preflight.
- Platform, disk (up to 32 GiB for cloud→local) and size limits surface as raw `sbx` errors.
- The htmx page offers "Move to cloud" only, although `POST /ui/conversations/{id}/move?to=local` already works.

Goal: a session can move either way on a real host, an expired cloud sandbox is recreated from history **with a visible
notice to the analyst**, and the demo fails with clear messages. This lands **before** the Python backend because it
adds a field to `openapi.yaml` that Python must match. OneDrive/`graph/` is handled in another session: do not touch it.

## Decisions (from shaping)

- **Visibility:** nullable `notice` on the assistant message (persisted, styled in the page, **not** replayed into
  prompts). Not a transient event, not a prose paragraph.
- **Cloud lifetime:** default `harness.sbx.cloud-ttl=8h` and new `harness.sbx.cloud-on-timeout=stop`, passed to `create`
  and `move`.
- **Source cleanup after a move:** deferred to Phase 2 (stays a roadmap gap). `harness.sbx.remove-source-after-move`
  remains only a proposed setting.
- **Standards:** `agent-os/standards/index.yml` is empty; the governing rules are the DDD skills (`ddd-foundations`,
  `ddd-aggregates`, `ddd-value-objects`, `ddd-services`) plus the layering tests and `DocumentationTest`.
- **Visuals:** none; follow `index.html` styling and the existing move toast.
- **References:** the sbx adapters, the move flow (`SessionApplicationService`, `Session`, `ConversationsResource`), and
  the specs `2026-09-30-1452-quarkus-mvp-backend` and `2026-10-01-1322-htmx-ui-on-quarkus`.

## Task 1: Save spec documentation

Create `agent-os/specs/2026-10-14-0737-cloud-move-hardening/` with `plan.md` (this plan), `shape.md` (scope and
decisions above), `standards.md` (the DDD skills and tests as the governing rules, no standards files), `references.md`,
`visuals/` (empty), and `verification.md` (the Task 2 checklist). Work on a branch `feature/cloud-move-hardening`;
commit per task as `Task N: …`, like earlier specs.

## Task 2: Real-host verification (hand-off to the user's sbx host)

`sbx` is not installed in this sandbox, so I write the checklist in `verification.md` and the user runs it on the host
with `sbx` ≥ 0.45.0 and a cloud subscription, pasting the output back. Findings drive Tasks 3–7, so this goes first.
Checklist, each with the exact command and what to record:

1. `sbx --version`; `sbx --cloud diagnose`; how a cloud anthropic secret is listed or checked (for the preflight).
2. `sbx create --name … claude` locally and with `--cloud`, with no workspace; does `--on-timeout` work on `create` or
   only on `move`?
3. `sbx ls -q` and `sbx --cloud ls -q`: exact name forms (`agent/name`, suffix).
4. The headless agent command the adapter issues: `claude -p … --output-format stream-json --verbose` and
   `--resume <id>`; compare the stream against `ClaudeStreamParser`. Include anything the local run showed.
5. `sbx move --to cloud --force --name …` and `--to local`: duration, destination name, whether the agent's conversation
   resumes, whether the source is stopped.
6. `exec` on a stopped cloud sandbox: does it resume, or need a start command?
7. The exact stderr and exit code of `exec` and `move` against a deleted or expired sandbox, a platform mismatch, a full
   disk and an oversized sandbox. Use a short TTL to force expiry.

Output: update the **(verify)** markers in `docs/integrations.md` and the README list with what was confirmed; correct
`SbxCli` where the real CLI differs. Error strings for Task 7 are taken from this output, not guessed.

## Task 3: Cloud lifetime settings

- `SbxSettings`: add `cloudOnTimeout`; `SbxAdapters` reads `harness.sbx.cloud-on-timeout`; default `cloud-ttl` to 8h.
- `SbxCli.create` and `SbxCli.move`: pass `--on-timeout <value>` for cloud destinations only (create only if Task 2
  shows it is accepted). Reuse `formatDuration`.
- Tests: extend `SbxCliTest` (argument lists asserted exactly) and `SbxSandboxTransferTest`.
- Docs: `docs/configuration.md` (the `harness.sbx.*` table; `DocumentationTest` requires exact key parity), the comment
  block in `application.properties`, `docs/integrations.md` (cloud expiry section, command table).

## Task 4: Detect an expired sandbox and recover (domain + sbx adapter)

Domain (plain Java, layering tests apply):
- `AgentReply` gains `Optional<String> notice`; keep the existing 3-argument constructor delegating to the new one so
  the 12 call sites don't change.
- `Message` carries the `notice` (`Message.assistant`, `rehydrate`); `Session.recordAgentReply` passes it through.
- `AgentTurn` carries the session's shared files (`Session.filesShared`) so a recreated sandbox can be re-staged; keep
  the 4-argument constructor.

Adapter (`SbxSandboxAgent`, `SandboxRegistry`):
- On a failed run, check `cli.listNames(location)` for the handle's name. If absent (or the failure matches the gone
  pattern from Task 2): `registry` gets a `forget(session)`, create the next generation (`nameFor`), forget the agent
  session, re-stage **all** shared files (extend `stage`), and re-run with history in the prompt (`prompt(turn, true)`).
  Reply with `notice` such as "The cloud sandbox had expired, so a new one was started from the conversation history.
  Files you attached earlier were copied in again; anything the agent installed or created is gone."
- Local sandboxes use the same path (a deleted local sandbox), with wording by location.
- `SbxSandboxTransfer.source`: if the cloud source is no longer listed, report it in `MoveProgress` and treat it as
  "nothing to package" (the next turn starts at the destination with history).
- If Task 2 shows a stopped cloud sandbox needs an explicit start, add that to `SbxCli` and use it before recreating.
- Tests with `FakeRunner` (fail `exec` once, omit the old name from `ls`; assert `create`, file re-staging, no
  `--resume`, the notice) in `SbxSandboxAgentTest` and `SbxSandboxTransferTest`; a `Session` test for the notice.

## Task 5: Carry the notice through the API and page

- `MessageDTO` gets a nullable `notice`; `Dtos.message` maps it. It travels on the existing `message` SSE event and in
  `GET /api/sessions/{id}`.
- Regenerate and commit `openapi.yaml`; update `docs/api.md` (JSON examples, failure/notice sentence),
  `docs/domain-model.md`, and `docs/ui-fragments.md`.
- `message.html`: render the notice as a styled note above the reply paragraphs. `@CheckedTemplate` fails the build on a
  typo. Never use `.raw`.
- `moby-bank-prototype`: ignores the unknown field; add a small render only if cheap, otherwise note it in the spec.
- Tests: `WebTest` (notice shown, escaped), a REST test for the DTO field, `ScriptedAgent`-based test in
  `SessionApplicationServiceTest`.

## Task 6: Preflight diagnostics (non-fatal)

- `SbxCli`: add `version()`, `diagnose(Location)` and the secret check confirmed in Task 2, listed in the command table in
  `docs/integrations.md`.
- A readiness `HealthCheck` in `infrastructure.sbx`, active only for `harness.sandbox.mode=sbx`: `sbx` present and
  ≥ 0.45.0, `sbx --cloud diagnose` ok, cloud anthropic secret present. Cache the result briefly so `/q/health` doesn't
  shell out on every hit. It must catch the `SandboxFailureException` thrown when the binary is missing.
- The app must still boot with no `sbx` (`SbxModeTest`), so nothing runs eagerly in `Adapters`, whose three `@Startup`
  producers are asserted by `FakeAdaptersTest`. Add a logged warning at startup instead of a failure.
- Tests: `SbxCliTest` (arguments), a health test with `FakeRunner`, and the missing-binary case.

## Task 7: Clear errors for move and cloud limits

- New `SbxErrors` in `infrastructure.sbx`: maps the stderr patterns recorded in Task 2 (platform mismatch, disk,
  sandbox too large, not signed in, no cloud secret, sandbox gone) to short `SandboxFailureException` messages that say
  what to do, used by `SbxCli.requireSuccess`. Unknown output keeps the current `what failed (exit N): tail` form.
- Table-driven `SbxErrorsTest` using the real strings.
- Documented in `docs/integrations.md` "Things to watch on a real host".

## Task 8: Move to local in the page

- `main.html`: replace the disabled "Running in cloud" button with a `data-move-local` button posting
  `.../move?to=local` (same `hx-target`/`hx-swap`, disabled unless idle). `ConversationsResource.move` already accepts
  `to=local`.
- `index.html`: generalize the loading toast (`[data-move-cloud],[data-move-local]`, different title); keep the toast id
  `move-cloud` that `ConversationsResource` and the tests use. Add an `error` icon variant to `toast()` (known limit in
  `ui-fragments.md`).
- Tests: update `WebTest` (it currently asserts the cloud pane has no `data-move-cloud`, and the moved-back pane has
  it). Update `docs/ui-fragments.md` (endpoint table, "Known limits") and the root `CLAUDE.md` line "offers Move to cloud
  only for now". After editing `index.html` run `./mvnw resources:copy-resources@copy-index-page`.

## Task 9: Fakes that can expire and fail

- `harness.fake.cloud-expiry-turns` (0 = off): after N cloud turns in a session, the fake agent's next cloud turn
  returns a reply with the same notice as Task 4. `harness.fake.move-failure` (`none|to-cloud|to-local`): the fake
  transfer throws `SandboxFailureException`.
- Plain constructor arguments on `FakeSandboxAgent` / `FakeSandboxTransfer`, read by the existing `Adapters` producers
  (keep exactly three `@Startup` methods; keep the old constructors so `FakeAdaptersTest` compiles).
- Document both keys in `docs/configuration.md`; add a test profile alongside `SlowFakesProfile`; tests in
  `FakeAdaptersTest` and a REST/Web test that the notice and a failed move reach the UI.

## Task 10: Docs, roadmap and contract

- `agent-os/product/roadmap.md`: update the rows with what was verified; leave source cleanup in Phase 2 known gaps.
- `docs/integrations.md`, `docs/architecture.md` (failure and notice flow), `docs/domain-model.md`, README.
- `./mvnw javadoc:javadoc` and commit `docs/apidocs/`; commit the regenerated `openapi.yaml`.

## Out of scope

OneDrive/`graph/`; the Python backend (it must match the updated `openapi.yaml` afterwards); source-sandbox cleanup
after a move; persistence across restarts (the sandbox registry still lives in memory and is rebuilt by listing names);
streaming partial agent text.

## Verification

1. `cd moby-bank-quarkus && ./mvnw test` (≈320 tests plus the new ones). `DocumentationTest` and the `*LayeringTest`
   classes must pass; they catch config and doc drift.
2. Fake mode, in a browser: start with `-Dharness.fake.cloud-expiry-turns=1`, move to cloud, send two messages and
   confirm the notice shows and survives a reload; move back with the new button; start with
   `-Dharness.fake.move-failure=to-cloud` and confirm the error toast and that the session stays local.
3. UI live test per CLAUDE.md: `HARNESS_API=http://localhost:8080 npm run test:live` against a backend started with the
   short fake delays.
4. On the sbx host (`-Dharness.sandbox.mode=sbx`): local turn → move to cloud → turn (agent remembers the earlier
   message and files) → force expiry with a short TTL → turn shows the notice and still answers → move to local → turn.
   `/q/health` reports each preflight failure when `sbx` is missing, signed out, or lacks the cloud secret.
