import {defineConfig} from 'vitest/config';

const shouldEnforceCoverageGate = process.env['COVERAGE_GATE'] === '1';

export default defineConfig({
  test: {
    pool: 'threads',
    maxWorkers: '50%',
    unstubGlobals: true,
    coverage: {
      thresholds: shouldEnforceCoverageGate ? {
        statements: 90,
        branches: 90,
        functions: 90,
        lines: 90
      } : undefined
    }
  }
});
