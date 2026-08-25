import React from 'react'
import type { SearchHit } from '../../types/api'

/** Full-JSON modal for a table row. Kept as pure JSON in v1 — the
 *  detail-drawer's role in end-user mode is different (KV hydration);
 *  here the analyst already has the search result in hand. */
export function RowInspector({ hit, onClose }: { hit: SearchHit; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-40" role="dialog">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="absolute right-0 top-0 bottom-0 w-[640px] max-w-full bg-white dark:bg-slate-800 shadow-2xl overflow-auto">
        <div className="sticky top-0 bg-white dark:bg-slate-800 border-b dark:border-slate-700 p-3 flex items-center justify-between dark:text-slate-100">
          <h3 className="text-sm font-semibold">
            {hit.htType ?? 'doc'} <code className="text-hitorro-primary ml-2">{hit.id ?? '?'}</code>
          </h3>
          <button className="text-slate-500 hover:text-slate-900 text-lg" onClick={onClose}>✕</button>
        </div>
        <pre className="text-[11px] whitespace-pre-wrap break-words p-3 bg-slate-50 dark:bg-slate-900 dark:text-slate-200">
{JSON.stringify(hit.doc, null, 2)}
        </pre>
      </div>
    </div>
  )
}
