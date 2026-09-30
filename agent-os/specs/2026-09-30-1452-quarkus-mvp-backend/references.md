# References for Quarkus MVP Backend

## Similar Implementations

### Moby Bank prototype (behavioral spec)

- **Location:** `moby-bank-prototype/Bank Agent Harness.dc.html`
- **Relevance:** Every UI behavior the API must support is simulated here today.
- **Key patterns to borrow:**
  - `SEED`: conversation and message shape (`title`, `group`, `cloud`, `messages[]` with `steps`, `paras`, `table`, `files`). This seeds the in-memory repositories.
  - `FOLDER_LIB`: OneDrive folder library (`id`, `name`, `path`, `count`, `files`). This seeds the fake document catalog.
  - `Component.send`: title derived from the first message (48 chars), "thinking" label, ~1.8s canned reply with a table when files are attached. This becomes `FakeSandboxAgent`.
  - `Component.moveToCloud`: two-stage progress ("Packaging context", "Transferring files"), ~2.6s, local copy retained. This becomes `FakeSandboxTransfer`; cloud→local does not exist yet.
  - `Component.openConnect`, `openAttach`, `confirmPicker`: connect folders vs attach files from connected folders.
  - `renderVals`: history grouping (Today / Previous 7 days / Earlier) and step summary ("Read N files, ran N calculations").
- **Constraint:** the dc-runtime template language only supports property paths, literals, `!` and equality, so computed flags stay in `renderVals()`.

## Skills Used While Building

- `quarkus-app` for scaffolding (`.claude/skills/quarkus-app/`)
- `ddd-foundations`, `ddd-value-objects`, `ddd-aggregates`, `ddd-services` for layering and design rules (`.claude/skills/ddd-*/`)
- `quarkus-ddd` is available but its package layout is deliberately not followed.

## External

- Docker Sandboxes kits: https://docs.docker.com/ai/sandboxes/customize/kit-reference/
