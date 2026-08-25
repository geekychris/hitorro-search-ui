import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClientContext } from '../../hooks/useClient'
import type { SearchClient } from '../../client'
import { createSearchClient } from '../../client'
import { useSearchStore } from '../../state/store'
import { useUrlSync } from '../../state/urlSync'
import { IndexPicker } from './IndexPicker'
import { ModeSwitch } from './ModeSwitch'
import { LangSelect } from './LangSelect'
import { ThemeToggle } from './ThemeToggle'
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
 * LangSelect + ThemeToggle), and switches the main body between
 * end-user and analyst mode based on the store's `mode`.
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
  useUrlSync()   // two-way store <-> location.hash — bookmarks + back/forward
  const mode = useSearchStore((s) => s.mode)
  const [drawerOpen, setDrawerOpen] = React.useState(false)

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 transition-colors">
      <header className="sticky top-0 z-30 bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center gap-3 flex-wrap">
          {/* Mobile-only: sidebar toggle. Shown below `lg:` where the
              facet sidebar collapses into a slide-in drawer. */}
          <button
            type="button"
            onClick={() => setDrawerOpen((o) => !o)}
            className="lg:hidden p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-700"
            aria-label="Toggle sidebar"
          >
            <svg viewBox="0 0 20 20" fill="currentColor" className="w-5 h-5"><path d="M2 4h16v2H2V4zm0 5h16v2H2V9zm0 5h16v2H2v-2z"/></svg>
          </button>
          <h1 className="text-lg font-semibold text-hitorro-primary">{title}</h1>
          <span className="text-slate-300 dark:text-slate-600 hidden sm:inline">|</span>
          <IndexPicker />
          <LangSelect />
          <span className="flex-1" />
          <ThemeToggle />
          <ModeSwitch />
        </div>
      </header>

      <main className="max-w-7xl mx-auto p-4">
        {mode === 'end-user'
          ? <EndUserView drawerOpen={drawerOpen} onCloseDrawer={() => setDrawerOpen(false)} />
          : <AnalystView drawerOpen={drawerOpen} onCloseDrawer={() => setDrawerOpen(false)} />}
      </main>
    </div>
  )
}

/**
 * Two-column layout above `lg:`; below it the sidebar collapses to a
 * slide-in drawer controlled by the header hamburger. The drawer sits
 * over the results with a translucent backdrop so users can peel it
 * away by tapping outside.
 */
function EndUserView({ drawerOpen, onCloseDrawer }: DrawerProps) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-[16rem_1fr] gap-4">
      <SidebarWrap open={drawerOpen} onClose={onCloseDrawer}><FacetPanel /></SidebarWrap>
      <div>
        <SearchBox placeholder="Search across the selected index…" />
        <div className="mt-4"><ResultsList /></div>
      </div>
    </div>
  )
}

function AnalystView({ drawerOpen, onCloseDrawer }: DrawerProps) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-[14rem_1fr] gap-4">
      <SidebarWrap open={drawerOpen} onClose={onCloseDrawer}><FacetTree /></SidebarWrap>
      <div>
        <QueryBar />
        <div className="mt-4"><ResultsTable /></div>
      </div>
    </div>
  )
}

interface DrawerProps { drawerOpen: boolean; onCloseDrawer: () => void }

/**
 * At `lg+` renders children inline in the grid column. Below `lg`,
 * renders as a fixed slide-in drawer that opens when `drawerOpen`
 * is true. One component handles both so the two views don't
 * duplicate the mobile-drawer plumbing.
 */
function SidebarWrap({ open, onClose, children }: { open: boolean; onClose: () => void; children: React.ReactNode }) {
  return (
    <>
      {/* Desktop: inline in grid */}
      <div className="hidden lg:block">{children}</div>

      {/* Mobile: drawer with backdrop */}
      {open && (
        <div className="lg:hidden fixed inset-0 z-40" role="dialog">
          <div className="absolute inset-0 bg-black/40" onClick={onClose} />
          <div className="absolute left-0 top-0 bottom-0 w-72 max-w-[80%] bg-slate-50 dark:bg-slate-900 shadow-2xl overflow-auto p-3">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm font-semibold">Filters</span>
              <button className="text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 text-lg" onClick={onClose}>✕</button>
            </div>
            {children}
          </div>
        </div>
      )}
    </>
  )
}
