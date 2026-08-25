import type {
  IndexInfo, IndexSchema, SearchRequest, SearchResponse, MultiSearchRequest,
} from '../types/api'

/**
 * The fleet-retrieval-backed search client the hooks + components use.
 * Two transports are supported:
 *
 *  - `bff` (default) — talks to hitorro-search-ui's own /api/* endpoints.
 *    Requests and responses match the DTOs in `types/api.ts` verbatim.
 *
 *  - `direct` — talks straight to hitorro-fleet-retrieval's
 *    /api/retrieval/* endpoints. Bypasses the BFF entirely. Requires
 *    CORS to be open on fleet-retrieval and adds the client-side
 *    request-shaping logic that would otherwise live on the BFF. Use
 *    when you're embedding the library into another app that talks
 *    directly to fleet-retrieval and doesn't want a proxy.
 *
 *  For v1, only the `bff` transport is fully wired. The `direct` factory
 *  throws — kept as a call-out so future work has an obvious hook.
 */
export type Transport = 'bff' | 'direct'

export interface SearchClient {
  indexes(): Promise<IndexInfo[]>
  schema(index: string): Promise<IndexSchema>
  search(req: SearchRequest): Promise<SearchResponse>
  searchMultiple(req: MultiSearchRequest): Promise<SearchResponse>
  doc(index: string, key: string): Promise<any>
}

export interface ClientOptions {
  /** Base URL of hitorro-search-ui (bff transport). Defaults to `''`
   *  so requests go same-origin — right when the SPA is served from
   *  the backend jar. Set to `http://localhost:8100` for dev. */
  baseUrl?: string
  transport?: Transport
  fetch?: typeof fetch
}

export function createSearchClient(opts: ClientOptions = {}): SearchClient {
  const transport = opts.transport ?? 'bff'
  if (transport === 'direct') {
    throw new Error('direct transport not implemented in v1 — use bff (or open a follow-up PR)')
  }
  const base = (opts.baseUrl ?? '').replace(/\/+$/, '')
  const f = opts.fetch ?? fetch

  const url = (p: string) => `${base}${p}`

  const json = async <T>(r: Response): Promise<T> => {
    if (!r.ok) {
      let body: any = {}
      try { body = await r.json() } catch { /* not JSON */ }
      const msg = body?.hint ? `${body.error} — ${body.hint}` : (body?.error ?? r.statusText)
      const err = new Error(`${r.status} ${msg}`) as any
      err.status = r.status
      err.body = body
      throw err
    }
    return r.json() as Promise<T>
  }

  return {
    async indexes() {
      return json<IndexInfo[]>(await f(url('/api/indexes')))
    },
    async schema(index) {
      return json<IndexSchema>(await f(url(`/api/indexes/${encodeURIComponent(index)}/schema`)))
    },
    async search(req) {
      return json<SearchResponse>(await f(url('/api/search'), {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(req),
      }))
    },
    async searchMultiple(req) {
      return json<SearchResponse>(await f(url('/api/search-multiple'), {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(req),
      }))
    },
    async doc(index, key) {
      return json<any>(await f(url(`/api/docs/${encodeURIComponent(index)}/${encodeURIComponent(key)}`)))
    },
  }
}
