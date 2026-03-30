import {TranslocoTestingModule} from '@jsverse/transloco';
import {describe, expect, it} from 'vitest';

import {getTranslocoModule} from './transloco-testing';

describe('getTranslocoModule', () => {
  it('returns a Transloco testing module wrapper', () => {
    const moduleWithProviders = getTranslocoModule();

    expect(moduleWithProviders.ngModule).toBe(TranslocoTestingModule);
    expect(moduleWithProviders.providers?.length ?? 0).toBeGreaterThan(0);
  });

  it('creates a fresh module wrapper when callers provide overrides', () => {
    const baseModule = getTranslocoModule();
    const customModule = getTranslocoModule({
      langs: {
        fr: {
          custom: {
            greeting: 'Bonjour',
          }
        },
      },
      translocoConfig: {
        availableLangs: ['en', 'fr'],
        defaultLang: 'fr',
      },
    });

    expect(customModule.ngModule).toBe(TranslocoTestingModule);
    expect(customModule).not.toBe(baseModule);
  });
});
