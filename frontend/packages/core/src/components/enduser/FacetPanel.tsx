import React from 'react'
import { useSearch, useIndexSchema } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'

/**
 * Sidebar of facet panels — auto-populated from the index schema's
 * `facetable` hint (identifier-method fields). Clicking a value pushes
 * it into the store's `filters`, triggering a new search.
 *
 * Two-pass: first render subscribes to the schema for the list of
 * facet-able fields; the useSearch subscription re-runs when the store's
 * `facets` field changes, so the returned counts stay in sync.
 */
export function FacetPanel() {
  const index      = useSearchStore((s) => s.index)
  const filters    = useSearchStore((s) => s.filters)
  const setFacets  = useSearchStore((s) => s.setFacets)
  const toggle     = useSearchStore((s) => s.toggleFilter)
  const clear      = useSearchStore((s) => s.clearFilter)
  const { data: schema } = useIndexSchema(index)
  const { data: result } = useSearch()

  // Auto-select facetable fields from schema on first schema-load.
  React.useEffect(() => {
    if (schema?.rendererHints?.facetable?.length) {
      setFacets(schema.rendererHints.facetable.slice(0, 6))
    }
  }, [schema, setFacets])

  if (!schema) return <div className="text-sm text-slate-500">loading facets…</div>

  const facetFields = schema.rendererHints.facetable ?? []
  const facetData = result?.facets ?? {}

  return (
    <aside className="space-y-4">
      {facetFields.map((field) => {
        const facet = facetData[field]
        const selected = filters[field] ?? []
        return (
          <div key={field} className="border border-slate-200 rounded p-3 bg-white">
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-sm font-semibold text-slate-800">{prettify(field)}</h4>
              {selected.length > 0 && (
                <button className="text-[11px] text-hitorro-primary hover:underline" onClick={() => clear(field)}>
                  clear
                </button>
              )}
            </div>
            {!facet ? (
              <div className="text-xs text-slate-400">no data yet</div>
            ) : (
              <ul className="space-y-1 max-h-56 overflow-auto">
                {facet.values.slice(0, 20).map((v) => (
                  <li key={v.value}>
                    <label className="flex items-center gap-2 text-sm cursor-pointer hover:bg-slate-50 px-1 rounded">
                      <input
                        type="checkbox"
                        checked={selected.includes(v.value)}
                        onChange={() => toggle(field, v.value)}
                      />
                      <span className="flex-1 truncate">{v.value || '(empty)'}</span>
                      <span className="text-xs text-slate-400">{v.count}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )
      })}
    </aside>
  )
}

function prettify(f: string): string {
  return f.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}
