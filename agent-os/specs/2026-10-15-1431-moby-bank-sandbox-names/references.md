# References for Moby Bank sandbox names

## Similar Implementations

### `SandboxRegistry`

- **Location:** `moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/sbx/SandboxRegistry.java`
- **Relevance:** the only place the sandbox name is built (`baseName`, `nameFor`) and parsed (`generationOf`). `generationOf` derives its prefix from `baseName`, so changing the prefix in one method changes both creation and rediscovery.
- **Key patterns:** the registry is in memory; the adapters rediscover a sandbox by listing names and taking the highest generation that parses.

### Rediscovery in the adapters

- **Location:** `moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/sbx/SbxSandboxAgent.java`, `SbxSandboxTransfer.java`
- **Relevance:** they call `SandboxRegistry.nameFor` for new sandboxes and `generationOf` on `sbx ls -q` output. Their Javadoc spells out the name format.

### Tests that spell the name out

- **Location:** `moby-bank-quarkus/src/test/java/com/mobybank/harness/infrastructure/sbx/` — `SandboxRegistryTest`, `SbxCliTest`, `SbxSandboxTransferTest`, `SbxSandboxAgentTest`
- **Relevance:** they assert the exact `sbx` command lines through `FakeRunner`, so the literals change with the prefix.

### Documentation that spells the name out

- `moby-bank-quarkus/README.md` (sbx section), `moby-bank-quarkus/docs/integrations.md` ("Sandbox names and generations"), the repo root `CLAUDE.md` ("Sandbox names" bullet), and the generated Javadoc in `moby-bank-quarkus/docs/apidocs/` (regenerate with `./mvnw javadoc:javadoc`).
