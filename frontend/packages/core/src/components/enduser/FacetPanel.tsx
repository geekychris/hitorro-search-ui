import React from 'react'
import { useSearch, useIndexSchema } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'
import { FacetSkeleton } from '../shell/Skeleton'

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

  if (!schema) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => <FacetSkeleton key={i} />)}
      </div>
    )
  }

  const facetFields = schema.rendererHints.facetable ?? []
  if (facetFields.length === 0) {
    return (
      <div className="p-3 text-xs text-slate-500 dark:text-slate-400 border border-dashed border-slate-300 dark:border-slate-600 rounded">
        No facet-able fields on this index. Add {`groups[].method: "identifier"`} to fields
        in the JVS type to enable faceting.
      </div>
    )
  }
  const facetData = result?.facets ?? {}

  return (
    <aside className="space-y-4">
      {facetFields.map((field) => {
        const facet = facetData[field]
        const selected = filters[field] ?? []
        return (
          <div key={field} className="border border-slate-200 dark:border-slate-700 rounded p-3 bg-white dark:bg-slate-800">
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-sm font-semibold text-slate-800 dark:text-slate-200">{prettify(field)}</h4>
              {selected.length > 0 && (
                <button className="text-[11px] text-hitorro-primary hover:underline" onClick={() => clear(field)}>
                  clear
                </button>
              )}
            </div>
            {!facet ? (
              <div className="text-xs text-slate-400">no data yet</div>
            ) : (
              <FacetValueList facet={facet} field={field} selected={selected} toggle={toggle} />
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

/**
 * Renders the value list inside one facet panel. Adds a small filter
 * input at the top when the facet has enough values to warrant it
 * (>15). The input is local — doesn't touch the store, just filters
 * what's rendered on-screen. High-cardinality facets (100+ senders)
 * become browsable this way without server-side round trips.
 */
function FacetValueList({ facet, field, selected, toggle }: {
  facet: { values: { value: string; count: number }[] };
  field: string;
  selected: string[];
  toggle: (field: string, value: string) => void;
}) {
  const [filter, setFilter] = React.useState('')
  const showFilter = facet.values.length > 15
  const filtered = React.useMemo(() => {
    if (!filter.trim()) return facet.values.slice(0, 40)
    const lc = filter.toLowerCase()
    return facet.values.filter((v) => (v.value || '').toLowerCase().includes(lc)).slice(0, 40)
  }, [facet.values, filter])

  return (
    <>
      {showFilter && (
        <input
          type="text"
          placeholder={`Filter ${facet.values.length} values…`}
          className="w-full mb-1 px-1.5 py-0.5 text-xs border border-slate-200 dark:border-slate-600 rounded bg-slate-50 dark:bg-slate-900 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-hitorro-primary"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
      )}
      <ul className="space-y-1 max-h-56 overflow-auto">
        {filtered.map((v) => (
          <li key={v.value}>
            <label className="flex items-center gap-2 text-sm cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700 px-1 rounded">
              <input
                type="checkbox"
                checked={selected.includes(v.value)}
                onChange={() => toggle(field, v.value)}
              />
              <span className="flex-1 truncate dark:text-slate-200">{v.value || '(empty)'}</span>
              <span className="text-xs text-slate-400">{v.count}</span>
            </label>
          </li>
        ))}
        {filtered.length === 0 && (
          <li className="text-xs text-slate-400 italic px-1">no matches</li>
        )}
      </ul>
    </>
  )
}
