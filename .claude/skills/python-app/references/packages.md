# Python Package Reference

Quick lookup for commonly requested packages. Add runtime packages with `uv add <name>`, tooling and test
packages with `uv add --dev <name>`. Never pin versions from memory — `uv add` resolves current releases
(`python-app` R1).

Names verified as of mid-2026. When in doubt, the PyPI page is the source of truth, not this table.

**(SVC)** = needs a running backing service: Docker Compose in dev, testcontainers in tests.

---

## Web / API

| Package | Description |
|---|---|
| `fastapi` | The standard framework for new APIs; pair with `uvicorn` |
| `uvicorn` | ASGI server for dev and production |
| `flask` | Established micro-framework; use when the project already does, not for new services |
| `python-multipart` | Required by FastAPI for form/file uploads |
| `jinja2` | Templating (server-rendered HTML) |
| `websockets` | WebSocket support (FastAPI uses it under the hood; direct use for custom servers) |

---

## Data / Persistence

| Package | Description |
|---|---|
| `sqlalchemy` | The ORM/toolkit standard; 2.0 style (`Mapped[]`, `mapped_column`) only |
| `psycopg[binary]` | PostgreSQL driver, sync (psycopg 3 — not psycopg2) **(SVC)** |
| `asyncpg` | PostgreSQL driver, async — only for end-to-end async services **(SVC)** |
| `alembic` | Schema migrations for SQLAlchemy |
| `pymysql` | MySQL driver **(SVC)** |
| `redis` | Redis client, sync and async **(SVC)** |
| `pymongo` | MongoDB client **(SVC)** |
| `sqlite3` | stdlib — no install; fine for tools, not for services |

---

## Messaging / Events

| Package | Description |
|---|---|
| `confluent-kafka` | Kafka client (librdkafka-based; the production standard) **(SVC)** |
| `aiokafka` | Async-native Kafka client — for async services **(SVC)** |
| `pika` | RabbitMQ (AMQP 0-9-1) client **(SVC)** |
| `celery` | Distributed task queue (needs a broker: Redis or RabbitMQ) **(SVC)** |

---

## HTTP Clients

| Package | Description |
|---|---|
| `httpx` | The modern default — sync and async in one API |
| `requests` | The long-standing sync standard; fine, but httpx supersedes it for new code |

---

## Security / Auth

| Package | Description |
|---|---|
| `pyjwt` | JWT encode/verify |
| `authlib` | OAuth2 / OIDC client and server flows |
| `passlib[argon2]` | Password hashing |
| `itsdangerous` | Signed tokens/sessions (used by Flask; handy standalone) |

---

## Config / Settings

| Package | Description |
|---|---|
| `pydantic-settings` | Env-var-driven settings via Pydantic models; read at the composition root only |
| `python-dotenv` | `.env` loading (pydantic-settings can use it directly) |

---

## Observability

| Package | Description |
|---|---|
| `structlog` | Structured logging |
| `prometheus-client` | Prometheus metrics endpoint |
| `opentelemetry-distro` | OpenTelemetry tracing (auto-instrumentation via `opentelemetry-instrument`) |

---

## CLI

| Package | Description |
|---|---|
| `argparse` | stdlib — no install; the default for simple CLIs |
| `typer` | Type-hints-based CLI framework (built on Click); common choice for new, larger CLIs |
| `click` | The established decorator-based standard; most major Python CLIs are built on it |
| `rich` | Terminal formatting, tables, progress bars (pairs with any of the above) |

Guidance: start with stdlib `argparse`. Reach for Typer or Click when subcommands, completion, or complex
option handling outgrow it — either is a normal choice; don't mix them in one codebase.

---

## Testing & Quality (dev group — `uv add --dev`)

| Package | Description |
|---|---|
| `pytest` | The test framework; part of every scaffold (`python-app` R7) |
| `ruff` | Linter + import sorter + formatter; part of every scaffold (R7) |
| `mypy` | Static type checker; part of every scaffold (R7) |
| `pytest-cov` | Coverage reporting |
| `testcontainers` | Real backing services in tests via Docker — the answer to every **(SVC)** marker |
| `httpx` | Also the test client for FastAPI apps (`ASGITransport` / `TestClient` is built on it) |
| `freezegun` | Clock control in tests (prefer injected clocks where the architecture provides them) |

How to *structure* tests is `fastapi-testing`'s territory — this table only covers what to install.

---

## Finding More Packages

```bash
# Search PyPI in the browser — there is no good CLI search
open https://pypi.org

# Inspect what's installed and why
uv tree

# Check a package's current version before discussing it
uv add --dry-run {pkg}
```
