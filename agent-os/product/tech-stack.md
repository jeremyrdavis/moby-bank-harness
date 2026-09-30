# Tech Stack

## Frontend

- React 18.3.1 (vendored UMD), rendered through a Claude Design `.dc.html` component and its dc-runtime (`support.js`)
- Docker Trident design system (`window.Trident.*`, Tailwind v4 + semantic tokens)
- Zero-dependency Node 18+ static server (`serve.mjs`)
- Lives in `moby-bank-prototype/`

## Backend

Two implementations, built sequentially:

1. Java / Quarkus
2. Python

Both run or drive agents in Docker Sandboxes (local and cloud). Framework and structure within each implementation are to be defined, guided by the skills listed below.

## Database

To be defined

## Other

- Docker Sandboxes: local and cloud agent runtimes
- Microsoft OneDrive integration for document sources
- Build skills and references:
  - https://github.com/jeremyrdavis/agentic-skills-for-python
  - https://github.com/jeremyrdavis/agentic-skills-for-quarkus
  - https://github.com/jeremyrdavis/ddd-foundations
  - https://github.com/jeremyrdavis/ddd-value-objects
  - https://github.com/jeremyrdavis/ddd-aggregates
  - https://github.com/jeremyrdavis/ddd-services
