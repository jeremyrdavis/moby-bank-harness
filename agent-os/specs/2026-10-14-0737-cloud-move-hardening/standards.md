# Standards for Cloud move hardening

`agent-os/standards/index.yml` has no entries, so there are no standards files to inject. These rules govern the work instead.

## DDD skills (`.claude/skills/`)

- `ddd-foundations`: layering `interfaces.rest` and `interfaces.web` -> `application` -> `domain` <- `infrastructure`. Domain stays free of Jakarta and Quarkus.
- `ddd-aggregates`: `Session` keeps a private constructor, a factory and `rehydrate()`. The new `notice` on `Message` goes through those, with no setters.
- `ddd-value-objects`: records, immutable, validated in the constructor.
- `ddd-services`: application services orchestrate and translate; infrastructure adapters do the `sbx` work.

## Tests that enforce the rules

- `*LayeringTest` fail the build on a wrong import. `WebLayeringTest`: `interfaces.web` may not name domain types, so a domain exception or `AgentReply` must be translated in `application` first.
- `DocumentationTest` fails when docs drift: every `harness.*` key read with `@ConfigProperty(name = ...)` must be in `docs/configuration.md`, backticked class names must exist, and `docs/api.md` must match `openapi.yaml`.
- `OpenApiContractTest` guards the endpoint list; `openapi.yaml` is generated at build time and committed.
- `FakeAdaptersTest` asserts exactly three `@Startup` producers in `Adapters`.
- `SbxSandboxAgentTest`, `SbxSandboxTransferTest` and `SbxCliTest` assert `sbx` argument lists through `FakeRunner`.
