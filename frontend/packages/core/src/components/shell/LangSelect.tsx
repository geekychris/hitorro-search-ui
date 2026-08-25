import React from 'react'
import { useSearchStore } from '../../state/store'

/** ISO 639-1 lang selector. Pinned list for now; when the schema hint
 *  exposes actually-populated langs per index we can drive this from
 *  useIndexSchema. */
const LANGS = [
  { code: 'en', label: 'English' },
  { code: 'de', label: 'Deutsch' },
  { code: 'fr', label: 'Français' },
  { code: 'es', label: 'Español' },
  { code: 'it', label: 'Italiano' },
  { code: 'ja', label: '日本語' },
  { code: 'zh', label: '中文' },
  { code: 'nl', label: 'Nederlands' },
]

export function LangSelect() {
  const lang    = useSearchStore((s) => s.lang)
  const setLang = useSearchStore((s) => s.setLang)
  return (
    <select
      className="border border-slate-300 rounded px-2 py-1 text-sm bg-white"
      value={lang}
      onChange={(e) => setLang(e.target.value)}
      title="Query-time language for i18n fields (title/body in the right mls entry)"
    >
      {LANGS.map((l) => (
        <option key={l.code} value={l.code}>{l.label}</option>
      ))}
    </select>
  )
}
