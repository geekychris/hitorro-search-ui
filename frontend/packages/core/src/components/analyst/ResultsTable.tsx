import React from 'react'
import { useSearch, useIndexSchema } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'
import { RowInspector } from './RowInspector'
import { ExportMenu } from './ExportMenu'

/**
 * Table view for analyst mode. Auto-picks columns from the index
 * schema's `identifier` hint (small, exact fields — good for scanning).
 * User can override via ColumnPicker (Phase 2); for v1 the default is
 * good enough. Row click → RowInspector modal with the full JSON.
 */
export function ResultsTable() {
  const index    = useSearchStore((s) => s.index)
  const size     = useSearchStore((s) => s.size)
  const page     = useSearchStore((s) => s.page)
  const setPage  = useSearchStore((s) => s.setPage)
  const { data, isLoading, error, isFetching } = useSearch()
  const { data: schema } = useIndexSchema(index)
  const [openHit, setOpenHit] = React.useState<any>(null)

  if (isLoading) return <div className="text-slate-500">Loading…</div>
  if (error)     return <div className="text-red-600">{String((error as Error).message)}</div>
  if (!data)     return null

  const cols: string[] = ['id', 'ht_type', ...(schema?.rendererHints?.identifier ?? []).slice(0, 4)]
  const uniqueCols = Array.from(new Set(cols))
  const totalPages = Math.max(1, Math.ceil(data.total / data.size))

  return (
    <div>
      <div className="flex items-center justify-between mb-2 text-sm gap-2 flex-wrap">
        <div className="text-slate-600 dark:text-slate-300">
          {data.total.toLocaleString()} rows · {data.stages.join(' → ')} · {data.tookMs} ms
          {isFetching && <span className="ml-2 text-hitorro-primary">refreshing…</span>}
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <ExportMenu />
          <div className="flex items-center gap-2">
            <button className="px-2 py-0.5 border dark:border-slate-600 rounded disabled:opacity-40" disabled={page === 0}
                    onClick={() => setPage(page - 1)}>← prev</button>
            <span className="text-xs">page {page + 1} / {totalPages}</span>
            <button className="px-2 py-0.5 border dark:border-slate-600 rounded disabled:opacity-40"
                    disabled={(page + 1) * size >= data.total}
                    onClick={() => setPage(page + 1)}>next →</button>
          </div>
        </div>
      </div>

      <div className="overflow-auto border border-slate-200 dark:border-slate-700 rounded">
        <table className="w-full text-xs">
          <thead className="bg-slate-100 dark:bg-slate-800 text-left dark:text-slate-200">
            <tr>{uniqueCols.map((c) => <th key={c} className="px-2 py-1 font-medium">{c}</th>)}</tr>
          </thead>
          <tbody>
            {data.hits.map((h, i) => (
              <tr key={i} className="border-t dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer dark:text-slate-300"
                  onClick={() => setOpenHit(h)}>
                {uniqueCols.map((c) => (
                  <td key={c} className="px-2 py-1 font-mono truncate max-w-[240px]">
                    {String(readField(h, c) ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {openHit && <RowInspector hit={openHit} onClose={() => setOpenHit(null)} />}
    </div>
  )
}

/** Pull a column value out of a hit. `id` reads hit.id; `ht_type` reads
 *  hit.htType (camelCase from the BFF); everything else falls through
 *  to hit.doc[<name>] via lodash-style dotted access. */
function readField(hit: any, col: string): any {
  if (col === 'id') return hit.id
  if (col === 'ht_type') return hit.htType
  return dotted(hit.doc, col)
}

function dotted(obj: any, path: string): any {
  return path.split('.').reduce((cur, seg) => (cur == null ? cur : cur[seg]), obj)
}
