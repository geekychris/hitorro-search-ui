import { create } from 'zustand'
import type { Mode } from '../types/api'

/**
 * Global search state shared across every component. Kept flat so any
 * change triggers a re-run of the hooks that watch it via TanStack Query.
 *
 * Deliberately minimal — nothing that belongs to a single component
 * (e.g. facet-panel expand/collapse) lives here. The store is only for
 * state that fans out to multiple components AND drives the network call.
 */
export interface SearchState {
  /** Primary index — drives useIndexSchema + used by /api/search when
   *  only one index is selected. */
  index: string | null
  /** Extra indexes to federate over. When non-empty, useSearch routes
   *  to /api/search-multiple with the union of [index, ...extraIndexes].
   *  Empty = single-index mode. */
  extraIndexes: string[]
  mode: Mode
  lang: string
  q: string
  filters: Record<string, string[]>
  facets: string[]     // fields the user asked to aggregate on
  sort: string         // 'relevance' | 'date_desc' | 'date_asc' | 'field:dir'
  page: number
  size: number

  // ---------- setters (grouped so components import one action) ----------
  setIndex: (name: string | null) => void
  toggleExtraIndex: (name: string) => void
  setExtraIndexes: (names: string[]) => void
  setMode: (m: Mode) => void
  setLang: (lang: string) => void
  setQuery: (q: string) => void
  setFacets: (facets: string[]) => void
  toggleFilter: (field: string, value: string) => void
  clearFilter: (field: string) => void
  setSort: (sort: string) => void
  setPage: (page: number) => void
  setSize: (size: number) => void
  reset: () => void
}

const initial = {
  index: null as string | null,
  extraIndexes: [] as string[],
  mode: 'end-user' as Mode,
  lang: 'en',
  q: '',
  filters: {} as Record<string, string[]>,
  facets: [] as string[],
  sort: 'relevance',
  page: 0,
  size: 20,
}

export const useSearchStore = create<SearchState>((set) => ({
  ...initial,
  setIndex:  (index) => set({ index, extraIndexes: [], page: 0, filters: {} }),
  toggleExtraIndex: (name) => set((s) => {
    if (!name || name === s.index) return s
    const has = s.extraIndexes.includes(name)
    const extraIndexes = has ? s.extraIndexes.filter((n) => n !== name) : [...s.extraIndexes, name]
    return { extraIndexes, page: 0 }
  }),
  setExtraIndexes: (names) => set({ extraIndexes: names.filter(Boolean), page: 0 }),
  setMode:   (mode)  => set({ mode, page: 0 }),
  setLang:   (lang)  => set({ lang, page: 0 }),
  setQuery:  (q)     => set({ q, page: 0 }),
  setFacets: (facets) => set({ facets }),
  toggleFilter: (field, value) => set((s) => {
    const cur = s.filters[field] ?? []
    const has = cur.includes(value)
    const next = has ? cur.filter((v) => v !== value) : [...cur, value]
    const filters = { ...s.filters }
    if (next.length === 0) delete filters[field]
    else                   filters[field] = next
    return { filters, page: 0 }
  }),
  clearFilter: (field) => set((s) => {
    const filters = { ...s.filters }
    delete filters[field]
    return { filters, page: 0 }
  }),
  setSort: (sort) => set({ sort, page: 0 }),
  setPage: (page) => set({ page }),
  setSize: (size) => set({ size, page: 0 }),
  reset:   ()     => set({ ...initial }),
}))
