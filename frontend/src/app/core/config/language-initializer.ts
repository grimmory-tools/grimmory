import {inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {firstValueFrom} from 'rxjs';
import {AVAILABLE_LANGS} from './transloco-loader';

// TODO(grimmory-cleanup): Remove after the Booklore-to-Grimmory localStorage migration window closes.
export const LEGACY_LANG_STORAGE_KEY = 'booklore-lang';
export const LANG_STORAGE_KEY = 'grimmory-lang';

function detectLanguage(available: string[]): string {
  const saved = localStorage.getItem(LANG_STORAGE_KEY) ?? localStorage.getItem(LEGACY_LANG_STORAGE_KEY);
  if (saved && available.includes(saved)) {
    localStorage.setItem(LANG_STORAGE_KEY, saved);
    return saved;
  }

  const browserLocale = navigator.language;
  if (browserLocale && available.includes(browserLocale)) {
    return browserLocale;
  }

  const baseLang = browserLocale?.split('-')[0];
  if (baseLang && available.includes(baseLang)) {
    return baseLang;
  }

  return 'en';
}

export function initializeLanguage() {
  return () => {
    const translocoService = inject(TranslocoService);
    const lang = detectLanguage(AVAILABLE_LANGS);
    translocoService.setActiveLang(lang);
    localStorage.setItem(LANG_STORAGE_KEY, lang);
    return firstValueFrom(translocoService.load(lang));
  };
}
