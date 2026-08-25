import { useEffect, useState } from 'react'

/**
 * Dark-mode toggle backed by localStorage + the `dark` class on
 * <html> (which Tailwind's `darkMode: 'class'` reads). Initialises
 * from a stored preference or the OS `prefers-color-scheme` fallback,
 * so a first-time user sees whatever they already prefer.
 *
 * Emits [theme, toggle] — the caller renders a button that calls
 * toggle() and reflects the current value.
 */
export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'hitorro-search-ui:theme'

function readInitial(): Theme {
  if (typeof window === 'undefined') return 'light'
  const stored = localStorage.getItem(STORAGE_KEY) as Theme | null
  if (stored === 'light' || stored === 'dark') return stored
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(readInitial)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    localStorage.setItem(STORAGE_KEY, theme)
  }, [theme])

  return [theme, () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))]
}
