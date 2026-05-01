import {computed, inject, Injectable} from '@angular/core';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {ScalePreference} from '../../../../shared/util/scale-preference.util';

@Injectable({
  providedIn: 'root'
})
export class CoverScalePreferenceService {

  private readonly BASE_WIDTH = 135;
  private readonly BASE_HEIGHT = 220;
  private readonly STORAGE_KEY = 'coverScalePreference';
  private readonly MIN_SCALE = 0.5;
  private readonly MAX_SCALE = 1.5;

  private readonly localStorageService = inject(LocalStorageService);
  private readonly scalePreference = new ScalePreference(this.localStorageService, {
    storageKey: this.STORAGE_KEY,
    minScale: this.MIN_SCALE,
    maxScale: this.MAX_SCALE,
  });
  readonly scaleFactor = this.scalePreference.scaleFactor;

  readonly currentCardSize = computed(() => ({
    width: Math.round(this.BASE_WIDTH * this.scaleFactor()),
    height: Math.round(this.BASE_HEIGHT * this.scaleFactor()),
  }));

  setScale(scale: number): void {
    this.scalePreference.setScale(scale);
  }
}
