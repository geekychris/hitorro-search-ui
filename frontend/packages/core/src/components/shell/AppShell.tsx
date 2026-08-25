import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClientContext } from '../../hooks/useClient'
import type { SearchClient } from '../../client'
import { createSearchClient } from '../../client'
import { useSearchStore } from '../../state/store'
import { IndexPicker } from './IndexPicker'
import { ModeSwitch } from './ModeSwitch'
import { LangSelect } from './LangSelect'
import { SearchBox } from '../enduser/SearchBox'
import { FacetPanel } from '../enduser/FacetPanel'
import { ResultsList } from '../enduser/ResultsList'
import { QueryBar } from '../analyst/QueryBar'
import { ResultsTable } from '../analyst/ResultsTable'
import { FacetTree } from '../analyst/FacetTree'

export interface AppShellProps {
  /** Search client. Auto-created against same-origin `/api` if omitted. */
  client?: SearchClient
  /** Optional TanStack Query client — pass your own if you want to
   *  share cache with the rest of your app. */
  queryClient?: QueryClient
  /** Text shown in the top-left header. Default: "hitorro search". */
  title?: string
}

/**
 * Top-level extensible shell. Wraps the app with QueryClient +
 * ClientContext, renders a header (title + IndexPicker + ModeSwitch +
 * LangSelect), and switches the main body between end-user and analyst
 * mode based on the store's `mode`.
 *
 * Drop this into any React app:
 *
 * ```tsx
 * import { AppShell, registerTypeRenderer } from '@hitorro/search-ui-core'
 * import { MailEmailCard } from './MailEmailCard'
 *
 * registerTypeRenderer('mail_email', MailEmailCard)
 *
 * export default function App() { return <AppShell title="Mail search" /> }
 * ```
 */
export function AppShell({ client, queryClient, title = 'hitorro search' }: AppShellProps) {
  const qc  = React.useMemo(() => queryClient ?? new QueryClient({
    defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 1 } },
  }), [queryClient])
  const cli = React.useMemo(() => client ?? createSearchClient(), [client])

  return (
    <QueryClientProvider client={qc}>
      <ClientContext.Provider value={cli}>
        <ShellBody title={title} />
      </ClientContext.Provider>
    </QueryClientProvider>
  )
}

// ------------------------------------------------------------------------

function ShellBody({ title }: { title: string }) {
  const mode = useSearchStore((s) => s.mode)
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="sticky top-0 z-30 bg-white border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center gap-3 flex-wrap">
          <h1 className="text-lg font-semibold text-hitorro-primary">{title}</h1>
          <span className="text-slate-300">|</span>
          <IndexPicker />
          <LangSelect />
          <span className="flex-1" />
          <ModeSwitch />
        </div>
      </header>

      <main className="max-w-7xl mx-auto p-4">
        {mode === 'end-user' ? <EndUserView /> : <AnalystView />}
      </main>
    </div>
  )
}

function EndUserView() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-[16rem_1fr] gap-4">
      <FacetPanel />
      <div>
        <SearchBox placeholder="Search across the selected index…" />
        <div className="mt-4"><ResultsList /></div>
      </div>
    </div>
  )
}

function AnalystView() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-[14rem_1fr] gap-4">
      <FacetTree />
      <div>
        <QueryBar />
        <div className="mt-4"><ResultsTable /></div>
      </div>
    </div>
  )
}
