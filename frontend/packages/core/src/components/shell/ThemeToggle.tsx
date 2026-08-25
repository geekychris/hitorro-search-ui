import React from 'react'
import { useTheme } from '../../hooks/useTheme'

/**
 * Sun/moon button that flips between light and dark mode. Reads +
 * writes the same localStorage key the useTheme hook manages, so the
 * choice survives reloads and applies before first paint (via the
 * OS preference on very first visit).
 */
export function ThemeToggle() {
  const [theme, toggle] = useTheme()
  const isDark = theme === 'dark'
  return (
    <button
      type="button"
      onClick={toggle}
      title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-700 transition"
      aria-label="Toggle color theme"
    >
      {isDark ? (
        <svg viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4 text-amber-400">
          <path d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464-4.95a1 1 0 011.414 1.414l-.707.707a1 1 0 11-1.414-1.414l.707-.707zM17 9a1 1 0 100 2h1a1 1 0 100-2h-1zM6.05 6.464A1 1 0 015.343 5.05L4.636 4.343A1 1 0 003.222 5.757L3.93 6.464a1 1 0 001.414 0zM10 15a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zm5.657-1.657a1 1 0 010 1.414l-.707.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM3 10a1 1 0 011-1h1a1 1 0 110 2H4a1 1 0 01-1-1zm3.05 3.05a1 1 0 010 1.414l-.707.707A1 1 0 013.93 13.757l.707-.707a1 1 0 011.414 0z"/>
        </svg>
      ) : (
        <svg viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4 text-slate-500">
          <path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z"/>
        </svg>
      )}
    </button>
  )
}
