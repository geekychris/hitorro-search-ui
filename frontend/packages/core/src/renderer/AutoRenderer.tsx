import React from 'react'
import type { TypeRendererProps } from './registry'

/**
 * Generic fallback renderer for hits whose {@code ht_type} has no
 * registered per-type override. Uses the {@code schema.rendererHints}
 * to decide what to show without hard-coding any field names:
 *
 *  - **title + body always** — pulled from the core_mls fields
 *    (sysobject-derived types put subject/name in title; summary/body
 *    text in body). Highlighted around the current query when a
 *    snippet is available; falls back to a body-clip when it isn't.
 *  - **identifier chips** — every schema-flagged {@code identifier}
 *    field with a non-null scalar value on this doc renders as a
 *    small chip in the metadata row. Booleans get color-coded chips;
 *    everything else is a plain label:value pair.
 *  - **date badges** — schema-flagged {@code date} fields render as
 *    localised timestamps in the right-hand corner.
 *  - **NER entities** — parses the `[{term&&NE_Type}]` bracket
 *    format out of every mls entry's segmented_ner array and renders
 *    each entity as a coloured chip.
 *
 * Everything is Tailwind-only — no CSS imports — so consumers can
 * restyle by extending the shared tailwind.config.js.
 */
export function AutoRenderer({ hit, schema, lang, query, onOpen, onFilter }: TypeRendererProps) {
  const doc = hit.doc ?? {}
  const hints = schema?.rendererHints
  const title = pickMls(doc.title, lang) ?? doc.name ?? doc.subject ?? '(untitled)'
  const bodyText  = pickMls(doc.body, lang, 'clean') ?? pickMls(doc.body, lang, 'text')
  const bodyMlses = doc.body?.mls ?? []
  const ner = collectNer(bodyMlses, lang)

  const identifierChips = (hints?.identifier ?? [])
    .map((f) => ({ field: f, value: doc[f] }))
    .filter((c) => c.value !== null && c.value !== undefined && typeof c.value !== 'object')

  const dateBadges = (hints?.date ?? [])
    .map((f) => ({ field: f, value: doc[f]?.date_received ?? doc[f] }))
    .filter((d) => d.value !== null && d.value !== undefined)

  return (
    <div
      className="p-4 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-hitorro-primary dark:hover:border-hitorro-primary hover:shadow-md cursor-pointer transition"
      onClick={onOpen}
    >
      {/* header row: title + type chip + first date badge */}
      <div className="flex items-start gap-2">
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100 truncate">{String(title)}</h3>
          <div className="mt-0.5 flex items-center gap-2">
            {hit.htType && (
              <span className="px-1.5 py-0.5 text-[10px] font-mono rounded bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                {hit.htType}
              </span>
            )}
            {hit.id && <code className="text-[11px] text-slate-400">{hit.id}</code>}
          </div>
        </div>
        {dateBadges[0] && (
          <span className="shrink-0 text-[11px] text-slate-400 font-mono">
            {fmtDate(dateBadges[0].value)}
          </span>
        )}
      </div>

      {/* body — snippet if we have one, else body clip; always visible */}
      {(hit.snippet || bodyText) && (
        <p className="mt-2 text-sm text-slate-700 dark:text-slate-300 line-clamp-3">
          {hit.snippet
            ? <HighlightedText text={hit.snippet} query={query} />
            : <span className="text-slate-500 dark:text-slate-400">{bodyText}</span>}
        </p>
      )}

      {/* identifier chips — sender_domain, is_newsletter, flags, etc.
          Clickable when onFilter is available so users can drill-in
          by clicking a value directly on the card. */}
      {identifierChips.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {identifierChips.map((c, i) => (
            <FieldChip
              key={i} field={c.field} value={c.value}
              onClick={onFilter ? () => onFilter(c.field, String(c.value)) : undefined}
            />
          ))}
        </div>
      )}

      {/* NER entities — one chip per unique (term, type) seen in body.
          Click adds `+body.mls.segmented_ner:NE_Type` so the user
          filters by "docs mentioning this entity type". */}
      {ner.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {ner.slice(0, 8).map((e, i) => (
            <button
              key={i}
              className={`px-1.5 py-0.5 text-[11px] rounded ${entityColor(e.type)} ${onFilter ? 'cursor-pointer hover:ring-1 hover:ring-offset-1' : ''}`}
              onClick={(ev) => {
                if (!onFilter) return
                ev.stopPropagation()
                onFilter('body.mls.segmented_ner', `NE_${e.type}`)
              }}
              title={onFilter ? `Filter to docs with NE_${e.type}` : undefined}
              type="button"
            >
              {e.term} <span className="opacity-60">· {e.type}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ==================== helpers (exported for per-type cards to reuse) ======

/** Return the first-matching-lang value from a core_mls field. */
export function pickMls(mls: any, lang: string, field: string = 'text'): any {
  if (!mls?.mls || !Array.isArray(mls.mls)) return null
  const match = mls.mls.find((m: any) => m.lang === lang) ?? mls.mls[0]
  return match?.[field] ?? null
}

/** Pull `[{term&&NE_Type}]` chips from every mls entry's segmented_ner. */
export function collectNer(mlses: any[], lang: string): { term: string; type: string }[] {
  const re = /\[\{([^&]+)&&(NE_[A-Za-z]+)\}\]/g
  const seen = new Set<string>()
  const out: { term: string; type: string }[] = []
  for (const m of mlses) {
    if (lang && m?.lang && m.lang !== lang) continue
    const sentences: string[] = Array.isArray(m?.segmented_ner) ? m.segmented_ner : []
    for (const s of sentences) {
      let match
      while ((match = re.exec(s)) !== null) {
        const key = `${match[1]}|${match[2]}`
        if (seen.has(key)) continue
        seen.add(key)
        out.push({ term: match[1].trim(), type: match[2].replace('NE_', '') })
      }
    }
    if (out.length) break   // only the matched-lang mls entry
  }
  return out
}

export function entityColor(t: string): string {
  switch (t) {
    case 'Person':       return 'bg-emerald-100 text-emerald-800'
    case 'Location':     return 'bg-sky-100 text-sky-800'
    case 'Organization': return 'bg-violet-100 text-violet-800'
    case 'Date':         return 'bg-amber-100 text-amber-800'
    default:             return 'bg-slate-100 text-slate-700'
  }
}

/** Format ISO / epoch-ms / raw date into a compact localised string. */
export function fmtDate(v: any): string {
  if (v == null) return ''
  const d = typeof v === 'number' ? new Date(v) : new Date(String(v))
  if (isNaN(d.getTime())) return String(v)
  return d.toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  })
}

/** Highlight the first bare query term in a text run. */
export function HighlightedText({ text, query }: { text: string; query?: string }) {
  if (!query || query.trim() === '' || query.trim() === '*:*') return <>{text}</>
  const bare = query.replace(/[+\-!(){}[\]^"~*?:\\/]/g, ' ').trim().toLowerCase()
  if (!bare) return <>{text}</>
  const first = bare.split(/\s+/)[0]
  const idx = text.toLowerCase().indexOf(first)
  if (idx < 0) return <>{text}</>
  return (
    <>
      {text.substring(0, idx)}
      <mark className="bg-yellow-200 text-slate-900 rounded px-0.5">{text.substring(idx, idx + first.length)}</mark>
      {text.substring(idx + first.length)}
    </>
  )
}

/**
 * Compact chip for a top-level scalar field. Booleans go colour-coded;
 * everything else renders as `field:value`. Long values truncate; the
 * `title` attribute gives the full text on hover.
 */
function FieldChip({ field, value, onClick }: { field: string; value: any; onClick?: () => void }) {
  if (typeof value === 'boolean') {
    const [label, cls] = booleanChip(field, value)
    return (
      <ChipButton className={cls} onClick={onClick} title={`Filter ${field}:${value}`}>
        {label}
      </ChipButton>
    )
  }
  const s = String(value)
  const short = s.length > 40 ? s.slice(0, 40) + '…' : s
  return (
    <ChipButton
      className="bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200"
      onClick={onClick}
      title={onClick ? `Filter ${field}:${s}` : `${field}: ${s}`}
    >
      <span className="text-slate-500 dark:text-slate-400">{field}:</span> {short}
    </ChipButton>
  )
}

/** Renders as a plain span when no onClick is provided (informational
 *  chip); becomes a button with hover ring when clickable. Both variants
 *  share layout so a click-toggle doesn't jiggle the layout. */
function ChipButton({ children, className, onClick, title }: {
  children: React.ReactNode; className: string; onClick?: () => void; title?: string
}) {
  const base = 'px-1.5 py-0.5 text-[11px] rounded'
  if (!onClick) return <span className={`${base} ${className}`} title={title}>{children}</span>
  return (
    <button
      type="button"
      className={`${base} ${className} cursor-pointer hover:ring-1 hover:ring-offset-1 hover:ring-hitorro-primary/40`}
      onClick={(e) => { e.stopPropagation(); onClick() }}
      title={title}
    >{children}</button>
  )
}

/**
 * Map (fieldName, bool) → [display, tailwind-classes]. Named booleans
 * like `read`, `flagged`, `is_newsletter` get semantically-coloured
 * chips instead of generic true/false.
 */
function booleanChip(field: string, value: boolean): [string, string] {
  const F = field.replace(/^is_/, '')
  if (field === 'read')          return [value ? 'read'   : 'unread',     value ? 'bg-slate-100 text-slate-600' : 'bg-hitorro-primary/10 text-hitorro-primary']
  if (field === 'flagged')       return [value ? '⚑ flagged' : 'flagged', value ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-400']
  if (field === 'is_newsletter') return [value ? '📰 newsletter' : 'not newsletter', value ? 'bg-purple-100 text-purple-700' : 'bg-slate-100 text-slate-400']
  return [value ? F : `not ${F}`, value ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-400']
}
