import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, map, tap} from 'rxjs/operators';
import {Book, BookMetadata, BulkMetadataUpdateRequest, MetadataUpdateWrapper} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {BookStateService} from './book-state.service';
import {BookSocketService} from './book-socket.service';
import {TranslocoService} from '@jsverse/transloco';
import {BookService} from './book.service';

@Injectable({
  providedIn: 'root',
})
export class BookMetadataManageService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private bookStateService = inject(BookStateService);
  private bookSocketService = inject(BookSocketService);
  private bookService = inject(BookService);
  private readonly t = inject(TranslocoService);

  updateBookMetadata(bookId: number | undefined, wrapper: MetadataUpdateWrapper, mergeCategories: boolean): Observable<BookMetadata> {
    const params = new HttpParams().set('mergeCategories', mergeCategories.toString());
    return this.http.put<BookMetadata>(`${this.url}/${bookId}/metadata`, wrapper, {params}).pipe(
      map(updatedMetadata => {
        this.bookSocketService.handleBookMetadataUpdate(bookId!, updatedMetadata);
        return updatedMetadata;
      })
    );
  }

  updateBooksMetadata(request: BulkMetadataUpdateRequest): Observable<void> {
    return this.http.put(`${this.url}/bulk-edit-metadata`, request).pipe(
      map(() => void 0)
    );
  }

  toggleAllLock(bookIds: Set<number>, lock: string): Observable<void> {
    const requestBody = {
      bookIds: Array.from(bookIds),
      lock: lock
    };
    return this.http.put<BookMetadata[]>(`${this.url}/metadata/toggle-all-lock`, requestBody).pipe(
      tap((updatedMetadataList) => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          const updatedMetadata = updatedMetadataList.find(meta => meta.bookId === book.id);
          return updatedMetadata ? {...book, metadata: updatedMetadata} : book;
        });
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      }),
      map(() => void 0),
      catchError((error) => {
        throw error;
      })
    );
  }

  toggleFieldLocks(bookIds: number[] | Set<number>, fieldActions: Record<string, 'LOCK' | 'UNLOCK'>): Observable<void> {
    const bookIdSet = bookIds instanceof Set ? bookIds : new Set(bookIds);

    const requestBody = {
      bookIds: Array.from(bookIdSet),
      fieldActions
    };

    return this.http.put<void>(`${this.url}/metadata/toggle-field-locks`, requestBody).pipe(
      tap(() => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          if (!bookIdSet.has(book.id)) return book;
          const updatedMetadata = {...book.metadata};
          for (const [field, action] of Object.entries(fieldActions)) {
            const lockField = field.endsWith('Locked') ? field : `${field}Locked`;
            if (lockField in updatedMetadata) {
              (updatedMetadata as Record<string, unknown>)[lockField] = action === 'LOCK';
            }
          }
          return {
            ...book,
            metadata: updatedMetadata
          };
        });
        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks as Book[]
        });
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
    const payload = {metadataType, targetValues, valuesToMerge};
    return this.http.post(`${this.url}/metadata/manage/consolidate`, payload).pipe(
      tap(() => {
        this.bookService.refreshBooks();
      })
    );
  }

  deleteMetadata(metadataType: 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages', valuesToDelete: string[]): Observable<unknown> {
    const payload = {metadataType, valuesToDelete};
    return this.http.post(`${this.url}/metadata/manage/delete`, payload).pipe(
      tap(() => {
        this.bookService.refreshBooks();
      })
    );
  }

  /*------------------ Cover Operations ------------------*/

  getUploadCoverUrl(bookId: number): string {
    return this.url + '/' + bookId + "/metadata/cover/upload"
  }

  uploadCoverFromUrl(bookId: number, url: string): Observable<BookMetadata> {
    return this.http.post<BookMetadata>(`${this.url}/${bookId}/metadata/cover/from-url`, {url});
  }

  regenerateCovers(): Observable<void> {
    return this.http.post<void>(`${this.url}/regenerate-covers`, {});
  }

  regenerateCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/regenerate-cover`, {});
  }

  getFileMetadata(bookId: number): Observable<BookMetadata> {
    return this.http.get<BookMetadata>(`${this.url}/${bookId}/file-metadata`);
  }

  generateCustomCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/generate-custom-cover`, {});
  }

  generateCustomCoversForBooks(bookIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.url}/bulk-generate-custom-covers`, {bookIds});
  }

  regenerateCoversForBooks(bookIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.url}/bulk-regenerate-covers`, {bookIds});
  }

  uploadAudiobookCoverFromUrl(bookId: number, url: string): Observable<BookMetadata> {
    return this.http.post<BookMetadata>(`${this.url}/${bookId}/metadata/audiobook-cover/from-url`, {url});
  }

  uploadAudiobookCoverFromFile(bookId: number, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<void>(`${this.url}/${bookId}/metadata/audiobook-cover/upload`, formData);
  }

  getUploadAudiobookCoverUrl(bookId: number): string {
    return this.url + '/' + bookId + "/metadata/audiobook-cover/upload";
  }

  regenerateAudiobookCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/regenerate-audiobook-cover`, {});
  }

  generateCustomAudiobookCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/generate-custom-audiobook-cover`, {});
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
    return this.http.post<void>(`${this.url}/bulk-upload-cover`, formData);
  }
}
