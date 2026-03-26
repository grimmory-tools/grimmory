import {computed, inject, Injectable, signal} from '@angular/core';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {Book} from '../../model/book.model';

@Injectable({
  providedIn: 'root'
})
export class CoverScalePreferenceService {

  private readonly BASE_WIDTH = 135;
  private readonly BASE_HEIGHT = 220;
  private readonly TITLE_BAR_HEIGHT = 31;
  private readonly DEBOUNCE_MS = 1000;
  private readonly STORAGE_KEY = 'coverScalePreference';

  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly localStorageService = inject(LocalStorageService);

  private readonly _scaleFactor = signal(1.0);
  readonly scaleFactor = this._scaleFactor.asReadonly();

  readonly currentCardSize = computed(() => ({
    width: Math.round(this.BASE_WIDTH * this.scaleFactor()),
    height: Math.round(this.BASE_HEIGHT * this.scaleFactor()),
  }));
  readonly gridColumnMinWidth = computed(() => `${this.currentCardSize().width}px`);

  private saveTimeout: ReturnType<typeof setTimeout> | null = null;
  private lastPersistedScale = 1.0;

  constructor() {
    const initialScale = this.loadScaleFromStorage();
    this._scaleFactor.set(initialScale);
    this.lastPersistedScale = initialScale;
  }

  setScale(scale: number): void {
    this._scaleFactor.set(scale);
    if (scale === this.lastPersistedScale) {
      return;
    }
    this.scheduleSave(scale);
  }

  getCardHeight(_book: Book): number {
    void _book;
    // Use uniform height for all book types to ensure smooth virtual scrolling.
    // Mixed heights cause choppy/jumpy scrolling because the virtual scroller
    // cannot accurately estimate positions when item heights vary.
    return this.currentCardSize().height;
  }

  private scheduleSave(scale: number): void {
    if (this.saveTimeout) {
      clearTimeout(this.saveTimeout);
    }

    this.saveTimeout = setTimeout(() => {
      this.saveScalePreference(scale);
    }, this.DEBOUNCE_MS);
  }

  private saveScalePreference(scale: number): void {
    try {
      this.localStorageService.set(this.STORAGE_KEY, scale);
      this.lastPersistedScale = scale;
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('book.coverPref.toast.savedSummary'),
        detail: this.t.translate('book.coverPref.toast.savedDetail', {scale: scale.toFixed(2)}),
        life: 1500
      });
    } catch {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('book.coverPref.toast.saveFailedSummary'),
        detail: this.t.translate('book.coverPref.toast.saveFailedDetail'),
        life: 3000
      });
    }
  }

  private loadScaleFromStorage(): number {
    const saved = this.localStorageService.get<number>(this.STORAGE_KEY);
    if (saved !== null && !isNaN(saved)) {
      return saved;
    }
    return 1.0;
  }
}
