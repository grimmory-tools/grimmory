import {Signal, WritableSignal, signal} from '@angular/core';
import {LocalStorageService} from '../service/local-storage.service';

interface ScalePreferenceConfig {
  storageKey: string;
  minScale: number;
  maxScale: number;
  defaultScale?: number;
}

export class ScalePreference {
  private readonly _scaleFactor: WritableSignal<number>;
  readonly scaleFactor: Signal<number>;
  private lastPersistedScale: number;

  constructor(
    private readonly localStorageService: LocalStorageService,
    private readonly config: ScalePreferenceConfig
  ) {
    const initialScale = this.loadScalePreference();
    this._scaleFactor = signal(initialScale);
    this.scaleFactor = this._scaleFactor.asReadonly();
    this.lastPersistedScale = initialScale;
  }

  setScale(scale: number): void {
    const normalizedScale = this.clampScale(scale);
    this._scaleFactor.set(normalizedScale);
    if (normalizedScale === this.lastPersistedScale) {
      return;
    }
    this.saveScalePreference(normalizedScale);
  }

  private loadScalePreference(): number {
    const saved = this.localStorageService.get<number>(this.config.storageKey);
    if (typeof saved === 'number' && !Number.isNaN(saved)) {
      const clamped = this.clampScale(saved);
      if (clamped !== saved) {
        this.localStorageService.set(this.config.storageKey, clamped);
      }
      return clamped;
    }
    return this.defaultScale;
  }

  private saveScalePreference(scale: number): void {
    this.localStorageService.set(this.config.storageKey, scale);
    this.lastPersistedScale = scale;
  }

  private clampScale(scale: number): number {
    if (Number.isNaN(scale)) {
      return this.defaultScale;
    }
    return Math.min(this.config.maxScale, Math.max(this.config.minScale, scale));
  }

  private get defaultScale(): number {
    return this.config.defaultScale ?? 1.0;
  }
}
