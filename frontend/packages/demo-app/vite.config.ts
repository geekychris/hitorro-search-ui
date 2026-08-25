import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// SPA build. Emits dist/index.html + dist/assets/*. Maven's
// maven-resources-plugin copies dist/ into the backend jar's
// src/main/resources/static/ during `mvn package`, so the Spring Boot
// app serves this directly at /.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Dev-mode: proxy /api/* to the backend on :8100 so `pnpm dev`
    // works while the backend is running via `mesh-up.sh`.
    proxy: {
      '/api': {
        target: 'http://localhost:8100',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Emit into ./dist (default); Maven copies it into the jar.
    sourcemap: true,
  },
})
