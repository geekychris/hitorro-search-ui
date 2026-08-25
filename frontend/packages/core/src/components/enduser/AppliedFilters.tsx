import React from 'react'
import { useSearchStore } from '../../state/store'

/**
 * Row of chips showing every active filter, each with an X to remove
 * one at a time. Rendered above the results so users can see + peel
 * away filters without hunting through the facet sidebar. Hidden
 * entirely when nothing is filtered.
 */
export function AppliedFilters() {
  const filters = useSearchStore((s) => s.filters)
  const toggle  = useSearchStore((s) => s.toggleFilter)
  const q       = useSearchStore((s) => s.q)
  const setQuery = useSearchStore((s) => s.setQuery)

  const filterCount = Object.values(filters).reduce((n, vs) => n + vs.length, 0)
  const hasQuery = q && q.trim() !== '' && q.trim() !== '*:*'

  if (filterCount === 0 && !hasQuery) return null

  return (
    <div className="flex items-center gap-2 flex-wrap mb-3 text-xs">
      <span className="text-slate-500">active:</span>
      {hasQuery && (
        <button
          className="px-2 py-0.5 rounded bg-hitorro-primary/10 text-hitorro-primary hover:bg-hitorro-primary/20 dark:bg-hitorro-primary/20"
          onClick={() => setQuery('')}
          title="Clear the query"
        >
          <span className="font-mono">{truncate(q, 40)}</span> <span className="opacity-60">✕</span>
        </button>
      )}
      {Object.entries(filters).flatMap(([field, values]) =>
        values.map((v) => (
          <button
            key={`${field}=${v}`}
            className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            onClick={() => toggle(field, v)}
            title={`remove ${field}:${v}`}
          >
            <span className="text-slate-500 dark:text-slate-400">{field}:</span> {truncate(v, 30)} <span className="opacity-60">✕</span>
          </button>
        ))
      )}
    </div>
  )
}

function truncate(s: string, n: number): string {
  return s.length > n ? s.slice(0, n) + '…' : s
}
