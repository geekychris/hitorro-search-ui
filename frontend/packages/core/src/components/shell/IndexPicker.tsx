import React from 'react'
import { useIndexes } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'

/**
 * Index selector. The primary select drives the schema (facets +
 * renderer hints); a "+ add" dropdown adds extra indexes to
 * federate over. When any extras are active, searches route through
 * {@code POST /api/search-multiple} using fleet-retrieval's merger.
 *
 * Auto-picks the first available index on mount so a fresh shell has
 * something loaded.
 */
export function IndexPicker() {
  const { data: indexes, isLoading, error } = useIndexes()
  const index         = useSearchStore((s) => s.index)
  const extraIndexes  = useSearchStore((s) => s.extraIndexes)
  const setIndex      = useSearchStore((s) => s.setIndex)
  const toggleExtra   = useSearchStore((s) => s.toggleExtraIndex)

  React.useEffect(() => {
    if (!index && indexes && indexes.length > 0) setIndex(indexes[0].name)
  }, [index, indexes, setIndex])

  if (isLoading) return <span className="text-sm text-slate-500">loading indexes…</span>
  if (error)     return <span className="text-sm text-red-600">{String((error as Error).message)}</span>
  if (!indexes?.length) return <span className="text-sm text-slate-500">no indexes yet — run a pipeline</span>

  // Indexes not currently selected as either primary or extra — the
  // pool from which "+ add" picks another.
  const addable = indexes.filter((i) => i.name !== index && !extraIndexes.includes(i.name))

  return (
    <div className="flex items-center gap-1 flex-wrap">
      <select
        className="border border-slate-300 dark:border-slate-600 rounded px-2 py-1 text-sm bg-white dark:bg-slate-800 dark:text-slate-100"
        value={index ?? ''}
        onChange={(e) => setIndex(e.target.value)}
      >
        {indexes.map((i) => (
          <option key={i.name} value={i.name}>
            {i.name} ({i.docCount >= 0 ? i.docCount : '?'} docs)
          </option>
        ))}
      </select>

      {extraIndexes.map((n) => (
        <span
          key={n}
          className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded bg-hitorro-primary/10 text-hitorro-primary dark:bg-hitorro-primary/20"
          title={`Federating over ${n} — click ✕ to remove`}
        >
          {n}
          <button
            type="button"
            className="hover:text-red-600"
            onClick={() => toggleExtra(n)}
            aria-label={`Remove ${n} from federation`}
          >✕</button>
        </span>
      ))}

      {addable.length > 0 && (
        <select
          className="border border-slate-300 dark:border-slate-600 rounded px-1.5 py-0.5 text-xs bg-slate-50 dark:bg-slate-700 dark:text-slate-200"
          value=""
          onChange={(e) => { if (e.target.value) toggleExtra(e.target.value); e.target.value = '' }}
          title="Add another index to federate over"
        >
          <option value="">+ federate…</option>
          {addable.map((i) => (
            <option key={i.name} value={i.name}>{i.name}</option>
          ))}
        </select>
      )}
    </div>
  )
}
