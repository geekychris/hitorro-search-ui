import React from 'react'
import { useSearch, useIndexSchema } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'
import { TypeRenderer } from '../../renderer/TypeRenderer'
import { DocDrawer } from './DocDrawer'
import { AppliedFilters } from './AppliedFilters'
import { SortMenu } from './SortMenu'
import { ResultCardSkeleton } from '../shell/Skeleton'

/**
 * Vertical list of hits — each rendered by TypeRenderer (per-type
 * override → AutoRenderer fallback). Pagination via prev/next; infinite
 * scroll is a Phase-2 enhancement.
 */
export function ResultsList() {
  const { data, isLoading, error, isFetching } = useSearch()
  const index    = useSearchStore((s) => s.index)
  const lang     = useSearchStore((s) => s.lang)
  const q        = useSearchStore((s) => s.q)
  const page     = useSearchStore((s) => s.page)
  const setPage  = useSearchStore((s) => s.setPage)
  const size     = useSearchStore((s) => s.size)
  const { data: schema } = useIndexSchema(index)
  const toggleFilter = useSearchStore((s) => s.toggleFilter)
  const [openKey, setOpenKey] = React.useState<string | null>(null)

  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => <ResultCardSkeleton key={i} />)}
      </div>
    )
  }
  if (error) return (
    <div className="p-4 rounded-lg border border-red-200 bg-red-50 text-red-800 dark:bg-red-950 dark:border-red-800 dark:text-red-200">
      <div className="font-semibold mb-1">Search failed</div>
      <div className="text-sm">{String((error as Error).message)}</div>
    </div>
  )
  if (!data) return null

  // Zero-hit empty state — offer actionable hints.
  if (data.total === 0) {
    return (
      <>
        <AppliedFilters />
        <div className="p-6 text-center border border-dashed border-slate-300 dark:border-slate-600 rounded-lg text-slate-500 dark:text-slate-400">
          <div className="text-lg mb-1">No results {q ? `for "${q}"` : ''}</div>
          <div className="text-sm">
            Try dropping a filter, broadening the query,
            or switch to <b>🔬 Analyst mode</b> for raw Lucene syntax.
          </div>
        </div>
      </>
    )
  }

  const totalPages = Math.max(1, Math.ceil(data.total / data.size))

  return (
    <div>
      <AppliedFilters />
      <div className="flex items-center justify-between mb-3 text-sm text-slate-600 dark:text-slate-300 flex-wrap gap-2">
        <div>
          <b>{data.total.toLocaleString()}</b> results
          {data.stages.length > 0 && (
            <span className="ml-2 text-xs text-slate-400">
              ({data.stages.join(' → ')}, {data.tookMs} ms)
            </span>
          )}
          {isFetching && <span className="ml-2 text-hitorro-primary">refreshing…</span>}
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <SortMenu />
          <div className="flex items-center gap-2">
            <button
              className="px-2 py-0.5 border rounded disabled:opacity-40 disabled:cursor-not-allowed dark:border-slate-600"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >← prev</button>
            <span className="text-xs">page {page + 1} / {totalPages}</span>
            <button
              className="px-2 py-0.5 border rounded disabled:opacity-40 disabled:cursor-not-allowed dark:border-slate-600"
              disabled={(page + 1) * size >= data.total}
              onClick={() => setPage(page + 1)}
            >next →</button>
          </div>
        </div>
      </div>

      <div className="space-y-2">
        {data.hits.map((hit) => (
          <TypeRenderer
            key={hit.id ?? Math.random()}
            hit={hit}
            schema={schema}
            query={q}
            lang={lang}
            onOpen={() => setOpenKey(hit.id)}
            onFilter={toggleFilter}
          />
        ))}
      </div>

      {openKey && index && (
        <DocDrawer index={index} docKey={openKey} onClose={() => setOpenKey(null)} />
      )}
    </div>
  )
}
