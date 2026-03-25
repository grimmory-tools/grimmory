import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, map, tap} from 'rxjs/operators';
import {Book, BookMetadata, BulkMetadataUpdateRequest, MetadataUpdateWrapper} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {invalidateBookQueries, invalidateBooksQuery, patchBookInCacheWith, patchBookMetadataInCache} from './book-query-cache';

@Injectable({
  providedIn: 'root',
})
export class BookMetadataManageService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);
  private readonly t = inject(TranslocoService);

  updateBookMetadata(bookId: number | undefined, wrapper: MetadataUpdateWrapper, mergeCategories: boolean, replaceMode: 'REPLACE_ALL' | 'REPLACE_WHEN_PROVIDED' = 'REPLACE_ALL'): Observable<BookMetadata> {
    const params = new HttpParams().set('mergeCategories', mergeCategories.toString()).set('replaceMode', replaceMode);
    return this.http.put<BookMetadata>(`${this.url}/${bookId}/metadata`, wrapper, {params}).pipe(
      tap(updatedMetadata => {
        if (bookId != null) {
          patchBookMetadataInCache(this.queryClient, bookId, updatedMetadata);
        }
      })
    );
  }

  updateBooksMetadata(request: BulkMetadataUpdateRequest): Observable<void> {
    return this.http.put(`${this.url}/bulk-edit-metadata`, request).pipe(
      tap(() => {
        invalidateBooksQuery(this.queryClient);
      }),
      map(() => void 0)
    );
  }

  toggleAllLock(bookIds: Set<number>, lock: string): Observable<void> {
    const requestBody = {
      bookIds: Array.from(bookIds),
      lock
    };
    return this.http.put<BookMetadata[]>(`${this.url}/metadata/toggle-all-lock`, requestBody).pipe(
      tap(updatedMetadataList => {
        updatedMetadataList.forEach(metadata => {
          if (metadata.bookId != null) {
            patchBookMetadataInCache(this.queryClient, metadata.bookId, metadata);
          }
        });
      }),
      map(() => void 0),
      catchError((error) => {
        throw error;
      })
    );
  }

  toggleFieldLocks(bookIds: number[] | Set<number>, fieldActions: Record<string, 'LOCK' | 'UNLOCK'>): Observable<void> {
    const bookIdSet = bookIds instanceof Set ? bookIds : new Set(bookIds);

    return this.http.put<void>(`${this.url}/metadata/toggle-field-locks`, {
      bookIds: Array.from(bookIdSet),
      fieldActions
    }).pipe(
      tap(() => {
        for (const bookId of bookIdSet) {
          patchBookInCacheWith(this.queryClient, bookId, book => {
            if (!book.metadata) return book;
            const updatedMetadata = {...book.metadata} as Record<string, unknown>;
            for (const [field, action] of Object.entries(fieldActions)) {
              const lockField = field.endsWith('Locked') ? field : `${field}Locked`;
              if (lockField in updatedMetadata) {
                updatedMetadata[lockField] = action === 'LOCK';
              }
            }
            return {...book, metadata: updatedMetadata as BookMetadata};
          });
        }
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.fieldLockFailedSummary'),
          detail: this.t.translate('book.bookService.toast.fieldLockFailedDetail'),
        });
        throw error;
      })
    );
  }

  consolidateMetadata(metadataType: 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages', targetValues: string[], valuesToMerge: string[]): Observable<unknown> {
    return this.http.post(`${this.url}/metadata/manage/consolidate`, {metadataType, targetValues, valuesToMerge}).pipe(
      tap(() => {
        invalidateBooksQuery(this.queryClient);
      })
    );
  }

  deleteMetadata(metadataType: 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages', valuesToDelete: string[]): Observable<unknown> {
    return this.http.post(`${this.url}/metadata/manage/delete`, {metadataType, valuesToDelete}).pipe(
      tap(() => {
        invalidateBooksQuery(this.queryClient);
      })
    );
  }

  getUploadCoverUrl(bookId: number): string {
    return this.url + '/' + bookId + '/metadata/cover/upload';
  }

  uploadCoverFromUrl(bookId: number, url: string): Observable<BookMetadata> {
    return this.http.post<BookMetadata>(`${this.url}/${bookId}/metadata/cover/from-url`, {url}).pipe(
      tap(updatedMetadata => {
        patchBookMetadataInCache(this.queryClient, bookId, updatedMetadata);
      })
    );
  }

  regenerateCovers(missingOnly = false): Observable<void> {
    return this.http.post<void>(`${this.url}/regenerate-covers?missingOnly=${missingOnly}`, {}).pipe(
      tap(() => {
        invalidateBooksQuery(this.queryClient);
      })
    );
  }

  regenerateCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/regenerate-cover`, {}).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, [bookId]);
      })
    );
  }

  getFileMetadata(bookId: number): Observable<BookMetadata> {
    return this.http.get<BookMetadata>(`${this.url}/${bookId}/file-metadata`);
  }

  generateCustomCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/generate-custom-cover`, {}).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, [bookId]);
      })
    );
  }

  generateCustomCoversForBooks(bookIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.url}/bulk-generate-custom-covers`, {bookIds}).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, bookIds);
      })
    );
  }

  regenerateCoversForBooks(bookIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.url}/bulk-regenerate-covers`, {bookIds}).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, bookIds);
      })
    );
  }

  uploadAudiobookCoverFromUrl(bookId: number, url: string): Observable<BookMetadata> {
    return this.http.post<BookMetadata>(`${this.url}/${bookId}/metadata/audiobook-cover/from-url`, {url}).pipe(
      tap(updatedMetadata => {
        patchBookMetadataInCache(this.queryClient, bookId, updatedMetadata);
      })
    );
  }

  uploadAudiobookCoverFromFile(bookId: number, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<void>(`${this.url}/${bookId}/metadata/audiobook-cover/upload`, formData).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, [bookId]);
      })
    );
  }

  getUploadAudiobookCoverUrl(bookId: number): string {
    return this.url + '/' + bookId + '/metadata/audiobook-cover/upload';
  }

  regenerateAudiobookCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/regenerate-audiobook-cover`, {}).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, [bookId]);
      })
    );
  }

  generateCustomAudiobookCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/generate-custom-audiobook-cover`, {}).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, [bookId]);
      })
    );
  }

  supportsDualCovers(book: Book): boolean {
    const allFiles = [book.primaryFile, ...(book.alternativeFormats || [])].filter(f => f?.bookType);
    const hasAudiobook = allFiles.some(f => f!.bookType === 'AUDIOBOOK');
    const hasEbook = allFiles.some(f => f!.bookType !== 'AUDIOBOOK');
    return hasAudiobook && hasEbook;
  }

  bulkUploadCover(bookIds: number[], file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('bookIds', bookIds.join(','));
    return this.http.post<void>(`${this.url}/bulk-upload-cover`, formData).pipe(
      tap(() => {
        invalidateBookQueries(this.queryClient, bookIds);
      })
    );
  }
}
