import {TranslocoTestingModule, TranslocoTestingOptions} from '@jsverse/transloco';
import {EN_TRANSLATIONS} from '../config/transloco-loader';

export function getTranslocoModule(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: {en: EN_TRANSLATIONS, ...options.langs},
    translocoConfig: {
      availableLangs: ['en'],
      defaultLang: 'en',
      ...options.translocoConfig,
    },
    ...options,
  });
}
