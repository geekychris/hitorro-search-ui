import React from 'react'
import { useSearch, useIndexSchema } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'
import { TypeRenderer } from '../../renderer/TypeRenderer'
import { DocDrawer } from './DocDrawer'

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
  const [openKey, setOpenKey] = React.useState<string | null>(null)

  if (isLoading) return <div className="text-slate-500">Searching…</div>
  if (error)     return <div className="text-red-600">{String((error as Error).message)}</div>
  if (!data)     return null

  const totalPages = Math.max(1, Math.ceil(data.total / data.size))

  return (
    <div>
      <div className="flex items-center justify-between mb-3 text-sm text-slate-600">
        <div>
          <b>{data.total.toLocaleString()}</b> results
          {data.stages.length > 0 && (
            <span className="ml-2 text-xs text-slate-400">
              ({data.stages.join(' → ')}, {data.tookMs} ms)
            </span>
          )}
          {isFetching && <span className="ml-2 text-hitorro-primary">refreshing…</span>}
        </div>
        <div className="flex items-center gap-2">
          <button
            className="px-2 py-0.5 border rounded disabled:opacity-40 disabled:cursor-not-allowed"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >← prev</button>
          <span className="text-xs">page {page + 1} / {totalPages}</span>
          <button
            className="px-2 py-0.5 border rounded disabled:opacity-40 disabled:cursor-not-allowed"
            disabled={(page + 1) * size >= data.total}
            onClick={() => setPage(page + 1)}
          >next →</button>
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
          />
        ))}
      </div>

      {openKey && index && (
        <DocDrawer index={index} docKey={openKey} onClose={() => setOpenKey(null)} />
      )}
    </div>
  )
}
