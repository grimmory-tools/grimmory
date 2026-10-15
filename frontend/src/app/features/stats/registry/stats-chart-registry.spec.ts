import {getValue} from '@jsverse/transloco';
import {describe, expect, it} from 'vitest';

import {EN_TRANSLATIONS} from '../../../core/config/transloco-loader';
import {LIBRARY_STATS_CHARTS} from './library-stats.registry';
import {READING_STATS_CHARTS} from './reading-stats.registry';

describe('stats chart registry', () => {
  it('has English copy for every name, title and description', () => {
    const keys = [...LIBRARY_STATS_CHARTS, ...READING_STATS_CHARTS]
      .flatMap(({nameKey, titleKey, descriptionKey}) => [nameKey, titleKey, descriptionKey])
      .filter((key) => key !== undefined);
    expect(keys.filter((key) => typeof getValue(EN_TRANSLATIONS, key) !== 'string')).toEqual([]);
  });
});
