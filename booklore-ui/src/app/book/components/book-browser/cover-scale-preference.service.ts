import {inject, Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../core/service/local-storage-service';

@Injectable({
  providedIn: 'root'
})
export class CoverScalePreferenceService {

  private readonly BASE_WIDTH = 135;
  private readonly BASE_HEIGHT = 220;
  private readonly DEBOUNCE_MS = 1000;
  private readonly STORAGE_KEY = 'coverScalePreference';

  private readonly messageService = inject(MessageService);
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

  private saveScalePreference(scale: number): void {
    try {
      this.localStorageService.set(this.STORAGE_KEY, scale);
      this.messageService.add({
        severity: 'success',
        summary: 'Cover Size Saved',
        detail: `Cover size set to ${scale.toFixed(2)}x.`,
        life: 1500
      });
    } catch (e) {
      this.messageService.add({
        severity: 'error',
        summary: 'Save Failed',
        detail: 'Could not save cover size preference locally.',
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
