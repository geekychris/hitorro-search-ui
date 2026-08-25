import React from 'react'
import { useSearchStore } from '../../state/store'

/**
 * Free-form Lucene query bar. Doesn't debounce — analyst users expect
 * explicit submission (Enter). Provides syntax hints inline so first-
 * time users know they can write `field:value`.
 */
export function QueryBar() {
  const q        = useSearchStore((s) => s.q)
  const setQuery = useSearchStore((s) => s.setQuery)
  const [local, setLocal] = React.useState(q)

  React.useEffect(() => { setLocal(q) }, [q])

  return (
    <div className="flex items-center gap-2">
      <input
        type="text"
        className="flex-1 font-mono text-sm px-3 py-2 border border-slate-300 dark:border-slate-600 rounded bg-white dark:bg-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-hitorro-primary"
        placeholder='Lucene syntax — e.g. body.mls.segmented_ner:NE_Person'
        value={local}
        onChange={(e) => setLocal(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') setQuery(local) }}
      />
      <button
        className="px-3 py-2 bg-hitorro-primary text-white rounded text-sm hover:bg-hitorro-primary/90"
        onClick={() => setQuery(local)}
      >Run</button>
    </div>
  )
}
