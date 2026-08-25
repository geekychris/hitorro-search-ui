import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// SPA build. Emits dist/index.html + dist/assets/*. Maven's
// maven-resources-plugin copies dist/ into the backend jar's
// src/main/resources/static/ during `mvn package`, so the Spring Boot
// app serves this directly at /.
//
// SEARCH_UI_DEV_PROXY overrides the /api/* proxy target for `pnpm dev`.
// Defaults to :8100 to match the packaged backend; override when you're
// running the BFF on a different port or a remote host.
const proxyTarget = process.env.SEARCH_UI_DEV_PROXY ?? 'http://localhost:8100'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: proxyTarget, changeOrigin: true },
    },
  },
  build: {
    sourcemap: true,
  },
})
