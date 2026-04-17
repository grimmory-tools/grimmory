import {Injectable, inject, Renderer2} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {CacheStorageService} from '../../../../../shared/service/cache-storage.service';
import {LocalSettingsService} from '../../../../../shared/service/local-settings.service';
import {CustomFontService} from '../../../../../shared/service/custom-font.service';
import {CustomFont} from '../../../../../shared/model/custom-font.model';
import {Observable, forkJoin, of, from} from 'rxjs';
import {map, switchMap, tap, catchError} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class EpubCustomFontService {
  private http = inject(HttpClient);
  private customFontService = inject(CustomFontService);
  private cacheStorageService = inject(CacheStorageService);
  private localSettingsService = inject(LocalSettingsService);
  private customFonts: CustomFont[] = [];
  private customFontBlobUrls = new Map<number, string>();

  loadAndCacheFonts(): Observable<CustomFont[]> {
    return from(this.customFontService.ensureFonts()).pipe(
      tap(fonts => this.customFonts = fonts),
      switchMap(fonts =>
        from(this.customFontService.loadAllFonts(fonts)).pipe(
          switchMap(() => this.cacheCustomFontsAsBlobs(fonts)),
          map(() => fonts)
        )
      ),
      catchError(() => of([]))
    );
  }

  private cacheCustomFontsAsBlobs(fonts: CustomFont[]): Observable<void> {
    if (fonts.length === 0) {
      return of(undefined);
    }

    const requests = fonts.map(font =>
      this.cacheSingleFont(font).pipe(
        catchError(() => of(undefined))
      )
    );

    return forkJoin(requests).pipe(map(() => undefined));
  }

  private cacheSingleFont(font: CustomFont): Observable<void> {
    const fontUrl = this.customFontService.getFontUrl(font.id);
    let obs$: Observable<Blob>;
    
    if (this.localSettingsService.get().cacheStorageEnabled) {
      obs$ = from(this.cacheStorageService.getCache(fontUrl)).pipe(
        switchMap(res => res.blob())
      );
    } else {
      obs$ = this.http.get(fontUrl, {responseType: 'blob'});
    }

    return obs$.pipe(
      tap(blob => {
        const blobUrl = URL.createObjectURL(blob);
        this.customFontBlobUrls.set(font.id, blobUrl);
      }),
      map(() => undefined)
    );
  }

  getBlobUrl(fontId: number): string | undefined {
    return this.customFontBlobUrls.get(fontId);
  }

  getCustomFonts(): CustomFont[] {
    return this.customFonts;
  }

  getCustomFontById(fontId: number): CustomFont | undefined {
    return this.customFonts.find(f => f.id === fontId);
  }

  cleanup(): void {
    this.customFontBlobUrls.forEach(url => URL.revokeObjectURL(url));
    this.customFontBlobUrls.clear();
  }

  sanitizeFontName(fontName: string): string {
    return fontName.replace(/\.(ttf|otf|woff|woff2)$/i, '').replace(/["'()]/g, '').replace(/\s+/g, '-');
  }

  getFontFamilyForPreview(fontValue: string): string {
    if (fontValue.startsWith('custom:')) {
      const id = parseInt(fontValue.substring(7), 10);
      if (!isNaN(id)) {
        const customFont = this.getCustomFontById(id);
        if (customFont) {
          return `"${this.sanitizeFontName(customFont.fontName)}", sans-serif`;
        }
      }
    }
    return fontValue;
  }

  generateCustomFontsStylesheet(): string {
    const customFonts = this.getCustomFonts();
    if (customFonts.length === 0) return '';

    let css = '';
    customFonts.forEach(font => {
      const blobUrl = this.getBlobUrl(font.id);
      if (blobUrl) {
        const sanitizedName = this.sanitizeFontName(font.fontName);
        css += `
          @font-face {
            font-family: "${sanitizedName}";
            src: url("${blobUrl}") format("truetype");
            font-weight: normal;
            font-style: normal;
            font-display: swap;
          }
        `;
      }
    });

    return css;
  }

  injectCustomFontsStylesheet(renderer: Renderer2, document: Document): void {
    const css = this.generateCustomFontsStylesheet();
    if (css) {
      const styleEl = renderer.createElement('style');
      styleEl.textContent = css;
      renderer.appendChild(document.head, styleEl);
    }
  }
}
