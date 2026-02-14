import {inject, Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';
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

  private readonly scaleChangeSubject = new Subject<number>();
  readonly scaleChange$ = this.scaleChangeSubject.asObservable();

  scaleFactor = 1.0;

  constructor() {
    this.loadScaleFromStorage();

    this.scaleChange$
      .pipe(debounceTime(this.DEBOUNCE_MS))
      .subscribe(scale => this.saveScalePreference(scale));
  }

  initScaleValue(scale: number | undefined): void {
    this.scaleFactor = scale ?? 1.0;
  }

  setScale(scale: number): void {
    this.scaleFactor = scale;
    this.scaleChangeSubject.next(scale);
  }

  get currentCardSize(): { width: number; height: number } {
    return {
      width: Math.round(this.BASE_WIDTH * this.scaleFactor),
      height: Math.round(this.BASE_HEIGHT * this.scaleFactor),
    };
  }

  get gridColumnMinWidth(): string {
    return `${this.currentCardSize.width}px`;
  }

  getCardHeight(_book: Book): number {
    // Use uniform height for all book types to ensure smooth virtual scrolling.
    // Mixed heights cause choppy/jumpy scrolling because the virtual scroller
    // cannot accurately estimate positions when item heights vary.
    return this.currentCardSize.height;
  }

  private saveScalePreference(scale: number): void {
    try {
      this.localStorageService.set(this.STORAGE_KEY, scale);
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('book.coverPref.toast.savedSummary'),
        detail: this.t.translate('book.coverPref.toast.savedDetail', {scale: scale.toFixed(2)}),
        life: 1500
      });
    } catch (e) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('book.coverPref.toast.saveFailedSummary'),
        detail: this.t.translate('book.coverPref.toast.saveFailedDetail'),
        life: 3000
      });
    }
  }

  private loadScaleFromStorage(): void {
    const saved = this.localStorageService.get<number>(this.STORAGE_KEY);
    if (saved !== null && !isNaN(saved)) {
      this.scaleFactor = saved;
    }
  }
}
