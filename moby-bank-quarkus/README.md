# moby-bank-quarkus

Quarkus backend for the Moby Bank agent harness (implementation #1; a Python implementation follows). It serves the API the `moby-bank-prototype/` UI needs and drives an agent running in a Docker Sandbox, local or cloud. See `agent-os/specs/2026-09-30-1452-quarkus-mvp-backend/` for the spec.

## Requirements

- JDK 25
- Maven (the `./mvnw` wrapper is included)
- Optional: Quarkus CLI (`quarkus dev`)

## Run

```bash
./mvnw quarkus:dev          # http://localhost:8080 , Dev UI at /q/dev
./mvnw test                 # unit and @QuarkusTest tests
```

Useful endpoints today: `/q/health`, `/q/openapi`, `/q/swagger-ui`.

## Layout

Packages follow the `ddd-foundations` layering under `com.mobybank.harness`: `domain`, `application`, `infrastructure`, `interfaces.rest`.

## Notes

- Generated with the Quarkus CLI 3.40.1 (`--no-code`), extensions: `rest-jackson`, `rest-client-jackson`, `smallrye-openapi`, `smallrye-health`.
- There is no database. State is held in memory.
