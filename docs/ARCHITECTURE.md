# Architecture

## The stack, from browser to disk

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Browser                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  @hitorro/search-ui-core  (React 18 + Vite + TS + Tailwind)         │ │
│  │  ┌───────────────┐  ┌─────────────┐  ┌─────────────────────────┐    │ │
│  │  │  <AppShell/>  │→ │  hooks      │→ │  createSearchClient()   │    │ │
│  │  │  ├ IndexPicker│  │  useSearch  │  │  fetch → /api/search    │    │ │
│  │  │  ├ ModeSwitch │  │  useIndexes │  │  fetch → /api/indexes   │    │ │
│  │  │  ├ FacetPanel │  │  useDoc     │  │  fetch → /api/docs/*    │    │ │
│  │  │  ├ ResultsList│  │  useSchema  │  └─────────────────────────┘    │ │
│  │  │  ├ QueryBar   │  └─────────────┘                                 │ │
│  │  │  └ …          │                                                  │ │
│  │  └───────────────┘  zustand store (index, mode, q, filters, page)   │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
                                    │  HTTP (JSON)
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  hitorro-search-ui backend  (Spring Boot 3.2, Java 21, :8100)            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │ /api/search     │→ │  JvsQueryShaper │→ │  FleetRetrievalClient   │  │
│  │ /api/indexes    │  │  (UI DTO ↔ JVS) │  │  (WebClient, Netty pool)│  │
│  │ /api/indexes/…/ │  └─────────────────┘  └─────────────────────────┘  │
│  │ /api/docs/…     │                                                    │
│  │ /            (SPA static assets from classpath:/static/)             │
│  └─────────────────┘                                                     │
└──────────────────────────────────────────────────────────────────────────┘
                                    │  HTTP (JSON, JVS query)
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  hitorro-fleet-retrieval  (Spring Boot 3, Java 21, :8095)                │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  RetrievalController                                                │ │
│  │    POST /api/retrieval/execute                                      │ │
│  │      RetrievalPipelineBuilder                                       │ │
│  │        ├ IndexRetriever   (JVSLuceneSearcher via JVSQueryParser)    │ │
│  │        ├ DocumentRetriever(KV hydration if fetch stage requested)   │ │
│  │        ├ FixupRetriever   (type-system projections)                 │ │
│  │        ├ FacetRetriever   (agg per requested facet field)           │ │
│  │        └ SummarizeRetriever (query-focused snippets)                │ │
│  │    GET  /api/retrieval/indexes                                      │ │
│  │    GET  /api/retrieval/documents/{name}/{key}                       │ │
│  │  ReadOnlyIndexService — loads .jvs-type.json sidecar per index      │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
                                    │  Filesystem read
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  ~/.hitorro/pipelines/                                                    │
│    lucene/                                                                │
│      mail-enriched-idx/                                                   │
│        ├ _0.cfs, segments_2, …    (Lucene segment files)                  │
│        └ .jvs-type.json           (type sidecar written by jvs-lucene sink)│
│    kv/                                                                    │
│      mail-enriched-kv/                                                    │
│        └ CURRENT, IDENTITY, *.log (RocksDB store written by kvstore sink) │
└──────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │  Written by mesh pipelines
                                    │
                        hitorro-mesh-driver + jvs-lucene / kvstore sinks
```

## Layer responsibilities

### `frontend/packages/core/` — the library

**Client** (`src/client/`): typed fetch wrappers for `/api/*`. Injectable via `<ClientContext>` so hooks can pull it out at runtime.

**Hooks** (`src/hooks/`): TanStack Query wrappers around client calls. `useSearch` watches every store field that affects the request; TanStack Query dedups + caches by query key. `useIndexes` / `useIndexSchema` cache aggressively (indexes change slowly; schemas change only on pipeline rerun).

**State** (`src/state/`): single zustand store holds `index / mode / lang / q / filters / facets / sort / page / size`. Every field has a dedicated setter so components subscribe granularly. Store mutations trigger hook refetches automatically because TanStack Query's queryKey includes every store field.

**Renderer** (`src/renderer/`): the registry pattern. `<TypeRenderer />` looks up `hit.htType` in the registry; hits a registered per-type component or falls back to `<AutoRenderer />`. `registerTypeRenderer(htType, cmp)` is process-local and called at app boot before mounting the shell.

**Components** (`src/components/`): three folders — `shell/` (IndexPicker, ModeSwitch, LangSelect, AppShell), `enduser/` (SearchBox, FacetPanel, ResultsList, DocDrawer), `analyst/` (QueryBar, ResultsTable, FacetTree, RowInspector). Everything is Tailwind-only for zero-CSS-file publishing.

### `backend/` — the BFF

**`api/`** — five REST endpoints. `SearchController` is the fat one; others are thin wrappers.

**`client/FleetRetrievalClient`** — one WebClient bean shared across all controllers. Blocking (`.block()` on the reactive chain) because Spring MVC's servlet stack. Errors are wrapped in `FleetException` that carries the upstream status + body so `SearchExceptionHandler` can surface actionable JSON to the browser.

**`shape/`** — the translation layer. `SearchRequest` is a small UI-friendly DTO (`{index, q, filters, facets, page, size, mode, lang}`). `JvsQueryShaper` converts it to the JVS query fleet-retrieval expects, then unwraps the response into the flat `SearchResponse` (`{total, hits, facets, snippets}`). Unit-tested in `JvsQueryShaperTest` — 11 cases covering query composition, execute-body assembly, response translation.

**`config/`** — CORS, static-asset cache policy, WebClient factory. `application.yml` has three profiles: default (dev on Mac), `docker` (compose network DNS), `k8s` (same-origin only).

**`error/`** — `@RestControllerAdvice` that maps typed exceptions to JSON. Fleet upstream failures preserve the upstream status when meaningful (4xx pass through; unknown → 502).

### `deploy/` — packaging targets

Docker: multi-stage build (node → maven → temurin-21-jre slim). Runs as uid 1000, HEALTHCHECK on `/actuator/health`.

Helm: standard chart shape — Chart.yaml + values.yaml + deployment/service/ingress templates. Labels follow the `app.kubernetes.io/name`, `part-of=hitorro` convention used elsewhere in the repo.

Orion: manifest declares the `hitorro-search-ui` capability so the driver's Fleet panel discovers it. Positioned as a fleet-* family sibling.

## The critical request path

A user typing "chase" into the end-user search box:

```
1. SearchBox onChange → setQuery("chase") in the zustand store
2. Debounce fires 300ms later, updating the store
3. useSearch's queryKey changes → TanStack Query re-runs queryFn
4. queryFn calls client.search({index, q:"chase", filters:{}, facets:[…], mode:"end-user", lang:"en", page:0, size:20})
5. Browser POSTs http://localhost:8100/api/search with UI DTO
6. SearchController.search() delegates to JvsQueryShaper.toExecuteRequest()
7. Shaper produces {indexName, query:{search:{query:"(body.mls.clean:chase OR title.mls.clean:chase)", offset:0, limit:20, lang:"en", facets:["sender_domain","read","flagged"]}, fetch:{}, fixup:{tags:["basic"]}, summarize:{maxDocs:20,maxWords:60}}}
8. FleetRetrievalClient.execute(body) → WebClient.post("/api/retrieval/execute") → fleet-retrieval :8095
9. Fleet-retrieval's RetrievalPipelineBuilder runs the stages in order:
     · IndexRetriever calls JVSLuceneSearcher.search(query) — JVSQueryParser rewrites logical paths via the type sidecar
     · FixupRetriever applies "basic" tag projections (adds .clean, .segmented, .segmented_ner if missing)
     · FacetRetriever counts per requested field
     · SummarizeRetriever produces per-doc snippets
10. Fleet returns {totalHits, documents:[…], aggregates:[summary, facets, summarize]}
11. JvsQueryShaper.fromExecuteResponse() unwraps: pulls id via extractId (id.id for core_id), pairs snippets to hits, flattens aggregates→facets map, computes localSnippet fallback when summarize is empty
12. SearchController returns SearchResponse to the browser
13. TanStack Query stores the response, feeds it to useSearch()
14. ResultsList re-renders — each hit goes through <TypeRenderer/> which picks MailEmailCard for ht_type=mail_email, AutoRenderer for anything else
15. FacetPanel re-renders — checkbox state joined against filters, counts from the new facets map
```

## The type sidecar contract

Every `jvs-lucene` sink writes `.jvs-type.json` next to its Lucene segment files. The BFF's `IndexController.schema()` reads it directly from disk to compute renderer hints; fleet-retrieval's `ReadOnlyIndexService.openOrGet()` reads it and hands the loaded `Type` to `JVSLuceneSearcher` so `JVSQueryParser` can rewrite logical field paths.

If the sidecar's missing, everything degrades gracefully:

- Schema endpoint returns 404 → React shell disables facets (still renders results generically)
- Fleet searcher uses `null` Type → JVSQueryParser falls back to physical field names
- Auto-renderer walks the doc without hints — still shows title/body + any scalar fields

## What flows through where

| Data | Written by | Read by |
| --- | --- | --- |
| Lucene segments | pipeline `jvs-lucene` sink | fleet-retrieval `IndexRetriever` |
| KV values | pipeline `kvstore` sink | fleet-retrieval `DocumentRetriever` (fetch stage) + `GET /api/retrieval/documents/*` |
| `.jvs-type.json` sidecar | pipeline `jvs-lucene` sink (this session's addition) | fleet-retrieval's ReadOnlyIndexService + search-ui BFF's IndexController |
| Renderer hints | search-ui BFF (derived from sidecar on read) | React library's FacetPanel + AutoRenderer + ResultsTable column picker |
| Search request DTO | React library | BFF SearchController |
| JVS query body | BFF JvsQueryShaper | fleet-retrieval RetrievalController |
| SearchResponse DTO | BFF JvsQueryShaper | React library hooks |

## Concurrency / caching

**Backend**: WebClient uses Netty's shared connection pool. No app-level cache — every request hits fleet-retrieval fresh. Trade-off: fleet-retrieval has index-level warm caches; caching in the BFF would need cache-invalidation which is a rabbit hole. Users doing repeated identical searches within 30s benefit from TanStack Query's client-side cache anyway.

**Frontend**: TanStack Query is the cache. `staleTime` per query:
- `useSearch` — 30s (fast enough to re-request on the next click)
- `useIndexes` — 60s
- `useIndexSchema` — Infinity (only changes on pipeline rerun)
- `useDoc` — 60s

`placeholderData: (prev) => prev` on useSearch keeps the previous results visible while the next request is in flight — critical for facet-click UX.

## What we deliberately don't do

- **Server-side rendering** — v1 is SPA-only. React Router isn't wired; deep-link paths (`/search`, `/analyst`) are handled by a Spring forward controller so refresh works, but SSR/RSC is out of scope.
- **Cross-index federation at the BFF** — v1 searches one index at a time. Fleet-retrieval has a `/api/retrieval/search-multiple` endpoint we don't yet consume.
- **State persistence** — filters/query/page live in memory. URL sync + localStorage restore are follow-ups.
- **Real-time results** — no WebSocket / SSE. Every user action re-fires the query. Fine for `<200ms` fleet responses.
- **Auth** — no Spring Security; `/api/**` is open. Add via profile when needed.
