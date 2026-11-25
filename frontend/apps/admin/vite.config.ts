/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import { backend, baseConfig, testConfig } from '../../vite.shared.ts';

/** Administration app, served under "/admin/" (reached through the book's dev server on port 5173). */
export default defineConfig({
  ...baseConfig(),
  base: '/admin/',
  server: {
    port: 5174,
    strictPort: true,
    // Local domain names from the hosts file (see README).
    allowedHosts: ['portfolio-public', 'portfolio-admin'],
    hmr: { clientPort: 5173 },
    proxy: { '/api': backend, '/media': backend },
  },
  test: { ...testConfig, name: 'admin' },
});
