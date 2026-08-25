import React from 'react'
import { useSearch, useIndexSchema } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'

/**
 * Compact facet tree for analyst mode. Same data as FacetPanel but
 * denser — no per-panel padding, values inline, click a value to
 * append `+field:"value"` to the current query. That lets the analyst
 * build a query iteratively via clicks instead of typing.
 */
export function FacetTree() {
  const index    = useSearchStore((s) => s.index)
  const q        = useSearchStore((s) => s.q)
  const setQuery = useSearchStore((s) => s.setQuery)
  const setFacets = useSearchStore((s) => s.setFacets)
  const { data: schema } = useIndexSchema(index)
  const { data } = useSearch()

  React.useEffect(() => {
    if (schema?.rendererHints?.facetable?.length) {
      setFacets(schema.rendererHints.facetable.slice(0, 8))
    }
  }, [schema, setFacets])

  if (!schema) return null
  const fields = schema.rendererHints.facetable ?? []

  const addTerm = (field: string, value: string) => {
    const term = `+${field}:${needsQuote(value) ? `"${value}"` : value}`
    setQuery((q ? q + ' ' : '') + term)
  }

  return (
    <div className="text-xs space-y-3">
      {fields.map((f) => {
        const facet = data?.facets?.[f]
        if (!facet) return null
        return (
          <div key={f}>
            <div className="font-semibold text-slate-700 mb-1">{f}</div>
            <ul>
              {facet.values.slice(0, 10).map((v) => (
                <li key={v.value}>
                  <button
                    className="w-full flex justify-between hover:bg-slate-50 px-1 rounded"
                    onClick={() => addTerm(f, v.value)}
                    title={`add +${f}:${v.value} to the query`}
                  >
                    <span className="truncate">{v.value || '(empty)'}</span>
                    <span className="text-slate-400 ml-2">{v.count}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </div>
  )
}

function needsQuote(v: string): boolean {
  return /[\s+\-!(){}\[\]^"~*?:\\/]/.test(v)
}
