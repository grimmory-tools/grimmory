import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {Observable, of, tap} from 'rxjs';
import {API_CONFIG} from '../../../../core/config/api-config';
import {AuthService} from '../../../../shared/service/auth.service';
import {CbxPageDimension} from '../models/cbx-page-dimension.model';

export type DoublePairs = Record<number, number>;

export interface WebtoonDetectionResult {
  isWebtoon: boolean;
  score: number;
}

@Injectable({providedIn: 'root'})
export class CbxPageDimensionService {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/cbx`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  private dimensionCache = new Map<string, CbxPageDimension[]>();

  private getToken(): string | null {
    return this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getPageDimensions(bookId: number, bookType?: string): Observable<CbxPageDimension[]> {
    const cacheKey = `${bookId}-${bookType ?? 'default'}`;
    const cached = this.dimensionCache.get(cacheKey);
    if (cached) {
      return of(cached);
    }
    let url = `${this.baseUrl}/${bookId}/page-dimensions`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<CbxPageDimension[]>(this.appendToken(url)).pipe(
      tap(dims => this.dimensionCache.set(cacheKey, dims))
    );
  }

  clearCache(bookId?: number): void {
    if (bookId !== undefined) {
      for (const key of this.dimensionCache.keys()) {
        if (key.startsWith(`${bookId}-`)) {
          this.dimensionCache.delete(key);
        }
      }
    } else {
      this.dimensionCache.clear();
    }
  }

  /**
   * Compute double-page pair map from dimensions.
   * Cover (page index 0) is always solo. Wide pages are always solo.
   * Returns a map of pageIndex → paired pageIndex for the right-hand page.
   */
  computeDoublePairs(dimensions: CbxPageDimension[]): DoublePairs {
    const pairs: DoublePairs = {};
    let i = 1; // Skip cover (index 0), it's always solo
    while (i < dimensions.length) {
      const current = dimensions[i];
      const next = i + 1 < dimensions.length ? dimensions[i + 1] : null;

      if (current.wide) {
        // Wide page is always solo
        i++;
        continue;
      }

      if (next && !next.wide) {
        // Pair these two non-wide pages
        pairs[i] = i + 1;
        i += 2;
      } else {
        // Next is wide or doesn't exist — current is solo
        i++;
      }
    }
    return pairs;
  }

  /**
   * Detect if the content is webtoon-style (long vertical strips).
   *
   * Scoring per page:
   * - Aspect ratio (h/w) ≥ 2.2: +1.0 (strong webtoon indicator)
   * - Aspect ratio 1.8–2.2: +0.5
   * - Aspect ratio 1.5–1.8: +0.2
   * - Aspect ratio < 1.2: −0.5 (penalize square/landscape)
   * - Width ≤ 750px: +0.2
   * - Height > 2000px: +0.5 (or > 1500px: +0.3)
   * - Area > 1,500,000 px²: +0.3
   *
   * Final decision: avgScore ≥ 0.7 AND one of:
   * - 40%+ pages have ratio ≥ 2.2
   * - Average ratio ≥ 2.0
   * - Width variation < 15% AND average ratio > 1.8
   */
  detectWebtoon(dimensions: CbxPageDimension[]): WebtoonDetectionResult {
    if (dimensions.length < 3) {
      return {isWebtoon: false, score: 0};
    }

    let totalScore = 0;
    let highRatioCount = 0;
    let totalRatio = 0;
    const widths: number[] = [];

    for (const dim of dimensions) {
      if (dim.width === 0 || dim.height === 0) continue;

      const ratio = dim.height / dim.width;
      totalRatio += ratio;
      widths.push(dim.width);

      let pageScore = 0;

      // Aspect ratio scoring
      if (ratio >= 2.2) {
        pageScore += 1.0;
        highRatioCount++;
      } else if (ratio >= 1.8) {
        pageScore += 0.5;
      } else if (ratio >= 1.5) {
        pageScore += 0.2;
      } else if (ratio < 1.2) {
        pageScore -= 0.5;
      }

      // Narrow width bonus
      if (dim.width <= 750) {
        pageScore += 0.2;
      }

      // Tall page bonus
      if (dim.height > 2000) {
        pageScore += 0.5;
      } else if (dim.height > 1500) {
        pageScore += 0.3;
      }

      // Large area bonus
      if (dim.width * dim.height > 1_500_000) {
        pageScore += 0.3;
      }

      totalScore += pageScore;
    }

    const validCount = widths.length;
    if (validCount < 3) {
      return {isWebtoon: false, score: 0};
    }

    const avgScore = totalScore / validCount;
    const avgRatio = totalRatio / validCount;
    const avgWidth = widths.reduce((a, b) => a + b, 0) / validCount;
    const widthStdDev = Math.sqrt(
      widths.reduce((sum, w) => sum + Math.pow(w - avgWidth, 2), 0) / validCount
    );
    const widthVariation = avgWidth > 0 ? widthStdDev / avgWidth : 1;

    // Reject traditional comics with small, square-ish pages
    const avgHeight = dimensions
      .filter(d => d.height > 0)
      .reduce((sum, d) => sum + d.height, 0) / validCount;
    if (avgHeight < 1200 && avgRatio < 1.7 && avgWidth < 700) {
      return {isWebtoon: false, score: avgScore};
    }

    const highRatioPercent = highRatioCount / validCount;
    const isWebtoon = avgScore >= 0.7 && (
      highRatioPercent >= 0.4 ||
      avgRatio >= 2.0 ||
      (widthVariation < 0.15 && avgRatio > 1.8)
    );

    return {isWebtoon, score: avgScore};
  }
}
