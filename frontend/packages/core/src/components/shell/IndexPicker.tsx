import React from 'react'
import { useIndexes } from '../../hooks/queries'
import { useSearchStore } from '../../state/store'

/**
 * Select the index to search. Auto-picks the first index on mount when
 * the store's `index` is null; wraps around the fleet-retrieval-driven
 * list so a mesh pipeline that lands a new index makes it selectable
 * on the next 60-second refresh.
 */
export function IndexPicker() {
  const { data: indexes, isLoading, error } = useIndexes()
  const index    = useSearchStore((s) => s.index)
  const setIndex = useSearchStore((s) => s.setIndex)

  React.useEffect(() => {
    if (!index && indexes && indexes.length > 0) setIndex(indexes[0].name)
  }, [index, indexes, setIndex])

  if (isLoading) return <span className="text-sm text-slate-500">loading indexes…</span>
  if (error)     return <span className="text-sm text-red-600">{String((error as Error).message)}</span>
  if (!indexes?.length) return <span className="text-sm text-slate-500">no indexes yet — run a pipeline</span>

  return (
    <select
      className="border border-slate-300 rounded px-2 py-1 text-sm bg-white"
      value={index ?? ''}
      onChange={(e) => setIndex(e.target.value)}
    >
      {indexes.map((i) => (
        <option key={i.name} value={i.name}>
          {i.name} ({i.docCount >= 0 ? i.docCount : '?'} docs)
        </option>
      ))}
    </select>
  )
}
