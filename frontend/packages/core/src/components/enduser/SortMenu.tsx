import React from 'react'
import { useSearchStore } from '../../state/store'
import { useIndexSchema } from '../../hooks/queries'

/**
 * Sort dropdown for end-user mode. Ships with three canonical options
 * (relevance, newest, oldest) plus one entry per {@code date}-hint
 * field the index schema advertises — so a mail index gets "sort by
 * received date", a photo index gets "sort by taken date", etc.
 *
 * The store's {@code sort} field is passed through to the BFF verbatim.
 * Recognised keywords: {@code relevance}, {@code date_desc},
 * {@code date_asc}. Anything else is treated as {@code field:direction}.
 */
export function SortMenu() {
  const index = useSearchStore((s) => s.index)
  const sort  = useSearchStore((s) => s.sort)
  const setSort = useSearchStore((s) => s.setSort)
  const { data: schema } = useIndexSchema(index)

  const dateFields = schema?.rendererHints?.date ?? []

  return (
    <div className="flex items-center gap-1 text-xs">
      <label className="text-slate-500">sort:</label>
      <select
        className="border border-slate-300 rounded px-2 py-0.5 text-xs bg-white dark:bg-slate-800 dark:border-slate-600"
        value={sort}
        onChange={(e) => setSort(e.target.value)}
      >
        <option value="relevance">relevance</option>
        <option value="date_desc">newest first</option>
        <option value="date_asc">oldest first</option>
        {dateFields.map((f) => (
          <React.Fragment key={f}>
            <option value={`${f}:desc`}>{f} ↓</option>
            <option value={`${f}:asc`}>{f} ↑</option>
          </React.Fragment>
        ))}
      </select>
    </div>
  )
}
