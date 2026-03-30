import {defineConfig} from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';

const shouldEnforceCoverageGate = process.env.COVERAGE_GATE === '1';

export default defineConfig({
  plugins: [angular({tsconfig: 'tsconfig.spec.json'})],
  test: {
    globals: true,
    environment: 'jsdom',
    isolate: true,
    reporters: [
      ['default', {summary: false}],
      ['junit', {outputFile: 'test-results/vitest-results.xml'}]
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json', 'json-summary', 'lcov'],
      reportsDirectory: 'coverage/grimmory',
      include: ['src/app/**/*.ts', 'src/main.ts'],
      exclude: ['src/app/**/*.spec.ts', 'src/app/**/*.module.ts', 'src/**/*.d.ts'],
      thresholds: shouldEnforceCoverageGate ? {
        statements: 90,
        branches: 90,
        functions: 90,
        lines: 90
      } : undefined
    }
  }
});
