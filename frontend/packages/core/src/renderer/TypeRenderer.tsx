import React from 'react'
import { AutoRenderer } from './AutoRenderer'
import { getTypeRenderer, type TypeRendererProps } from './registry'

/**
 * Router that picks either a registered per-type override or the
 * generic {@link AutoRenderer}. The AppShell always uses this component
 * (never AutoRenderer directly) so third-party consumers can slot in
 * their own via `registerTypeRenderer(...)` and everything Just Works.
 */
export function TypeRenderer(props: TypeRendererProps) {
  const Override = getTypeRenderer(props.hit.htType ?? null)
  const Cmp = Override ?? AutoRenderer
  return <Cmp {...props} />
}
