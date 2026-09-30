---
name: python-app
description: >
  Use this skill whenever a user wants to create, scaffold, bootstrap, initialize, generate, or set up a new
  Python project — a web service, API, CLI tool, script collection, or library. Also trigger when a user asks
  how to add or remove dependencies in an existing Python project, upgrade dependencies, manage virtual
  environments, or pin/upgrade the Python version. This skill ensures all projects are generated with uv,
  a current stable Python, a src or flat layout appropriate to the project type, and ruff + pytest + mypy
  configured from the start. Trigger even for casual phrasing like "spin up a FastAPI app", "start a new
  Python service", "set up a Python project", "create a CLI in Python", or "add a package to my project".
---

# Python App Skill

This skill governs how to create and maintain Python projects: project generation, dependency management,
and toolchain upkeep. Follow the numbered Core Rules; the steps below expand them into a workflow.

---

## When to Use

- Creating a new Python project of any kind — web service, CLI tool, library, script collection
- Adding, removing, or upgrading dependencies in a new or existing uv-managed project
- Pinning or upgrading the Python interpreter version of a project

**Out of scope:** this skill scaffolds; it does not decide architecture. It works identically for a plain
REST/CRUD service and a DDD modular monolith. Package layout *inside* `src/`, layering, persistence patterns,
and test design belong to their owning skills (see Companion Skills). The only architecture-adjacent thing
this skill does is pick the *dependencies* a given use case needs (Rule 6).

---

## Core Rules

Cite as `python-app R<n>` in comments and findings.

1. **Verify the current stable Python before generating anything.** The version named in this skill is stale
   by definition. Check https://www.python.org/downloads/ or `uv python list`. Never pin package versions
   from memory either — `uv add` resolves current releases; let it.
2. **uv first; venv + pip only as a mandated fallback.** uv owns init, dependencies, lockfile, interpreter
   versions, and script running. Fall back to `python -m venv` + `pip` only when the environment explicitly
   requires it (locked-down CI, policy). Do not use Poetry, Pipenv, or conda unless the project already does.
3. **Never hand-edit the `dependencies` arrays in `pyproject.toml`.** All dependency changes go through
   `uv add` / `uv remove` so `uv.lock` stays consistent with the manifest. (Hand-editing tool config sections
   like `[tool.ruff]` is fine — the rule protects the lockfile, not the file.)
4. **Derive parameters from project context first; ask only for what is genuinely undeterminable.** A project
   spec, CLAUDE.md, or existing conventions override this skill's defaults. Never re-ask for something the
   project has already decided.
5. **Choose the layout by project type** (see the decision table in Step 1): `--app` for services,
   `--package` for installable tools, `--lib` for libraries.
6. **Choose dependencies by use case** (see the decision table in Step 3, and `references/packages.md`).
   How to *use* the chosen libraries is the territory of `fastapi-persistence`, `fastapi-rest`, and
   `fastapi-testing`, not this skill's.
7. **ruff, pytest, and mypy are part of the scaffold, not optional extras.** Every new project gets them as a
   dev-dependency group with baseline configuration before any application code is written. A Python project
   without lint, tests, and type checking configured is a directory, not a scaffold.
8. **Verify after generating.** A scaffold is not done until `uv sync` succeeds, `uv run ruff check .` passes,
   and the package imports (Step 4). Never present a generated project without a passing verification step.

---

## Step 0 — Check Current Versions

Before generating, verify the current stable Python release (Rule 1):

```bash
uv python list          # shows available/installed interpreters, latest first
```

- Current stable: e.g. `3.14.x` — **confirm, do not trust this number**
- Use the latest stable unless the project context pins an older version (libraries commonly support
  current-minus-two; services should just use current)

Package versions are never checked manually — `uv add` resolves them (Rule 1).

---

## Step 1 — Gather Project Parameters

Resolve in this order: (1) explicit user instruction, (2) project context — spec, CLAUDE.md, existing
`pyproject.toml` — (3) the defaults below (Rule 4). Ask the user only when none of the three yields an answer.

| Parameter | Default | Notes |
|---|---|---|
| project name | *(required)* | Ask if not derivable from context; kebab-case dir, snake_case package |
| Python version | latest stable | Pinned in `.python-version` by `uv init` |
| layout | by project type — see below | |
| dependencies | *(required)* | Derive from the described use case via Step 3; confirm non-obvious choices |
| author metadata | omit | Do not ask; `uv init` output is fine as-is |

### Layout decision (Rule 5)

| Project type | `uv init` flag | What you get |
|---|---|---|
| Web service / long-running app (deployed, not installed) | `--app` (default) | Flat layout, `main.py` entry point, no build backend |
| Installable application / CLI tool (`pip install`-able, console script) | `--package` | `src/` layout, build backend, `[project.scripts]` entry |
| Library (imported by other projects) | `--lib` | `src/` layout, build backend, `py.typed` marker |

---

## Step 2 — Generate the Project

### Option A: uv (preferred, Rule 2)

```bash
uv init {project-name} --app --python 3.14   # or --package / --lib per Step 1
cd {project-name}

# Runtime dependencies (Step 3 decides which)
uv add fastapi uvicorn

# Quality toolchain — mandatory, Rule 7
uv add --dev ruff pytest mypy
```

