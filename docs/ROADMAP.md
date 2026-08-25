# Roadmap — improvement ideas

v1 ships as a functional end-to-end search UI. This file collects the gaps + follow-up ideas so contributors have somewhere to start. Ordered roughly by impact.

## High-impact

### URL sync + shareable search state
Serialise `{index, mode, q, filters, facets, page, size, lang}` into the URL hash (or search-params) so users can bookmark / share a specific search. Hydrate the zustand store from `window.location` on mount. Small change (~50 LOC), huge UX win.

### Per-type card templates for the rest of the mesh's types
Only `mail_email` / `mail_message` ship real cards today. Data types the mesh already registers as tables but doesn't yet Lucene-index: `photo_asset`, `message`, `safari_visit`, `screentime_event`. Once one of those grows a `jvs-lucene` sink, ship a matching card. Layout notes for each live in `frontend/packages/demo-app/src/overrides/README.md`.

### Facet UI improvements
- **Range sliders** for `long` / `double` / `date` fields (size_bytes, taken_ts, price). Requires a new schema-hint bucket (`range`) + a `RangeFilter` component.
- **Search-within-facet** for high-cardinality identifier fields (sender_address has 200+ values). Add a filter input at the top of any facet with >20 values.
- **Nested / hierarchical facets** — categories with `>` separators render as a tree. Common in product catalogs.
- **Applied-filters bar** above the results — chip per active filter with an X to remove one at a time.

### Sort menu in end-user mode
BFF already accepts `sort`. UI just needs a dropdown next to the results header — "relevance / newest / oldest / custom field". Custom field would need schema-driven autocomplete.

### Result-card entity chip → filter
Right now the NER chips inside a result card are visual-only. Making them clickable to add `+body.mls.segmented_ner:NE_Person` to the query is a small change that closes a nice loop (see a person mentioned → filter for that person).

### Query-builder wizard for analyst mode
The current QueryBar is raw Lucene. A visual builder (add-clause button → field picker → operator dropdown → value input) would open analyst mode to users who don't know Lucene syntax. Non-trivial but well-scoped — model after Kibana Discover's "Add filter" panel.

## Deployment / operations

### Auth
Not wired in v1. Options:
- Spring Security with a bearer-token strategy — the React client already has a `fetch` override hook for header injection.
- OIDC via `spring-boot-starter-oauth2-client` — good for enterprise deploys.
- Basic auth for dev / same-network use — one Spring config bean.

### CI / release automation
- GitHub Actions workflow: `mvn install` on push, Docker image build + push to a registry on tag.
- Semantic-release for both the npm library (`@hitorro/search-ui-core`) and the Docker image.

### Metrics / observability
Actuator's `/actuator/metrics` is enabled but nothing custom is wired. Instrument:
- Search latency histogram (BFF wall time)
- Fleet call latency histogram (WebClient outbound)
- Per-index request count / error count
Ship Prometheus scrape config in the Helm chart.

### Tests
- **Frontend unit** — Vitest + React Testing Library. Cover the store, the renderer registry, the auto-renderer (walks a mock doc), and the shell's mode switch. Zero coverage today.
- **Frontend E2E** — Playwright against a running BFF + fleet-retrieval. Loads the SPA, submits a search, asserts hits render with expected fields, clicks a facet, checks pagination.
- **Backend E2E** — WebTestClient against `/api/*` with fleet-retrieval mocked. Verifies the request-shaping matches golden JVS queries.

## Frontend polish

### Loading states
Skeletons instead of "Searching…" text. `FacetPanel`'s "loading facets…" message is a placeholder — replace with shimmering rectangles.

