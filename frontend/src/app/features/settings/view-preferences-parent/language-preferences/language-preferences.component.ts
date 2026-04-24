import {Component, DestroyRef, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Select} from 'primeng/select';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {AVAILABLE_LANGS, LANG_LABELS} from '../../../../core/config/transloco-loader';
import {LANG_STORAGE_KEY} from '../../../../core/config/language-initializer';

@Component({
  selector: 'app-language-preferences',
  standalone: true,
  imports: [FormsModule, Select, TranslocoDirective],
  templateUrl: './language-preferences.component.html',
  styleUrl: './language-preferences.component.scss',
})
export class LanguagePreferencesComponent {
  readonly languageOptions = AVAILABLE_LANGS.map(value => ({
    value,
    label: LANG_LABELS[value] ?? value,
  }));
  activeLang: string;

  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.activeLang = this.t.getActiveLang();
  }

  onLanguageChange(lang: string): void {
    if (lang === this.activeLang) return;
    const previous = this.activeLang;
    this.t.load(lang).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.t.setActiveLang(lang);
        localStorage.setItem(LANG_STORAGE_KEY, lang);
        this.activeLang = lang;
      },
      error: () => {
        // Force the p-select to snap back by briefly invalidating the binding.
        this.activeLang = '';
        queueMicrotask(() => (this.activeLang = previous));
      },
    });
  }
}
