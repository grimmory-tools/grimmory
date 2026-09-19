import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

@Injectable({
  providedIn: 'root'
})
export class SidecarService {
  private http = inject(HttpClient);
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  exportToSidecar(bookId: number): Observable<{message: string}> {
    return this.http.post<{message: string}>(`${this.apiUrl}/books/${bookId}/sidecar/export`, {});
  }

  importFromSidecar(bookId: number): Observable<{message: string}> {
    return this.http.post<{message: string}>(`${this.apiUrl}/books/${bookId}/sidecar/import`, {});
  }

  bulkExport(libraryId: number): Observable<{message: string, exported: number}> {
    return this.http.post<{message: string, exported: number}>(
      `${this.apiUrl}/libraries/${libraryId}/sidecar/export-all`, {}
    );
  }

  bulkImport(libraryId: number): Observable<{message: string, imported: number}> {
    return this.http.post<{message: string, imported: number}>(
      `${this.apiUrl}/libraries/${libraryId}/sidecar/import-all`, {}
    );
  }
}