### Empty states
- "No results for X" with a graceful hint (drop a filter, broaden the query, try analyst mode's raw syntax)
- "No indexes yet" — link out to the mesh docs for how to seed one

### Dark mode
Tailwind supports it via `dark:` classes. Just add them; expose a toggle in the header.

### Accessibility
- Facet checkboxes need proper `aria-labelledby` / `role="listbox"` grouping
- Modal drawers need focus-trap + Escape-to-close
- Colour-only state indicators (unread / flagged chips) should carry text or icons too (already do — verify contrast passes)

### Mobile layout
End-user mode is grid-2-columns above `lg:` and 1-column below. Analyst mode's table is horizontal-scrollable but the facet tree loses visibility. Small-screen mode should collapse the sidebar into a drawer.

### Result-card thumbnails
For image types (`photo_asset`), embed a small thumbnail (via the KV store's image bytes, or a URL if the field is a URL). Requires a `<Image>` component with fallback + lazy-load.

## Backend polish

### Streaming responses for large exports
Analyst mode's export menu (JSON/NDJSON/CSV) is called out but not implemented. Fleet-retrieval has `POST /api/retrieval/execute/stream` returning NDJSON — proxy it through the BFF as `POST /api/search/stream` with the same UI DTO shape.

### Multi-index federation
BFF endpoint `POST /api/search-multiple` that hits fleet-retrieval's `search-multiple` for cross-index queries. UI: IndexPicker becomes a multi-select; results are tagged with their source index; facet-panel merges values across sources.

### Response snippets — fix double-highlighting
Local snippet fallback wraps the first match in a text window; the frontend then highlights again. If summarize starts returning `<em>`-marked HTML, both layers may fight. Standardize on plain text in the snippet + always let the frontend highlight, OR standardize on HTML and stop client-side re-highlighting.

### KV name discovery
Today the DocController falls back from `<idx>-idx` → `<idx>-kv` heuristically. A proper solution: the pipeline records `{lucene_index, kv_store}` pairing in the sidecar, and the BFF reads it. Or fleet-retrieval exposes a lookup endpoint.

### Concurrency limits
WebClient uses Netty defaults. Under load, cap outbound to fleet-retrieval so the BFF fails fast instead of piling up on a slow coordinator.

## Docs

- Screenshots / animated GIFs of the two modes in `docs/USING.md`
- Deployment recipes for common non-K8s targets (systemd, docker-swarm, nomad)
- Migration guide when versions bump

## Speculative / bigger

### First-party ingestion UI
Today the mesh's driver UI has pipeline authoring. If we want a "load some data, then search it" tool separate from the mesh, a small ingest panel on this app (upload CSV → pick fields → auto-create index) would make it standalone-viable.

### AI-assisted search
- Semantic search via an embedding column on the index — swap the coordinator's IndexRetriever for a hybrid Lucene+vector retriever.
- Query rewrite via an LLM ("emails from last Tuesday" → `+times.date_received:[…] +body.mls.clean:*`).
- Snippet summarize with an LLM instead of the length-based fallback.

### Real-time indexing feed
NATS / Kafka subject that streams `{index, doc}` events → BFF pushes to browsers via SSE → UI shows "1 new result" toast. Requires wiring on the fleet-retrieval side too.

### Multi-tenant isolation
Right now every user sees every index. In a multi-tenant deploy: index-name prefixing + a claims-scoped filter added by the BFF before shaping the query.

### Component storybook
Storybook for the library — makes contributor onboarding much faster + gives designers a way to review changes without spinning up the whole stack.

## Bugs / known-limitations

- `id.id` extraction fallback in `JvsQueryShaper.extractId` is naive — assumes core_id or scalar. Composite id types with a different inner-field name won't resolve. Reading the type's `id` field's schema would be more robust.
- The `page` stage was removed from the JVS query shaping because it double-paginates. If a downstream user actually needs `page.rows / page.page` semantics (rare — search stage handles offset/limit natively), the shaper needs to be smarter.
- SPA deep-link forward controller only covers `/search`, `/analyst`, `/index/{name}`. If we grow more routes, the list needs to be kept in sync.
- Vite dev-server proxy at `/api → :8100` is hardcoded to port 8100. If someone runs the BFF on a different port, they need to edit `packages/demo-app/vite.config.ts`.
