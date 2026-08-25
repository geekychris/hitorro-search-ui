import base from '../../tailwind.config.js'

/** @type {import('tailwindcss').Config} */
export default {
  ...base,
  // Include both the demo app's own sources AND the library's compiled
  // dist so Tailwind's purge sees every class name used by the shell,
  // facet panel, result cards, etc.
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    '../core/dist/**/*.{js,mjs}',
    '../core/src/**/*.{ts,tsx}',
  ],
}