Then add baseline tool configuration to `pyproject.toml` (hand-editing tool sections is fine per Rule 3):

```toml
[tool.ruff]
line-length = 100

[tool.ruff.lint]
select = ["E", "F", "I", "UP", "B"]   # errors, pyflakes, import sort, modernize, bugbear

[tool.mypy]
strict = true

[tool.pytest.ini_options]
testpaths = ["tests"]
```

Create an empty `tests/` directory with a trivial passing test so `uv run pytest` is green from commit one.

**Installation note** (include if the user might not have uv):

```bash
# Standalone installer (recommended)
curl -LsSf https://astral.sh/uv/install.sh | sh

# Via Homebrew (macOS/Linux)
brew install uv
```

### Option B: venv + pip (mandated-fallback only, Rule 2)

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install fastapi uvicorn
pip install ruff pytest mypy
pip freeze > requirements.txt
```

Use only when the environment forbids uv. Note the trade-offs to the user: no lockfile-with-manifest
consistency, no interpreter management, manual freeze discipline.

---

## Step 3 — Dependency Selection

Choose by use case, not by habit (Rule 6). Full curated tables in `references/packages.md`, including which
packages need a running backing service.

| Use case | Packages | Notes |
|---|---|---|
| Web API | `fastapi` + `uvicorn` | The standard pairing for new services |
| Sync database access | `sqlalchemy` + `psycopg[binary]` + `alembic` | SQLAlchemy 2.0 style; sync is a legitimate, simpler default |
| Async database access | `sqlalchemy` + `asyncpg` + `alembic` | Only if the service is async end-to-end |
| HTTP client | `httpx` | Sync and async in one library |
| CLI tool | `argparse` (stdlib) | Zero dependencies; reach for Typer or Click only when it outgrows argparse — see packages.md |
| Settings/config | `pydantic-settings` | Env-var driven; read at the composition root only |

**Architecture note:** the library choices above are identical for conventional and DDD projects — Python's
standard stack doesn't change with architecture. What differs (e.g., whether SQLAlchemy models are the domain
model or persistence mirrors) is decided in `fastapi-persistence` / `ddd-persistence`, not here.

Dependency groups: runtime deps plain (`uv add`), tooling and test deps in the dev group
(`uv add --dev pytest ruff mypy testcontainers`).

---

## Step 4 — Verify, Then Hand Off

### Verification (Rule 8 — mandatory when generating agentically)

```bash
uv sync                                        # env resolves and installs
uv run ruff check .                            # lint passes
uv run python -c "import {package_name}"       # package imports
uv run pytest -q                               # trivial suite green
```

If any step fails, fix the scaffold before doing anything else. Do not declare the task done, and do not start
writing application code, on top of a broken scaffold.

### Post-generation guidance for the user

1. **Run the app**: `uv run uvicorn main:app --reload` (web) or `uv run python main.py`
2. **Run anything in the env**: `uv run <cmd>` — no manual activation needed
3. **Add dependencies later**: `uv add <pkg>` / `uv add --dev <pkg>` (Rule 3)
4. **Interactive docs** (FastAPI): `http://localhost:8000/docs` once the app is running
5. **Backing services**: packages marked **(SVC)** in `references/packages.md` need a running service —
   Docker Compose in dev, testcontainers in tests

---

## Step 5 — Maintaining Existing Projects

```bash
uv add {pkg}                  # add a dependency (never hand-edit, Rule 3)
uv remove {pkg}               # remove a dependency
uv lock --upgrade             # upgrade everything within constraints
uv lock --upgrade-package {pkg}  # upgrade one package
uv python pin 3.15            # move the project to a newer interpreter
uv sync                       # bring the env in line after any change
```

After any interpreter upgrade, re-run the full verification block from Step 4.

---

## Canonical Examples

### Web API with PostgreSQL (sync stack)

```bash
uv init thought-service --app --python 3.14 && cd thought-service
uv add fastapi uvicorn sqlalchemy "psycopg[binary]" alembic pydantic-settings
uv add --dev ruff pytest mypy testcontainers httpx
```

### Same service under DDD / strict domain purity

Identical commands — architecture changes nothing at scaffold time (see Step 3 note). Layering begins with
`ddd-foundations` after this skill hands off.

### Installable CLI tool

```bash
uv init report-tool --package --python 3.14 && cd report-tool
uv add --dev ruff pytest mypy
# stdlib argparse: no runtime deps needed; add typer only if it outgrows argparse
```

### Library

```bash
uv init validation-lib --lib --python 3.14 && cd validation-lib
uv add --dev ruff pytest mypy
```

---

## Companion Skills

| Skill | Owns |
|---|---|
| `fastapi-persistence` | SQLAlchemy model design, sessions/transactions, Alembic migrations, sync vs. async in practice |
| `fastapi-rest` | Router/endpoint design, Pydantic request/response models, error mapping |
| `fastapi-testing` | Test structure, fixtures, testcontainers usage, in-memory fakes |
| `ddd-foundations` | Layering and package structure when the project is a DDD modular monolith |
| `ddd-persistence` | Repository doctrine under strict domain purity (interface in domain, mirror models in infrastructure) |

This skill hands off the moment verification passes. Everything written inside the package afterward is
governed by the skills above.
