import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { API_CONFIG } from '../../core/config/api-config';
import { invalidateAllBookCaches } from '../../features/book/service/legacy-book-cache';

@Injectable({ providedIn: 'root' })
export class MetadataMatchWeightsService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  private http = inject(HttpClient);
  private queryClient = inject(QueryClient);

  recalculateAll(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/books/metadata/recalculate-match-scores`, {}).pipe(
      tap(() => invalidateAllBookCaches(this.queryClient))
    );
  }
}
