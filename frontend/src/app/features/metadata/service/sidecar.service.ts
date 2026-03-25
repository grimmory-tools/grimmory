import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

export interface SidecarCoverInfo {
  source: string;
  path: string;
}

export interface SidecarSeries {
  name?: string;
  number?: number;
  total?: number;
}

export interface SidecarIdentifiers {
  asin?: string;
  goodreadsId?: string;
  googleId?: string;
  hardcoverId?: string;
  comicvineId?: string;
  lubimyczytacId?: string;
  ranobedbId?: string;
  audibleId?: string;
}

export interface SidecarRating {
  average?: number;
  count?: number;
}

export interface SidecarRatings {
  amazon?: SidecarRating;
  goodreads?: SidecarRating;
  hardcover?: SidecarRating;
  lubimyczytac?: SidecarRating;
  ranobedb?: SidecarRating;
  audible?: SidecarRating;
}

export interface SidecarBookMetadata {
  title?: string;
  subtitle?: string;
  authors?: string[];
  publisher?: string;
  publishedDate?: string;
  description?: string;
  isbn10?: string;
  isbn13?: string;
  language?: string;
  pageCount?: number;
  categories?: string[];
  moods?: string[];
  tags?: string[];
  series?: SidecarSeries;
  identifiers?: SidecarIdentifiers;
  ratings?: SidecarRatings;
  ageRating?: number;
  contentRating?: string;
  narrator?: string;
  abridged?: boolean;
}

export interface SidecarMetadata {
  version: string;
  generatedAt: string;
  generatedBy: string;
  metadata: SidecarBookMetadata;
  cover?: SidecarCoverInfo;
}

export type SidecarSyncStatus = 'IN_SYNC' | 'OUTDATED' | 'MISSING' | 'CONFLICT' | 'NOT_APPLICABLE';

@Injectable({
  providedIn: 'root'
})
export class SidecarService {
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  constructor(private http: HttpClient) {}

  getSidecarContent(bookId: number): Observable<SidecarMetadata> {
    return this.http.get<SidecarMetadata>(`${this.apiUrl}/books/${bookId}/sidecar`);
  }

  getSyncStatus(bookId: number): Observable<{status: SidecarSyncStatus}> {
    return this.http.get<{status: SidecarSyncStatus}>(`${this.apiUrl}/books/${bookId}/sidecar/status`);
  }

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
