import {DOCUMENT, isPlatformBrowser} from '@angular/common';
import {inject, Injectable, PLATFORM_ID, signal} from '@angular/core';

import {DEFAULT_UI_FONT, normalizeUiFont, type UiFontPreference} from '../model/ui-font.model';

const STORAGE_KEY = 'appUiFont';

@Injectable({providedIn: 'root'})
export class AppUiFontService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly uiFontSignal = signal<UiFontPreference>(DEFAULT_UI_FONT);

  readonly uiFont = this.uiFontSignal.asReadonly();

  constructor() {
    this.applyUiFont(this.getStoredUiFont());
  }

  private getStoredUiFont(): UiFontPreference {
    if (!isPlatformBrowser(this.platformId)) {
      return DEFAULT_UI_FONT;
    }

    return normalizeUiFont(localStorage.getItem(STORAGE_KEY));
  }

  applyUiFont(uiFont: string | null | undefined): void {
    const nextUiFont = normalizeUiFont(uiFont);
    this.uiFontSignal.set(nextUiFont);
    this.applyRootAttribute(nextUiFont);

    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(STORAGE_KEY, nextUiFont);
    }
  }

  private applyRootAttribute(uiFont: UiFontPreference): void {
    const root = this.document.documentElement;
    if (uiFont === DEFAULT_UI_FONT) {
      delete root.dataset['uiFont'];
      return;
    }

    root.dataset['uiFont'] = uiFont;
  }
}
