import {Injectable} from '@angular/core';
import {Translation, TranslocoLoader} from '@jsverse/transloco';
import {from, of, Observable} from 'rxjs';
import en from '../../../i18n/en';

export const EN_TRANSLATIONS: Translation = en;

// To add a new language: create src/i18n/<lang>/ with domain JSONs + index.ts, then add an entry here.
const LAZY_LANG_LOADERS: Record<string, () => Promise<{default: Translation}>> = {
  es: () => import('../../../i18n/es'),
  it: () => import('../../../i18n/it'),
  de: () => import('../../../i18n/de'),
  fr: () => import('../../../i18n/fr'),
  nl: () => import('../../../i18n/nl'),
  pl: () => import('../../../i18n/pl'),
  pt: () => import('../../../i18n/pt'),
  ru: () => import('../../../i18n/ru'),
  hr: () => import('../../../i18n/hr'),
  sv: () => import('../../../i18n/sv'),
  zh: () => import('../../../i18n/zh'),
  ja: () => import('../../../i18n/ja'),
  hu: () => import('../../../i18n/hu'),
  sl: () => import('../../../i18n/sl'),
  sk: () => import('../../../i18n/sk'),
  uk: () => import('../../../i18n/uk'),
  id: () => import('../../../i18n/id'),
  da: () => import('../../../i18n/da'),
};

export const AVAILABLE_LANGS = ['en', ...Object.keys(LAZY_LANG_LOADERS)];

export const LANG_LABELS: Record<string, string> = {
  en: 'English',
  es: 'Español',
  it: 'Italiano',
  de: 'Deutsch',
  fr: 'Français',
  nl: 'Nederlands',
  pl: 'Polski',
  pt: 'Português',
  ru: 'Русский',
  hr: 'Hrvatski',
  sv: 'Svenska',
  zh: '中文',
  ja: '日本語',
  hu: 'Magyar',
  sl: 'Slovenščina',
  sk: 'Slovenčina',
  uk: 'Українська',
  id: 'Bahasa Indonesia',
  da: 'Dansk',
};

function deepMerge(base: Record<string, Translation>, override: Record<string, Translation>): Record<string, Translation> {
  const result = {...base};
  for (const key of Object.keys(override)) {
    if (override[key] && typeof override[key] === 'object' && !Array.isArray(override[key])
      && base[key] && typeof base[key] === 'object' && !Array.isArray(base[key])) {
      result[key] = deepMerge(base[key], override[key]);
    } else if (override[key]) {
      result[key] = override[key];
    }
  }
  return result;
}

@Injectable({providedIn: 'root'})
export class TranslocoInlineLoader implements TranslocoLoader {
  getTranslation(lang: string): Observable<Translation> {
    if (lang === 'en') {
      return of(EN_TRANSLATIONS);
    }
    const loader = LAZY_LANG_LOADERS[lang];
    if (loader) {
      return from(loader().then(m => deepMerge(EN_TRANSLATIONS, m.default)));
    }
    return of(EN_TRANSLATIONS);
  }
}
