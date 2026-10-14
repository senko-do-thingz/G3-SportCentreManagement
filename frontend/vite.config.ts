/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  define: mode === 'manager-preview' ? { 'import.meta.env.VITE_MANAGER_PREVIEW': '"true"' } : {},
  plugins: [react()],
  server: {
    port: 5173,
    // Must match an origin allowed by the backend CORS config (dev profile default).
    strictPort: true,
  },
  test: {
    maxWorkers: 2,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    env: {
      VITE_API_BASE_URL: 'http://api.test/api/v1',
    },
  },
}))
