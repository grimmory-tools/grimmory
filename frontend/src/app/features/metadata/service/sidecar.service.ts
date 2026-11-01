import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {invalidateAllBookCaches, invalidateBooksById} from '../../book/service/legacy-book-cache';

@Service()
export class SidecarService {
  private readonly http = inject(HttpClient);
  private readonly queryClient = inject(QueryClient);
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  exportToSidecar(bookId: number): Observable<{message: string}> {
    return this.http.post<{message: string}>(`${this.apiUrl}/books/${bookId}/sidecar/export`, {});
  }

  importFromSidecar(bookId: number): Observable<{message: string}> {
    return this.http.post<{message: string}>(`${this.apiUrl}/books/${bookId}/sidecar/import`, {}).pipe(
      tap(() => {
        invalidateBooksById(this.queryClient, [bookId]);
      })
    );
  }

  bulkExport(libraryId: number): Observable<{message: string, exported: number}> {
    return this.http.post<{message: string, exported: number}>(
      `${this.apiUrl}/libraries/${libraryId}/sidecar/export-all`, {}
    );
  }

  bulkImport(libraryId: number): Observable<{message: string, imported: number}> {
    return this.http.post<{message: string, imported: number}>(
      `${this.apiUrl}/libraries/${libraryId}/sidecar/import-all`, {}
    ).pipe(
      tap(() => {
        invalidateAllBookCaches(this.queryClient);
      })
    );
  }
}
