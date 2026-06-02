import {Injectable, inject} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {firstValueFrom} from 'rxjs';
import {AVAILABLE_LANGS} from '../../core/config/transloco-loader';

const DEFAULT_LOCALE = 'en';
const STORAGE_KEY = 'appLocale';

@Injectable({providedIn: 'root'})
export class AppLocaleService {
  private translocoService = inject(TranslocoService);

  getDisplayLocale(): string {
    const locale = localStorage.getItem(STORAGE_KEY);
    return locale && AVAILABLE_LANGS.includes(locale) ? locale : DEFAULT_LOCALE;
  }

  async applyLocale(locale: string): Promise<void> {
    const nextLocale = AVAILABLE_LANGS.includes(locale) ? locale : DEFAULT_LOCALE;
    await firstValueFrom(this.translocoService.load(nextLocale));
    this.translocoService.setActiveLang(nextLocale);
    localStorage.setItem(STORAGE_KEY, nextLocale);
  }
}
