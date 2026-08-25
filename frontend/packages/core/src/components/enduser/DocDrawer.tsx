import React from 'react'
import { useDoc } from '../../hooks/queries'

/**
 * Full-doc drawer opened when a result card is clicked. Fetches the KV-
 * hydrated document (fleet-retrieval /documents/{index}/{key}) and
 * shows it as pretty-printed JSON. A future enhancement is a
 * type-aware detail view — for v1, JSON is honest and unopinionated.
 */
export function DocDrawer({ index, docKey, onClose }: { index: string; docKey: string; onClose: () => void }) {
  const { data, isLoading, error } = useDoc(index, docKey)
  return (
    <div className="fixed inset-0 z-40" role="dialog">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} />
      <div className="absolute right-0 top-0 bottom-0 w-[560px] max-w-full bg-white dark:bg-slate-800 shadow-2xl overflow-auto">
        <div className="sticky top-0 bg-white dark:bg-slate-800 border-b dark:border-slate-700 p-3 flex items-center justify-between dark:text-slate-100">
          <h3 className="text-sm font-semibold">
            {index} / <code className="text-hitorro-primary">{docKey}</code>
          </h3>
          <button className="text-slate-500 hover:text-slate-900 text-lg" onClick={onClose}>✕</button>
        </div>
        <div className="p-3">
          {isLoading && <div className="text-slate-500">Loading…</div>}
          {error && <div className="text-red-600 text-sm">{String((error as Error).message)}</div>}
          {data && (
            <pre className="text-[11px] whitespace-pre-wrap break-words bg-slate-50 dark:bg-slate-900 dark:text-slate-200 p-2 rounded">
              {JSON.stringify(data, null, 2)}
            </pre>
          )}
        </div>
      </div>
    </div>
  )
}
