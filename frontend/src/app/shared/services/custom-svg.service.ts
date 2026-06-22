import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { finalize, map, shareReplay, tap } from 'rxjs/operators';
import { API_CONFIG } from '../../core/config/api-config';
import { CustomSvgCacheService } from './custom-svg-cache.service';
import DOMPurify from 'dompurify';

interface SvgIconData {
  svgName: string;
  svgData: string;
}

interface IconSaveResult {
  iconName: string;
  success: boolean;
  errorMessage: string;
}

interface SvgIconBatchResponse {
  totalRequested: number;
  successCount: number;
  failureCount: number;
  results: IconSaveResult[];
}

@Injectable({
  providedIn: 'root'
})
export class CustomSvgService {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/icons`;
  private readonly requestCache = new Map<string, Observable<string>>();

  private readonly http = inject(HttpClient);
  private readonly customSvgCache = inject(CustomSvgCacheService);

  private sanitizeSvgContent(content: string): string {
    return DOMPurify.sanitize(content, {
      USE_PROFILES: { svg: true },
      FORBID_TAGS: ['script', 'style', 'foreignObject']
    });
  }

  getIconNames(page: number = 0, size: number = 1000): Observable<string[]> {
    return this.http.get<{ content: string[] }>(`${this.baseUrl}?page=${page}&size=${size}`).pipe(
      map(response => response.content)
    );
  }

  getSvgIconContent(iconName: string): Observable<string> {
    const cached = this.customSvgCache.getCachedSanitized(iconName);
    if (cached !== null) {
      return of('');
    }

    if (!this.requestCache.has(iconName)) {
      const request$ = this.http.get(`${this.baseUrl}/${encodeURIComponent(iconName)}/content`, {
        responseType: 'text'
      }).pipe(
        tap(content => {
          const sanitized = this.sanitizeSvgContent(content);
          this.customSvgCache.cacheIcon(iconName, content, sanitized);
        }),
        shareReplay({ bufferSize: 1, refCount: true }),
        finalize(() => this.requestCache.delete(iconName))
      );

      this.requestCache.set(iconName, request$);
    }

    return this.requestCache.get(iconName)!;
  }

  getSanitizedSvgContent(iconName: string): Observable<string> {
    const cached = this.customSvgCache.getCachedSanitized(iconName);
    if (cached !== null) {
      return of(cached);
    }

    return new Observable<string>(observer => {
      this.getSvgIconContent(iconName).subscribe({
        next: () => {
          const sanitized = this.customSvgCache.getCachedSanitized(iconName);
          if (sanitized !== null) {
            observer.next(sanitized);
            observer.complete();
          }
        },
        error: (err) => observer.error(err)
      });
    });
  }

  deleteSvgIcon(svgName: string): Observable<unknown> {
    return this.http.delete(`${this.baseUrl}/${encodeURIComponent(svgName)}`).pipe(
      tap(() => {
        this.customSvgCache.removeIcon(svgName);
        this.requestCache.delete(svgName);
      })
    );
  }

  saveBatchSvgIcons(icons: SvgIconData[]): Observable<SvgIconBatchResponse> {
    return this.http.post<SvgIconBatchResponse>(`${this.baseUrl}/batch`, { icons }).pipe(
      tap((response) => {
        response.results.forEach(result => {
          if (result.success) {
            const iconData = icons.find(icon => icon.svgName === result.iconName);
            if (iconData) {
              const sanitized = this.sanitizeSvgContent(iconData.svgData);
              this.customSvgCache.cacheIcon(iconData.svgName, iconData.svgData, sanitized);
            }
          }
        });
      })
    );
  }
}
