import React from 'react'
import {
  type TypeRendererProps,
  pickMls, collectNer, entityColor, fmtDate, HighlightedText,
} from '@hitorro/search-ui-core'

/**
 * Reference per-type override for hits whose {@code ht_type} matches
 * one of the mail types (mail_email, mail_message). Both types share
 * the same field set inherited from sysobject + a common set of
 * mail-specific top-level fields, so the same card renders both.
 *
 * Layout leans into email conventions — subject headline is the
 * primary readable, sender chips + received-date sit as a metadata
 * row, snippet-highlighted body preview underneath, then NER entity
 * chips (persons / orgs / locations extracted by the enrichment
 * step). Title + body are always visible; extra fields are progressive
 * enhancement — anything null just doesn't render.
 *
 * The imports from @hitorro/search-ui-core are the same helpers
 * AutoRenderer uses internally, so per-type overrides get consistent
 * MLS/language handling + snippet highlighting + NER parsing for free.
 * Registered in main.tsx via registerTypeRenderer("mail_email", ...)
 * and again for "mail_message" — one card, two type aliases.
 */
export function MailEmailCard({ hit, lang, query, onOpen }: TypeRendererProps) {
  const doc = hit.doc ?? {}
  const subject = pickMls(doc.title, lang) ?? '(no subject)'
  const bodyText = pickMls(doc.body, lang, 'clean') ?? pickMls(doc.body, lang, 'text')
  const sender  = doc.sender_address ?? doc.sender_name ?? 'unknown'
  const senderName = doc.sender_name && doc.sender_name !== sender ? doc.sender_name : null
  const domain  = doc.sender_domain ?? ''
  const received = doc.times?.date_received
  const receivedFmt = received ? fmtDate(Number(received)) : null
  const ner = collectNer(doc.body?.mls ?? [], lang).slice(0, 8)
  const sizeKb = doc.size_bytes ? Math.round(doc.size_bytes / 1024) : null

  return (
    <div
      className="p-4 rounded-lg border border-slate-200 hover:border-hitorro-primary hover:shadow-md cursor-pointer transition group bg-white"
      onClick={onOpen}
    >
      {/* headline: ✉ + subject + received date on the right */}
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-base font-semibold text-slate-900 truncate group-hover:text-hitorro-primary min-w-0">
          <span className="mr-1.5 text-slate-400 group-hover:text-hitorro-primary">✉︎</span>
          {String(subject)}
        </h3>
        {receivedFmt && (
          <span className="shrink-0 text-[11px] text-slate-400 font-mono">{receivedFmt}</span>
        )}
      </div>

      {/* sender row: address (mono) + optional display name + domain chip + state flags */}
      <div className="mt-1 flex items-center gap-2 text-xs text-slate-500 flex-wrap">
        <span className="font-mono">{sender}</span>
        {senderName && <span className="italic">"{senderName}"</span>}
        {domain && (
          <span className="px-1.5 py-0.5 bg-slate-100 rounded text-[10px]">{domain}</span>
        )}
        {doc.read === false && (
          <span className="px-1.5 py-0.5 bg-hitorro-primary/10 text-hitorro-primary rounded text-[10px]">unread</span>
        )}
        {doc.flagged === true && (
          <span className="px-1.5 py-0.5 bg-amber-100 text-amber-700 rounded text-[10px]">⚑ flagged</span>
        )}
        {doc.is_newsletter === true && (
          <span className="px-1.5 py-0.5 bg-purple-100 text-purple-700 rounded text-[10px]">📰 newsletter</span>
        )}
        {sizeKb !== null && sizeKb > 0 && (
          <span className="text-slate-400">{sizeKb.toLocaleString()} KB</span>
        )}
        {typeof doc.recipient_count === 'number' && doc.recipient_count > 1 && (
          <span className="text-slate-400">{doc.recipient_count} recipients</span>
        )}
      </div>

      {/* body — snippet if we have one, else body clip; ALWAYS render when text exists */}
      {(hit.snippet || bodyText) && (
        <p className="mt-2 text-sm text-slate-700 line-clamp-3">
          {hit.snippet
            ? <HighlightedText text={hit.snippet} query={query} />
            : <span className="text-slate-500">{bodyText}</span>}
        </p>
      )}

      {/* NER entities extracted from the body — persons, orgs, locations, dates */}
      {ner.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {ner.map((e, i) => (
            <span key={i} className={`px-1.5 py-0.5 text-[11px] rounded ${entityColor(e.type)}`}>
              {e.term} <span className="opacity-60">· {e.type}</span>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
