import React from 'react'
import { useSearchStore } from '../../state/store'

/**
 * Big central query input for end-user mode. Debounced 300 ms so
 * mid-word typing doesn't spam the coordinator; hitting Enter fires
 * immediately.
 */
export function SearchBox({ placeholder = 'Search…' }: { placeholder?: string }) {
  const q        = useSearchStore((s) => s.q)
  const setQuery = useSearchStore((s) => s.setQuery)
  const [local, setLocal] = React.useState(q)

  // Keep local in sync when store changes externally (e.g. clear).
  React.useEffect(() => { setLocal(q) }, [q])

  // Debounce store update — 300 ms feels responsive without flooding.
  React.useEffect(() => {
    const t = setTimeout(() => {
      if (local !== q) setQuery(local)
    }, 300)
    return () => clearTimeout(t)
  }, [local, q, setQuery])

  return (
    <div className="relative">
      <input
        type="text"
        className="w-full pl-10 pr-4 py-2.5 text-base border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-hitorro-primary"
        placeholder={placeholder}
        value={local}
        onChange={(e) => setLocal(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') setQuery(local) }}
      />
      <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" viewBox="0 0 20 20" fill="currentColor">
        <path fillRule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clipRule="evenodd" />
      </svg>
    </div>
  )
}
