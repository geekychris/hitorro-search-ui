import React from 'react'

/**
 * Shimmering skeleton block. Used everywhere the app is waiting on
 * fleet-retrieval and doesn't have prior data to keep visible. Cheap
 * to render (no JS animation — pure Tailwind + `animate-pulse`).
 *
 * Prefer over plain "Loading…" text: users perceive skeletons as
 * faster because they see the shape of what's coming.
 */
export function Skeleton({ className = 'h-4 w-full' }: { className?: string }) {
  return (
    <div
      className={`animate-pulse bg-slate-200 dark:bg-slate-700 rounded ${className}`}
      aria-hidden="true"
    />
  )
}

/** Result-card-shaped skeleton — matches the visual weight of one
 *  hit (title line + snippet lines + chips row) so the layout
 *  doesn't reflow when real results arrive. */
export function ResultCardSkeleton() {
  return (
    <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 space-y-2">
      <Skeleton className="h-4 w-3/5" />
      <div className="flex gap-2">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-3 w-16" />
      </div>
      <Skeleton className="h-3 w-full" />
      <Skeleton className="h-3 w-11/12" />
      <div className="flex gap-1 pt-1">
        <Skeleton className="h-4 w-12" />
        <Skeleton className="h-4 w-16" />
        <Skeleton className="h-4 w-10" />
      </div>
    </div>
  )
}

/** Facet-panel-shaped skeleton — one panel outline with 5 rows. */
export function FacetSkeleton() {
  return (
    <div className="border border-slate-200 dark:border-slate-700 rounded p-3 bg-white dark:bg-slate-800 space-y-2">
      <Skeleton className="h-3 w-20" />
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-center gap-2">
          <Skeleton className="h-3 w-3 rounded-sm" />
          <Skeleton className="h-3 flex-1" />
          <Skeleton className="h-3 w-6" />
        </div>
      ))}
    </div>
  )
}
