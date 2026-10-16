# Moby Bank sandbox names — Shaping Notes

## Scope

GitHub issue #11: put "moby-bank" in the names of the Docker Sandboxes the harness creates, so that they can be told apart from other sandboxes on a host (`sbx ls`). Today a session's sandbox is named `harness-<first 12 hex of the session id>-g<generation>`.

## Decisions

- New format is `moby-bank-<12 hex>-g<generation>`, e.g. `moby-bank-0a1b2c3d4e5f-g0`. The word `harness` is replaced, not kept. A cloud sandbox is still listed as `claude/moby-bank-0a1b2c3d4e5f-g1-k3j2x` (agent prefix and service suffix).
- Clean break: sandboxes created under the old `harness-…` names are not recognised when a session's sandbox is rediscovered after a restart. The `sbx` mode has not yet run against a real host and the registry is in memory, so there is nothing to migrate. A test records that old names are ignored.
- The prefix stays hardcoded (no `harness.sbx.name-prefix` setting). Teams reusing the harness change one method, `SandboxRegistry.baseName`.
- The preflight probe sandbox added by the cloud-move hardening (PR #13) is renamed too, `harness-probe-<8 hex>` → `moby-bank-probe-<8 hex>`, since it is also a sandbox the app creates.
- The `harness-stage-` temporary directory used while staging files is a local folder, not a sandbox name, and is unchanged.

## Context

- **Visuals:** None.
- **References:** `SandboxRegistry` (builds and parses the names) and the sbx adapter tests; see `references.md`.
- **Product alignment:** Cosmetic. Consistent with `agent-os/product/mission.md` (a reference harness other teams reuse: the name is built in one place).

## Standards Applied

None (`agent-os/standards/index.yml` is empty).

## Open Items

None.
