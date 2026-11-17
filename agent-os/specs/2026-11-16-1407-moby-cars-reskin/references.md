# References for moby-cars re-skin

## Similar Implementations

### Seeded conversation c1

- **Location:** `moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/DemoData.java` (`sessions(Clock)`)
- **Relevance:** the template for the seeded Q2 used-car report conversation.
- **Key patterns:** helpers `user(...)`, `assistant(...)`, `read(...)`, `compute(...)`; one `ResultTable` per assistant message.

### Fake agent reply

- **Location:** `moby-bank-quarkus/src/main/java/com/mobybank/harness/infrastructure/FakeSandboxAgent.java` (`answer`)
- **Relevance:** produces the Q3 report in a new chat. It branches only on whether files are attached.

### Login page

- **Location:** `login.html` (repo root), copied by the `copy-login-page` execution in `moby-bank-quarkus/pom.xml`.

### Prior UI spec

- **Location:** `agent-os/specs/2026-10-01-1322-htmx-ui-on-quarkus/`
