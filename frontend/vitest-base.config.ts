import {defineConfig} from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    isolate: true,
    reporters: [
      ['default', {summary: false}],
      ['junit', {outputFile: 'test-results/vitest-results.xml'}]
    ]
  },
});
