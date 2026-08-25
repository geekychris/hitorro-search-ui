# hitorro-search-ui

Extensible search UI for the hitorro platform. Ships **three artifacts**:

- **`@hitorro/search-ui-core`** — publishable React 18 component library. Drop `<AppShell />` into any React app.
- **`demo-app`** — reference SPA that mounts the library and registers a per-type override (`MailEmailCard`) as a worked example.
- **`hitorro-search-ui` backend** — Spring Boot 3.2 BFF on `:8100` that proxies `hitorro-fleet-retrieval` and shapes a UI-friendly REST surface (`/api/search`, `/api/indexes`, `/api/docs/*`).

**Two experiences in one shell:**

- **End-user mode** — search box + facet sidebar + snippet-highlighted result cards + per-type card overrides. Looks like a product-catalog search.
- **Analyst mode** — Lucene query bar + faceted results table + JSON row inspector + facet-tree drill-in. Looks like Kibana Discover.

## Quick start — Mac local

The mesh's `mesh-up.sh` boots hitorro-search-ui automatically once you've built it.

```bash
# 1. Build (first time — pulls node + pnpm into target/, then builds React + Spring Boot fat jar)
cd ~/hitorro/hitorro-search-ui
mvn clean install

# 2. Bring up the whole stack (nats + fleet-retrieval + driver + agents + search-ui)
cd ~/hitorro/hitorro-mesh-examples/scripts
./mesh-up.sh

# 3. Open the UI
open http://localhost:8100
```

The search page defaults to the first available index. Type a query, watch faceted results roll in.

Opt out of search-ui: `MESH_SEARCH_UI=0 ./mesh-up.sh`.

## Docs

- **[docs/USING.md](docs/USING.md)** — using the UI end-to-end (both modes, all features)
- **[docs/BUILDING.md](docs/BUILDING.md)** — building from source, Docker, K8s, Orion targets
- **[docs/CONNECTING.md](docs/CONNECTING.md)** — pointing at a different fleet-retrieval, CORS, dev-mode proxy, embedding
- **[docs/EXTENDING.md](docs/EXTENDING.md)** — per-type card templates, custom shells, new BFF endpoints, styling
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — how the layers fit together, data flow, request lifecycle
- **[docs/ROADMAP.md](docs/ROADMAP.md)** — improvement ideas, follow-ups, known gaps

## Layout

```
backend/     Spring Boot BFF (Java 21, Spring Boot 3.2)
frontend/    npm workspace — core library + demo-app (Vite + React 18 + TS + Tailwind + TanStack Query)
deploy/      Dockerfile, docker-compose, Helm chart, Orion manifest
docs/        this documentation set
```

## Requirements

- **Java 21+** on `$PATH`
- **`mvn`** (or use the wrapper if you drop one in)
- **A running `hitorro-fleet-retrieval`** — `mesh-up.sh` starts it on `:8095` in shared mode against `~/.hitorro/pipelines/`
- **A pipeline that produced at least one `jvs-lucene` sink** — try `~/hitorro/hitorro-mesh-examples/scripts/mail-enrich-search.sh` to seed `mail-enriched-idx` + its paired `mail-enriched-kv`

## License

Same as the rest of the hitorro tree.
