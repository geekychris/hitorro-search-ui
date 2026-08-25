/** @type {import('tailwindcss').Config} */
// Shared config picked up by both the core library and the demo app.
// Each package extends with its own `content` paths in its own tailwind
// config so purge scans the right files.
export default {
  // darkMode: 'class' — activate by adding `dark` class to <html>. The
  // useTheme hook in the library toggles this + persists to localStorage.
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        hitorro: {
          primary: '#0e7490',
          accent: '#f97316',
          muted: '#64748b',
        },
      },
    },
  },
  plugins: [],
}
