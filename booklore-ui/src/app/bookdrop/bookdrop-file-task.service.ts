import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {BookMetadata} from '../book/model/book.model';
import {API_CONFIG} from '../config/api-config';

export enum BookdropFileStatus {
  ACCEPTED = 'ACCEPTED',
  REJECTED = 'REJECTED',
}

export interface BookdropFinalizePayload {
  selectAll?: boolean;
  excludedIds?: number[];
  defaultLibraryId?: number;
  defaultPathId?: number;
  files?: {
    fileId: number;
    libraryId: number;
    pathId: number;
    metadata: BookMetadata;
  }[];
}

export interface BookdropFile {
  showDetails: boolean;
  id: number;
  fileName: string;
  filePath: string;
  fileSize: number;
  originalMetadata?: BookMetadata;
  fetchedMetadata?: BookMetadata;
  createdAt: string;
  updatedAt: string;
  status: BookdropFileStatus;
}

export interface BookdropFileResult {
  fileName: string;
  success: boolean;
  message: string;
}

export interface BookdropFinalizeResult {
  totalFiles: number;
  successfullyImported: number;
  failed: number;
  processedAt: string;
  results: BookdropFileResult[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Injectable({providedIn: 'root'})
export class BookdropFileTaskService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/bookdrop`;
  private http = inject(HttpClient);

  getPendingFiles(page: number = 0, size: number = 50): Observable<Page<BookdropFile>> {
    return this.http.get<Page<BookdropFile>>(`${this.url}/files?status=pending&page=${page}&size=${size}`);
  }

  finalizeImport(payload: BookdropFinalizePayload): Observable<BookdropFinalizeResult> {
    return this.http.post<BookdropFinalizeResult>(`${this.url}/imports/finalize`, payload);
  }

  discardFiles(payload: { selectAll: boolean; excludedIds?: number[]; selectedIds?: number[] }): Observable<void> {
    return this.http.post<void>(`${this.url}/files/discard`, payload);
  }
}
