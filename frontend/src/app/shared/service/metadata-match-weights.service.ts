import { inject, Service } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { API_CONFIG } from '../../core/config/api-config';
import { invalidateAllBookCaches } from '../../features/book/service/legacy-book-cache';

@Service()
export class MetadataMatchWeightsService {
  private readonly http = inject(HttpClient);
  private readonly queryClient = inject(QueryClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  recalculateAll(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/books/metadata/recalculate-match-scores`, {}).pipe(
      tap(() => invalidateAllBookCaches(this.queryClient))
    );
  }
}
