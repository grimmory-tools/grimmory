import {describe, expect, it} from 'vitest';

import {EN_TRANSLATIONS} from '../../../core/config/transloco-loader';
import {LIBRARY_STATS_CHARTS} from './library-stats.registry';
import {READING_STATS_CHARTS} from './reading-stats.registry';

function translation(key: string): unknown {
  return key.split('.').reduce<unknown>(
    (node, part) => (typeof node === 'object' && node !== null ? Reflect.get(node, part) : undefined),
    EN_TRANSLATIONS,
  );
}

describe('stats chart registry', () => {
  it('has English copy for every name, title and description', () => {
    for (const chart of [...LIBRARY_STATS_CHARTS, ...READING_STATS_CHARTS]) {
      for (const key of [chart.nameKey, chart.titleKey, ...(chart.descriptionKey ? [chart.descriptionKey] : [])]) {
        expect(typeof translation(key), key).toBe('string');
      }
    }
  });
});
