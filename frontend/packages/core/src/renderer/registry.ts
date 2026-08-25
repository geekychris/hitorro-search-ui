import type { ComponentType } from 'react'
import type { SearchHit, IndexSchema } from '../types/api'

/**
 * Props every registered type-override receives. Overrides get everything
 * they need to render polished output without having to poke at global
 * state or refetch anything.
 */
export interface TypeRendererProps {
  hit: SearchHit
  schema?: IndexSchema
  /** Current query text — enables snippet highlighting inside overrides. */
  query?: string
  /** Current language — pick the right mls entry. */
  lang: string
  /** Click callback — the AppShell wires this to open the detail drawer. */
  onOpen?: () => void
  /** Add-filter callback — cards (or their entity chips) call this to
   *  drop a `+field:value` into the current search state without
   *  needing to know how the store is wired. */
  onFilter?: (field: string, value: string) => void
}

// Module-scoped registry — populated at app boot via registerTypeRenderer.
const registry = new Map<string, ComponentType<TypeRendererProps>>()

/**
 * Register a React component to render hits of a specific JVS type.
 * Example: `registerTypeRenderer('mail_email', MailEmailCard)`.
 *
 * Multiple calls with the same type name overwrite silently — the most
 * recent registration wins. Useful for hot-reload and for apps that want
 * to swap renderers based on user preferences.
 */
export function registerTypeRenderer(htType: string, cmp: ComponentType<TypeRendererProps>) {
  registry.set(htType, cmp)
}

export function unregisterTypeRenderer(htType: string) {
  registry.delete(htType)
}

export function getTypeRenderer(htType: string | null | undefined): ComponentType<TypeRendererProps> | null {
  if (!htType) return null
  return registry.get(htType) ?? null
}

export function listRegisteredTypes(): string[] {
  return Array.from(registry.keys())
}
