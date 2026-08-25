# Connecting hitorro-search-ui

Wiring the BFF at the right fleet-retrieval, exposing it to browsers, embedding the React library elsewhere.

## Backend → fleet-retrieval

The BFF picks up its upstream from `hitorro.searchui.fleet.base-url` (env: `SEARCH_UI_FLEET_URL`). Default is `http://localhost:8095` — matches where `mesh-up.sh` launches fleet-retrieval.

```bash
# Point at a remote coordinator
java -jar backend/target/hitorro-search-ui-3.0.1.jar \
    --hitorro.searchui.fleet.base-url=https://retrieval.your-cluster.example
```

The BFF assumes the upstream:

- Exposes `POST /api/retrieval/execute` for full JVS pipeline queries
- Exposes `GET  /api/retrieval/indexes` for the index list
- Exposes `GET  /api/retrieval/documents/{name}/{key}` for KV lookups
- Speaks JSON

Any hitorro-fleet-retrieval since we added the `.jvs-type.json` sidecar loader (2026-08-24) works. Older versions ignore logical field paths — the UI still works, users just have to type physical field names.

### Timeouts + connection pooling

The `WebClient` is configured with a 15-second response timeout and 5-second connect timeout (`FleetRetrievalConfig.java`). Override with:

```bash
java -jar … --hitorro.searchui.fleet.timeout-ms=30000
```

Netty pool sizing uses Reactor defaults — 8 connections per host. Bump via system properties on the JVM if you're pinning this at high traffic.

## Browser → BFF (CORS)

The BFF's `CorsConfig` reads `hitorro.searchui.cors.allowed-origins`:

| Value | Meaning |
| --- | --- |
| `*` (dev default) | Any origin can hit `/api/**` |
| Empty string (`k8s` profile default) | Same-origin only — no CORS headers emitted |
| Comma list | Explicit allow-list, e.g. `https://search.foo.com,https://internal.bar.com` |

The `k8s` profile ships with same-origin-only because the reference deploy fronts the app with an ingress that terminates TLS and routes to the Service in the same cluster — no browser ever sees the raw backend port.

For local dev where `pnpm dev` runs on `:5173` and the backend runs on `:8100`, keep the default `*`.

## Where the SPA gets its API URL

The client baseURL defaults to `''` (same-origin), which means the browser hits `/api/*` relative to whatever host served the SPA. This works for:

- Packaged jar (SPA + BFF both on `:8100`) — same-origin.
- Vite dev server (`:5173`) — the Vite proxy in `packages/demo-app/vite.config.ts` rewrites `/api/*` to `:8100`.

To point the SPA at a different backend (e.g. embedding the library in another app that has its own BFF proxy):

```tsx
import { AppShell, createSearchClient } from '@hitorro/search-ui-core'

const client = createSearchClient({ baseUrl: 'https://search-api.your-company.example' })
<AppShell client={client} />
```

## Embedding the library in your own app

The library is a standard ESM React component library. Install as a workspace dep or from a private registry:

```json
{
  "dependencies": {
    "@hitorro/search-ui-core": "3.0.1",
    "@tanstack/react-query": "^5.28.0",
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  }
}
```

Minimum host app:

```tsx
import { AppShell, registerTypeRenderer } from '@hitorro/search-ui-core'
import { MyProductCard } from './cards/MyProductCard'

registerTypeRenderer('product', MyProductCard)

export function SearchPage() {
  return <AppShell title="Product search" />
}
```

If you already have a `QueryClient` in your app, pass it in:

```tsx
<AppShell queryClient={myAppsQueryClient} />
```

### Sub-component composition

If you don't want the whole shell, compose the pieces yourself:

```tsx
import {
  ClientContext, createSearchClient,
  SearchBox, FacetPanel, ResultsList,
  useSearchStore,
} from '@hitorro/search-ui-core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const qc = new QueryClient()
const client = createSearchClient({ baseUrl: '/api' })

export function MyCustomSearch() {
  const setIndex = useSearchStore(s => s.setIndex)
  React.useEffect(() => setIndex('products'), [])

  return (
    <QueryClientProvider client={qc}>
      <ClientContext.Provider value={client}>
        <div className="grid grid-cols-[16rem_1fr] gap-4">
          <FacetPanel />
          <div>
            <SearchBox placeholder="Find a product…" />
            <ResultsList />
          </div>
        </div>
      </ClientContext.Provider>
    </QueryClientProvider>
  )
}
```

Every hook (`useSearch`, `useIndexes`, `useIndexSchema`, `useDoc`) requires the `ClientContext` provider — the AppShell sets it up for you but sub-components need you to wire it.

## Custom BFF endpoints

The React client is deliberately minimal — you can extend the BFF with your own controllers under `com.hitorro.searchui.api` and hit them from the client via:

```ts
const client = createSearchClient({
  baseUrl: '/api',
  fetch: (input, init) => fetch(input, { ...init, headers: { ...init?.headers, 'x-my-header': '…' } }),
})
```

The `fetch` override lets you inject auth headers, tracing, etc. everywhere the library talks to the BFF.

## Auth (Phase-2)

Not shipped in v1. When you add it:

- Spring Security bean in `backend/src/main/java/com/hitorro/searchui/config/` — allow `/`, `/index.html`, `/assets/**`, `/actuator/health` unauthenticated, require auth on `/api/**`.
- Frontend: inject the token in the `fetch` override above.
- CORS: switch `allowed-origins` from `*` to your app's origin and add `credentials: 'include'` in the fetch override so cookies/bearer tokens travel.

## Deploying beside an existing driver

`mesh-up.sh` handles the sibling-launch on `:8100` automatically. If you want to run just the search-ui without the mesh:

```bash
# Terminal 1 — fleet-retrieval (needs pipelines already written)
java -jar ~/hitorro/hitorro-fleet-retrieval/target/hitorro-fleet-retrieval-3.0.1.jar \
    --server.port=8095 \
    --hitorro.fleet.retrieval.mode=shared \
    --hitorro.fleet.retrieval.pipelines-home=$HOME/.hitorro/pipelines

# Terminal 2 — search-ui
java -jar ~/hitorro/hitorro-search-ui/backend/target/hitorro-search-ui-3.0.1.jar
```

## Reading which index is which

The BFF's `/api/indexes` returns everything under `~/.hitorro/pipelines/lucene/` that fleet-retrieval knows about. The `hasSidecar` flag tells you whether the index has a `.jvs-type.json` next to its segment files — required for the schema endpoint and for logical-field-path expansion.

If `hasSidecar: false`, the search still works via full physical field names but facets and the auto-renderer's schema hints won't populate.
