// TypeScript mirror of the hitorro-search-ui BFF DTOs (SearchRequest /
// SearchResponse). Kept aligned with backend/src/main/java/.../shape/*.
// When the backend DTOs change, update these types and touch the version
// so downstream apps can bump their peer-dep pin.

export type Mode = 'end-user' | 'analyst'

export interface SearchRequest {
  index: string
  q?: string
  filters?: Record<string, string[]>
  facets?: string[]
  sort?: string
  page?: number
  size?: number
  mode?: Mode
  lang?: string
}

/** Cross-index federation request. Same shape as SearchRequest but
 *  `indexes` (plural) + an optional `merger` for cross-index rank
 *  fusion ({@code score | rrf | field:name[:desc]}). */
export interface MultiSearchRequest {
  indexes: string[]
  q?: string
  filters?: Record<string, string[]>
  facets?: string[]
  sort?: string
  page?: number
  size?: number
  mode?: Mode
  lang?: string
  merger?: string
}

export interface SearchHit {
  id: string | null
  htType: string | null
  snippet: string | null
  /** Full JVS document — walked by the auto-renderer or handed to a
   *  per-type override. Untyped because JVS shape varies by index. */
  doc: any
}

export interface FacetValue { value: string; count: number }
export interface Facet      { field: string; totalCount: number; values: FacetValue[] }

export interface SearchResponse {
  total: number
  page: number
  size: number
  tookMs: number
  stages: string[]
  hits: SearchHit[]
  facets: Record<string, Facet>
}

// ==================== index discovery + schema ==============================

export interface IndexInfo {
  name: string
  docCount: number
  lastModifiedMs: number
  hasSidecar: boolean
}

export interface RendererHints {
  facetable:  string[]
  textSearch: string[]
  date:       string[]
  mls:        string[]
  identifier: string[]
}

export interface IndexSchema {
  /** Raw JVS type JSON — same as .jvs-type.json on disk. */
  type: any
  rendererHints: RendererHints
}
