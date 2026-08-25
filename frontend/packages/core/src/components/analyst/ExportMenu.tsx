import React from 'react'
import { useSearchStore } from '../../state/store'

/**
 * Three-format download button — hits {@code POST /api/search/export}
 * with the CURRENT search request (index/q/filters/facets/mode/lang/sort)
 * plus a chosen output format. The browser handles the streaming
 * download natively via Content-Disposition, so this component just
 * has to POST the request and let the response arrive as a file.
 *
 * Max rows capped at 10,000 in the BFF (raise the `max=` param to
 * grab more, up to the hard limit of 100,000). Analysts wanting the
 * full haystack should use the NDJSON stream + pipe to jq / duckdb.
 */
export function ExportMenu({ baseUrl = '' }: { baseUrl?: string }) {
  const state = useSearchStore()
  const [busy, setBusy] = React.useState<string | null>(null)

  const download = async (format: 'json' | 'ndjson' | 'csv') => {
    setBusy(format)
    try {
      // Direct download via a POSTed form-style fetch: we can't just
      // set window.location because the request body carries filters
      // + facets. Fetch → blob → object-URL → anchor click.
      const req = {
        index: state.index, q: state.q, filters: state.filters,
        facets: state.facets, sort: state.sort, mode: state.mode, lang: state.lang,
      }
      const resp = await fetch(`${baseUrl}/api/search/export?format=${format}&max=10000`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(req),
      })
      if (!resp.ok) throw new Error(`export failed: HTTP ${resp.status}`)
      const blob = await resp.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${state.index ?? 'export'}.${format}`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      alert(`Export failed: ${(e as Error).message}`)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="inline-flex items-center gap-1 text-xs">
      <span className="text-slate-500">export:</span>
      {(['json', 'ndjson', 'csv'] as const).map((f) => (
        <button
          key={f}
          className="px-2 py-0.5 border border-slate-300 dark:border-slate-600 rounded hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40"
          disabled={busy !== null || !state.index}
          onClick={() => download(f)}
          title={`Download search results as ${f.toUpperCase()}`}
        >
          {busy === f ? '⋯' : f}
        </button>
      ))}
    </div>
  )
}
