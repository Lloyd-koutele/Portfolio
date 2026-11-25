/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import { backend, baseConfig, testConfig } from '../../vite.shared.ts';

/**
 * Reading app, served at "/". In development it also forwards /admin to the admin dev server, so the whole site
 * is reachable at http://localhost:5173 exactly as in production.
 */
export default defineConfig({
  ...baseConfig(),
  base: '/',
  server: {
    port: 5173,
    strictPort: true,
    // Local domain names from the hosts file (see README).
    allowedHosts: ['portfolio-public', 'portfolio-admin'],
    proxy: {
      '/api': backend,
      '/media': backend,
      '/admin': { target: 'http://localhost:5174', ws: true },
    },
  },
  test: { ...testConfig, name: 'book' },
});
