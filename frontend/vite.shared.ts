import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import type { UserConfig } from 'vite';

/** Settings common to both apps: React, Tailwind, the @shared alias and the test environment. */
export const sharedSrc = fileURLToPath(new URL('./packages/shared/src', import.meta.url));
export const backend = 'http://localhost:8080';

export function baseConfig(): UserConfig {
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@shared': sharedSrc } },
  };
}

export const testConfig = {
  environment: 'jsdom',
  setupFiles: [`${sharedSrc}/test/setup.ts`],
  css: false,
  include: ['src/**/*.test.{ts,tsx}'],
};
