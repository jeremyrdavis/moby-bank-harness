# Issue #11: Add "moby-bank" to the sandbox names

## Context

Issue #11 ("Add 'moby-bank' to the sandbox names") asks that the Docker Sandboxes the harness creates carry the
app's name, so that someone running `sbx ls` on a host with other sandboxes can tell which ones belong to this
demo. Today a session's sandbox is named `harness-<first 12 hex of the session id>-g<generation>`.

Decisions made while shaping (with the user):

- **New format:** `moby-bank-<12 hex>-g<generation>`, e.g. `moby-bank-0a1b2c3d4e5f-g0`. The `harness` word is
  replaced, not kept. Cloud listings still look like `claude/moby-bank-0a1b2c3d4e5f-g1-k3j2x`.
- **Clean break:** old `harness-…` sandboxes are not recognised on rediscovery. The sbx mode has never run against a
  real host and the registry is in memory, so there is nothing to migrate.
- **Hardcoded**, not a config setting.
- No visuals; no standards apply (`agent-os/standards/index.yml` is empty).

The prefix is built in exactly one place, `SandboxRegistry.baseName`
(`moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/sbx/SandboxRegistry.java:47`), and
`generationOf` in the same class derives its matching prefix from it, so rediscovery follows automatically.
Everything else is literal strings in tests, Javadoc and docs.

## Task 1: Save spec documentation

Create `agent-os/specs/2026-10-15-1431-moby-bank-sandbox-names/` (follow the format of
`agent-os/specs/2026-10-01-1322-htmx-ui-on-quarkus/`):

- `plan.md`: this plan.
- `shape.md`: scope (issue #11), the decisions above, context (Visuals: none; References: `SandboxRegistry` and the
  sbx tests; Product alignment: cosmetic, consistent with the mission; teams reusing the harness rename the prefix in
  one method), Standards applied: none.
- `standards.md`: "No standards apply: `agent-os/standards/index.yml` is empty."
- `references.md`: `SandboxRegistry` (name building and parsing), `SbxSandboxAgent` / `SbxSandboxTransfer`
  (rediscovery by listing names), and the docs that spell the name out (listed in Task 3).
- `visuals/`: empty folder with a `.gitkeep` (the earlier specs have one).

## Task 2: Change the prefix in code and tests

Module: `moby-bank-quarkus`.

- `src/main/java/com/mobybank/harness/infrastructure/sbx/SandboxRegistry.java`: in `baseName` return
  `"moby-bank-" + …`; update the class Javadoc (`harness-<session>-g<generation>` → `moby-bank-<session>-g<generation>`).
- `src/main/java/com/mobybank/harness/infrastructure/sbx/SbxSandboxTransfer.java:19`: same Javadoc change.
- Leave `Files.createTempDirectory("harness-stage-")` in `SbxSandboxAgent` alone: it is a local temp folder, not a
  sandbox name.
- Replace `harness-` with `moby-bank-` in the sandbox-name literals of these tests (the `sbx` command strings in
  the assertions change with them):
  - `src/test/java/com/mobybank/harness/infrastructure/sbx/SandboxRegistryTest.java`
  - `src/test/java/com/mobybank/harness/infrastructure/sbx/SbxCliTest.java`
  - `src/test/java/com/mobybank/harness/infrastructure/sbx/SbxSandboxTransferTest.java`
  - `src/test/java/com/mobybank/harness/infrastructure/sbx/SbxSandboxAgentTest.java`
  - any other test found by `grep -rn "harness-" src/test` (the four above are the known ones).
- Add one assertion to `SandboxRegistryTest` that an old-style `harness-0a1b2c3d4e5f-g1` name is **not** matched by
  `generationOf`, recording the clean-break decision.

## Task 3: Update documentation

- `moby-bank-quarkus/README.md:40`: `harness-<session>-g<generation>` → `moby-bank-<session>-g<generation>`.
- `moby-bank-quarkus/docs/integrations.md:75-79`: the name block and the `claude/harness-…` example.
- `CLAUDE.md` (repo root, "Sandbox names" bullet, line 61).
- Regenerate the committed Javadoc: `cd moby-bank-quarkus && bash -c './mvnw javadoc:javadoc'` (updates
  `docs/apidocs/.../SandboxRegistry.html` and `SbxSandboxTransfer.html`). Commit the regenerated files.
- `agent-os/product/roadmap.md` needs no change (it does not mention the name).

## Task 4: Verify

```bash
cd moby-bank-quarkus
grep -rn "harness-" src docs/*.md README.md ../CLAUDE.md   # expect only "harness-stage-" in SbxSandboxAgent
bash -c './mvnw test'                                      # ~320 tests, including DocumentationTest
```

`DocumentationTest` confirms the docs still match the code and that `docs/apidocs/index.html` exists. The real
`sbx` CLI is not installed in this sandbox, so the name change cannot be tried against a real host; the README's
"verify on a real host" list already covers the `sbx` commands and remains accurate.

Then commit on a branch (e.g. `feature/moby-bank-sandbox-names`) and open a PR that closes #11.

## What was built

Implemented as planned, rebuilt on top of PR #13 (cloud-move hardening) which landed on `main` meanwhile. Beyond the
plan: the preflight probe sandbox from PR #13 is named `moby-bank-probe-<8 hex>` (was `harness-probe-…`), and the
new `SbxErrorsTest` and `SbxPreflightTest` use the new prefix in their literals. The `verification.md` of the
cloud-move-hardening spec keeps the `harness-…` names it recorded from a real host, as history.
