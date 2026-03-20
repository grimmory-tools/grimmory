import {inject, Injectable, signal} from '@angular/core';
import {LocalStorageService} from '../../../shared/service/local-storage.service';

@Injectable({
  providedIn: 'root'
})
export class AuthorScalePreferenceService {

  private readonly DEBOUNCE_MS = 1000;
  private readonly STORAGE_KEY = 'authorScalePreference';

  private readonly localStorageService = inject(LocalStorageService);

  private readonly _scaleFactor = signal(1.0);
  readonly scaleFactor = this._scaleFactor.asReadonly();

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

  private scheduleSave(scale: number): void {
    if (this.saveTimeout) {
      clearTimeout(this.saveTimeout);
    }

    this.saveTimeout = setTimeout(() => {
      this.saveScalePreference(scale);
    }, this.DEBOUNCE_MS);
  }

  private saveScalePreference(scale: number): void {
    this.localStorageService.set(this.STORAGE_KEY, scale);
    this.lastPersistedScale = scale;
  }

  private loadScaleFromStorage(): number {
    const saved = this.localStorageService.get<number>(this.STORAGE_KEY);
    if (saved !== null && !isNaN(saved)) {
      return saved;
    }
    return 1.0;
  }
}
