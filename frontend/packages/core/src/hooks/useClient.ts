import { createContext, useContext } from 'react'
import type { SearchClient } from '../client'

// Injects the fleet-retrieval / BFF client into hooks. AppShell wraps
// with the provider; downstream hooks pull it out via useClient().
export const ClientContext = createContext<SearchClient | null>(null)

export function useClient(): SearchClient {
  const c = useContext(ClientContext)
  if (!c) throw new Error(
    'useClient() called outside <AppShell> — wrap your app or pass a client via ClientContext.Provider')
  return c
}
