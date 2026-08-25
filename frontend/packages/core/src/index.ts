// ===== @hitorro/search-ui-core — public API surface =====================
// Everything a consumer app needs to build a search UI against hitorro-
// fleet-retrieval (via the BFF at /api/*). Kept flat + explicit so the
// entry-point is easy to scan and to tree-shake.

// Top-level shell — most apps only import this + AppShellProps.
export { AppShell } from './components/shell/AppShell'
export type { AppShellProps } from './components/shell/AppShell'

// Individual components — for apps that build a custom layout instead
// of the default shell.
export { IndexPicker } from './components/shell/IndexPicker'
export { ModeSwitch }  from './components/shell/ModeSwitch'
export { LangSelect }  from './components/shell/LangSelect'
export { SearchBox }   from './components/enduser/SearchBox'
export { FacetPanel }  from './components/enduser/FacetPanel'
export { ResultsList } from './components/enduser/ResultsList'
export { DocDrawer }   from './components/enduser/DocDrawer'
export { QueryBar }     from './components/analyst/QueryBar'
export { ResultsTable } from './components/analyst/ResultsTable'
export { RowInspector } from './components/analyst/RowInspector'
export { FacetTree }    from './components/analyst/FacetTree'

// Renderer — for per-type override registration.
export { TypeRenderer }   from './renderer/TypeRenderer'
export { AutoRenderer }   from './renderer/AutoRenderer'
export { registerTypeRenderer, unregisterTypeRenderer, listRegisteredTypes,
         getTypeRenderer } from './renderer/registry'
export type { TypeRendererProps } from './renderer/registry'

// Renderer helpers — reusable in per-type card templates so overrides
// stay consistent with AutoRenderer's MLS handling, snippet
// highlighting, NER parsing, date formatting, entity colours.
export { pickMls, collectNer, entityColor, fmtDate, HighlightedText }
       from './renderer/AutoRenderer'

// Client — for apps that inject a customised transport (auth headers,
// tracing, etc).
export { createSearchClient } from './client'
export type { SearchClient, ClientOptions, Transport } from './client'
export { ClientContext, useClient } from './hooks/useClient'

// Hooks — for apps that build custom UI on top of the same data.
export { useSearch, useIndexes, useIndexSchema, useDoc } from './hooks/queries'
export { useSearchStore }  from './state/store'
export type { SearchState } from './state/store'

// Types — TS mirror of BFF DTOs.
export type {
  Mode, SearchRequest, SearchResponse, SearchHit, Facet, FacetValue,
  IndexInfo, IndexSchema, RendererHints,
} from './types/api'
