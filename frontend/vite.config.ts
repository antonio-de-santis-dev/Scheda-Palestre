/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

const backend = process.env.GYM_BACKEND_URL ?? 'http://localhost:8080';

// The SPA talks to the backend on the same origin: in development Vite proxies /api.
export default defineConfig({
  plugins: [react(), { name: 'gymplanner-service-worker', generateBundle() {
    this.emitFile({ type: 'asset', fileName: 'sw.js', source: readFileSync(new URL('./src/pwa/sw.js', import.meta.url), 'utf8').replace('__BUILD_ID__', randomUUID()) });
  } }],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/actuator': { target: backend, changeOrigin: false },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    restoreMocks: true,
  },
});
