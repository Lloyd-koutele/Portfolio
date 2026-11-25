/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import { baseConfig, testConfig } from '../../vite.shared.ts';

export default defineConfig({ ...baseConfig(), test: { ...testConfig, name: 'shared' } });
