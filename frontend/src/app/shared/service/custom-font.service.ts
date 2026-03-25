import {computed, effect, inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom, Observable, tap} from 'rxjs';
import {CustomFont} from '../model/custom-font.model';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

const CUSTOM_FONTS_QUERY_KEY = ['customFonts'] as const;

@Injectable({
  providedIn: 'root'
})
export class CustomFontService {
  private apiUrl = `${API_CONFIG.BASE_URL}/api/v1/custom-fonts`;
  private loadedFonts = new Set<string>();
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private queryClient = inject(QueryClient);
  private readonly token = this.authService.token;

  private fontsQuery = injectQuery(() => ({
    ...this.getFontsQueryOptions(),
    enabled: !!this.token(),
  }));

  fonts = computed(() => this.fontsQuery.data() ?? []);
  isFontsLoading = computed(() => !!this.token() && this.fontsQuery.isPending());

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: CUSTOM_FONTS_QUERY_KEY});
      }
    });
  }

  private getFontsQueryOptions() {
    return queryOptions({
      queryKey: CUSTOM_FONTS_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<CustomFont[]>(this.apiUrl))
    });
  }

  ensureFonts(): Promise<CustomFont[]> {
    return this.queryClient.ensureQueryData(this.getFontsQueryOptions());
  }

  uploadFont(file: File, fontName?: string): Observable<CustomFont> {
    const formData = new FormData();
    formData.append('file', file);
    if (fontName) {
      formData.append('fontName', fontName);
    }

    return this.http.post<CustomFont>(`${this.apiUrl}/upload`, formData).pipe(
      tap(font => {
        this.queryClient.setQueryData<CustomFont[]>(CUSTOM_FONTS_QUERY_KEY, current =>
          [...(current ?? []), font]
        );
        this.loadFontFace(font).catch(err => {
          console.error('Failed to load font after upload:', err);
        });
      })
    );
  }

  deleteFont(fontId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${fontId}`).pipe(
      tap(() => {
        const currentFonts = this.fonts();
        const deletedFont = currentFonts.find(f => f.id === fontId);

        this.queryClient.setQueryData<CustomFont[]>(CUSTOM_FONTS_QUERY_KEY, current =>
          (current ?? []).filter(f => f.id !== fontId)
        );

        if (deletedFont) {
          this.removeFontFace(deletedFont.fontName);
          this.loadedFonts.delete(deletedFont.fontName);
        }
      })
    );
  }

  getFontUrl(fontId: number): string {
    return `${this.apiUrl}/${fontId}/file`;
  }

  private getToken(): string | null {
    return this.authService.getInternalAccessToken();
  }

  public appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  async loadFontFace(font: CustomFont): Promise<void> {
    if (this.loadedFonts.has(font.fontName)) {
      return;
    }

    try {
      const absoluteFontUrl = this.getFontUrl(font.id);
      const fontUrlWithToken = this.appendToken(absoluteFontUrl);

      const fontFace = new FontFace(
        font.fontName,
        `url(${fontUrlWithToken})`,
        {
          weight: 'normal',
          style: 'normal'
        }
      );

      await fontFace.load();
      document.fonts.add(fontFace);
      this.loadedFonts.add(font.fontName);
    } catch (error) {
      console.error(`Failed to load font ${font.fontName}:`, error);
      throw error;
    }
  }

  async loadAllFonts(fonts: CustomFont[]): Promise<void> {
    const loadPromises = fonts.map(font => this.loadFontFace(font));
    await Promise.allSettled(loadPromises);
  }

  isFontLoaded(fontName: string): boolean {
    return this.loadedFonts.has(fontName);
  }

  private removeFontFace(fontName: string): void {
    for (const font of document.fonts) {
      if (font.family === fontName) {
        document.fonts.delete(font);
      }
    }
  }
}
