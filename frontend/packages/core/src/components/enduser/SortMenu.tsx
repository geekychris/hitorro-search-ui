import React from 'react'
import { useSearchStore } from '../../state/store'
import { useIndexSchema } from '../../hooks/queries'

/**
 * Sort dropdown for end-user mode.
 *
 * <p>Shows relevance + one entry per sortable field the schema
 * advertises. "Sortable" means the field has NUMERIC / SORTED_NUMERIC
 * DocValues in Lucene — populated by the BFF from FieldInfos, so it
 * reflects reality regardless of what the type sidecar declared. Date
 * fields (a subset of sortable) additionally power the
 * "newest first" / "oldest first" shortcut entries when at least one
 * date field is present.</p>
 *
 * <p>Every non-relevance value serialises to the {@code field:direction}
 * shape the BFF's {@code JvsQueryShaper} parses. The coordinator then
 * sees a canonical sort chain regardless of which dropdown entry the
 * user picked.</p>
 */
export function SortMenu() {
  const index = useSearchStore((s) => s.index)
  const sort  = useSearchStore((s) => s.sort)
  const setSort = useSearchStore((s) => s.setSort)
  const { data: schema } = useIndexSchema(index)

  const hints = schema?.rendererHints
  const dateFields = hints?.date ?? []
  // Prefer the explicit sortable bucket; fall back to date fields for
  // older BFFs that don't populate it (single-source pre-augmentation).
  const sortableFields = hints?.sortable && hints.sortable.length > 0
    ? hints.sortable
    : dateFields
  const primaryDate = dateFields[0]
  const newestValue = primaryDate ? `${primaryDate}:desc` : null
  const oldestValue = primaryDate ? `${primaryDate}:asc`  : null

  return (
    <div className="flex items-center gap-1 text-xs">
      <label className="text-slate-500">sort:</label>
      <select
        className="border border-slate-300 rounded px-2 py-0.5 text-xs bg-white dark:bg-slate-800 dark:border-slate-600"
        value={sort}
        onChange={(e) => setSort(e.target.value)}
      >
        <option value="relevance">relevance</option>
        {newestValue && <option value={newestValue}>newest first</option>}
        {oldestValue && <option value={oldestValue}>oldest first</option>}
        {sortableFields.map((f) => (
          <React.Fragment key={f}>
            <option value={`${f}:desc`}>{f} ↓</option>
            <option value={`${f}:asc`}>{f} ↑</option>
          </React.Fragment>
        ))}
      </select>
    </div>
  )
}
