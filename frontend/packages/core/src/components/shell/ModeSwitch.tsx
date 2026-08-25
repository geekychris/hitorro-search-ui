import React from 'react'
import { useSearchStore } from '../../state/store'
import clsx from 'clsx'

/**
 * Toggle between end-user (cards + facets + snippets) and analyst
 * (query bar + table + JSON inspector). Purely a UI selector — the
 * mode change is pushed into the store which triggers the search
 * hooks to re-request with the right stages.
 */
export function ModeSwitch() {
  const mode    = useSearchStore((s) => s.mode)
  const setMode = useSearchStore((s) => s.setMode)
  return (
    <div className="inline-flex rounded border border-slate-300 overflow-hidden text-sm">
      {(['end-user', 'analyst'] as const).map((m) => (
        <button
          key={m}
          className={clsx(
            'px-3 py-1 transition',
            mode === m ? 'bg-hitorro-primary text-white' : 'bg-white text-slate-700 hover:bg-slate-100'
          )}
          onClick={() => setMode(m)}
        >
          {m === 'end-user' ? '👤 End-user' : '🔬 Analyst'}
        </button>
      ))}
    </div>
  )
}
