import { useQuery } from '@tanstack/react-query'
import { useClient } from './useClient'
import { useSearchStore } from '../state/store'
import type { SearchRequest, SearchResponse } from '../types/api'

/**
 * Central search hook. Watches every field in the store that affects the
 * request, so any store update triggers a background re-fetch (TanStack
 * Query dedups + caches). Kept as one hook (not one-per-field) because
 * the request shape is atomic — sending three separate requests for a
 * store change that touches three fields would be wasteful.
 */
export function useSearch() {
  const client = useClient()
  const { index, mode, lang, q, filters, facets, sort, page, size } = useSearchStore()

  return useQuery<SearchResponse>({
    enabled: !!index,
    // key includes every field that affects the request so cache hits
    // work correctly across pagination + filter tweaks.
    queryKey: ['search', { index, mode, lang, q, filters, facets, sort, page, size }],
    queryFn: async () => {
      const req: SearchRequest = { index: index!, mode, lang, q, filters, facets, sort, page, size }
      return client.search(req)
    },
    // Keep previous results visible while the next request is in flight
    // — critical for facet clicks / pagination so the UI doesn't blank.
    placeholderData: (prev) => prev,
    staleTime: 30_000,
  })
}

/** List of queryable indexes. Cached longer since it only changes when
 *  the mesh's pipelines write a new one. */
export function useIndexes() {
  const client = useClient()
  return useQuery({
    queryKey: ['indexes'],
    queryFn: () => client.indexes(),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  })
}

/** Type + renderer-hints for a specific index. Cached indefinitely
 *  since the sidecar only changes when the pipeline reruns. */
export function useIndexSchema(index: string | null) {
  const client = useClient()
  return useQuery({
    enabled: !!index,
    queryKey: ['index-schema', index],
    queryFn: () => client.schema(index!),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })
}

/** One-shot doc hydration for the detail drawer. */
export function useDoc(index: string | null, key: string | null) {
  const client = useClient()
  return useQuery({
    enabled: !!index && !!key,
    queryKey: ['doc', index, key],
    queryFn: () => client.doc(index!, key!),
    staleTime: 60_000,
  })
}
