import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {BookMetadata} from '../../book/model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';

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
  page: {
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };
}

export interface PatternExtractRequest {
  pattern: string;
  selectAll?: boolean;
  excludedIds?: number[];
  selectedIds?: number[];
  preview?: boolean;
}

export interface FileExtractionResult {
  fileId: number;
  fileName: string;
  success: boolean;
  extractedMetadata?: BookMetadata;
  errorMessage?: string;
}

export interface PatternExtractResult {
  totalFiles: number;
  successfullyExtracted: number;
  failed: number;
  results: FileExtractionResult[];
}

export interface BulkEditRequest {
  fields: Partial<BookMetadata>;
  enabledFields: string[];
  mergeArrays: boolean;
  selectAll?: boolean;
  excludedIds?: number[];
  selectedIds?: number[];
}

export interface BulkEditResult {
  totalFiles: number;
  successfullyUpdated: number;
  failed: number;
}

@Injectable({providedIn: 'root'})
export class BookdropService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/bookdrop`;
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

  rescan(): Observable<void> {
    return this.http.post<void>(`${this.url}/rescan`, {});
  }

  extractFromPattern(payload: PatternExtractRequest): Observable<PatternExtractResult> {
    return this.http.post<PatternExtractResult>(`${this.url}/files/extract-pattern`, payload);
  }

  bulkEditMetadata(payload: BulkEditRequest): Observable<BulkEditResult> {
    return this.http.post<BulkEditResult>(`${this.url}/files/bulk-edit`, payload);
  }
}
