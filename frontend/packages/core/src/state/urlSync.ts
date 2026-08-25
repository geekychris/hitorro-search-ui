import { useEffect } from 'react'
import { useSearchStore, type SearchState } from './store'

/**
 * Two-way sync between the zustand store and {@code window.location.hash}.
 *
 * Serialised keys: {@code i} (index), {@code m} (mode), {@code l} (lang),
 * {@code q} (query), {@code f} (filters JSON), {@code fc} (facets),
 * {@code s} (sort), {@code p} (page). Short keys keep bookmarkable URLs
 * short and readable. Falsy / default values are omitted so a fresh
 * shell has an empty hash.
 *
 * <p>Call {@code useUrlSync()} once inside any component tree wrapped
 * by AppShell. Hydrates the store from the URL on mount, then subscribes
 * to store changes and rewrites the hash. Also listens for hashchange
 * so back/forward navigation restores prior search state.</p>
 */
export function useUrlSync() {
  useEffect(() => {
    // 1. Hydrate — read hash on mount, apply to store.
    applyHashToStore()

    // 2. Subscribe — every store change updates the hash.
    const unsub = useSearchStore.subscribe((state) => {
      writeStoreToHash(state)
    })

    // 3. Listen — back/forward buttons trigger hashchange.
    const onHashChange = () => applyHashToStore()
    window.addEventListener('hashchange', onHashChange)

    return () => {
      unsub()
      window.removeEventListener('hashchange', onHashChange)
    }
  }, [])
}

// ----------------------------------------------------------------------

function applyHashToStore() {
  const params = parseHash()
  if (params.size === 0) return
  const patch: Partial<SearchState> = {}
  if (params.has('i'))  patch.index = params.get('i')!
  if (params.has('ix')) patch.extraIndexes = params.get('ix')!.split(',').filter(Boolean)
  if (params.has('m'))  patch.mode  = (params.get('m') as any) || 'end-user'
  if (params.has('l'))  patch.lang  = params.get('l')!
  if (params.has('q'))  patch.q     = params.get('q')!
  if (params.has('s'))  patch.sort  = params.get('s')!
  if (params.has('p'))  patch.page  = parseInt(params.get('p')!, 10) || 0
  if (params.has('fc')) patch.facets = params.get('fc')!.split(',').filter(Boolean)
  if (params.has('f')) {
    try { patch.filters = JSON.parse(decodeURIComponent(params.get('f')!)) }
    catch { /* malformed URL — ignore */ }
  }
  useSearchStore.setState(patch as any)
}

function writeStoreToHash(state: SearchState) {
  const params: string[] = []
  const put = (k: string, v: string) => params.push(`${k}=${encodeURIComponent(v)}`)

  if (state.index)               put('i', state.index)
  if (state.extraIndexes.length) put('ix', state.extraIndexes.join(','))
  if (state.mode !== 'end-user') put('m', state.mode)
  if (state.lang !== 'en')       put('l', state.lang)
  if (state.q)                   put('q', state.q)
  if (state.sort !== 'relevance')put('s', state.sort)
  if (state.page > 0)            put('p', String(state.page))
  if (state.facets.length)       put('fc', state.facets.join(','))
  if (Object.keys(state.filters).length) put('f', JSON.stringify(state.filters))

  const hash = params.length ? '#' + params.join('&') : ''
  // Compare against current to avoid pushing identical history entries.
  if (window.location.hash !== hash) {
    // Replace instead of push so hitting back doesn't have to unwind
    // every keystroke — undo goes back to the previous "meaningful" state.
    // Users expecting back-per-search are welcome to swap to pushState.
    history.replaceState(null, '', window.location.pathname + window.location.search + hash)
  }
}

function parseHash(): URLSearchParams {
  const h = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : ''
  return new URLSearchParams(h)
}
