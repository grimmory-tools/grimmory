import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, first, Observable, of, throwError} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, filter, map, tap, shareReplay, finalize, distinctUntilChanged} from 'rxjs/operators';
import {Book, BookDeletionResponse, BookMetadata, BookRecommendation, BookSetting, BulkMetadataUpdateRequest, MetadataUpdateWrapper, ReadStatus, AdditionalFileType, AdditionalFile} from '../model/book.model';
import {BookState} from '../model/state/book-state.model';
import {API_CONFIG} from '../../config/api-config';
import {FetchMetadataRequest} from '../../metadata/model/request/fetch-metadata-request.model';
import {MetadataRefreshRequest} from '../../metadata/model/request/metadata-refresh-request.model';
import {MessageService} from 'primeng/api';
import {ResetProgressType, ResetProgressTypes} from '../../shared/constants/reset-progress-type';
import {AuthService} from '../../core/service/auth.service';
import {FileDownloadService} from '../../shared/service/file-download.service';

@Injectable({
  providedIn: 'root',
})
export class BookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private authService = inject(AuthService);
  private fileDownloadService = inject(FileDownloadService);

  private bookStateSubject = new BehaviorSubject<BookState>({
    books: null,
    loaded: false,
    error: null,
  });

  private loading$: Observable<Book[]> | null = null;

  constructor() {
    this.authService.token$.pipe(
      distinctUntilChanged()
    ).subscribe(token => {
      if (token === null) {
        this.bookStateSubject.next({
          books: null,
          loaded: true,
          error: null,
        });
        this.loading$ = null;
      } else {
        const current = this.bookStateSubject.value;
        if (current.loaded && !current.books) {
          this.bookStateSubject.next({
            books: null,
            loaded: false,
            error: null,
          });
          this.loading$ = null;
        }
      }
    });
  }

  bookState$ = this.bookStateSubject.asObservable().pipe(
    tap(state => {
      if (!state.loaded && !state.error && !this.loading$) {
        this.loading$ = this.fetchBooks().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  getCurrentBookState(): BookState {
    return this.bookStateSubject.value;
  }

  private fetchBooks(): Observable<Book[]> {
    return this.http.get<Book[]>(this.url).pipe(
      tap(books => {
        this.bookStateSubject.next({
          books: books || [],
          loaded: true,
          error: null,
        });
      }),
      catchError(error => {
        const curr = this.bookStateSubject.value;
        this.bookStateSubject.next({
          books: curr.books,
          loaded: true,
          error: error.message,
        });
        throw error;
      })
    );
  }

  refreshBooks(): void {
    this.http.get<Book[]>(this.url).pipe(
      tap(books => {
        this.bookStateSubject.next({
          books: books || [],
          loaded: true,
          error: null,
        });
      }),
      catchError(error => {
        this.bookStateSubject.next({
          books: null,
          loaded: true,
          error: error.message,
        });
        return of(null);
      })
    ).subscribe();
  }

  getBookByIdFromState(bookId: number): Book | undefined {
    const currentState = this.bookStateSubject.value;
    return currentState.books?.find(book => +book.id === +bookId);
  }

  getBooksByIdsFromState(bookIds: number[]): Book[] {
    const currentState = this.bookStateSubject.value;
    if (!currentState.books || bookIds.length === 0) return [];

    const idSet = new Set(bookIds.map(id => +id));
    return currentState.books.filter(book => idSet.has(+book.id));
  }

  updateBookShelves(bookIds: Set<number | undefined>, shelvesToAssign: Set<number | null | undefined>, shelvesToUnassign: Set<number | null | undefined>): Observable<Book[]> {
    const requestPayload = {
      bookIds: Array.from(bookIds),
      shelvesToAssign: Array.from(shelvesToAssign),
      shelvesToUnassign: Array.from(shelvesToUnassign),
    };
    return this.http.post<Book[]>(`${this.url}/shelves`, requestPayload).pipe(
      map(updatedBooks => {
        const currentState = this.bookStateSubject.value;
        const currentBooks = currentState.books || [];
        updatedBooks.forEach(updatedBook => {
          const index = currentBooks.findIndex(b => b.id === updatedBook.id);
          if (index !== -1) {
            currentBooks[index] = updatedBook;
          }
        });
        this.bookStateSubject.next({...currentState, books: [...currentBooks]});
        return updatedBooks;
      }),
      catchError(error => {
        const currentState = this.bookStateSubject.value;
        this.bookStateSubject.next({...currentState, error: error.message});
        throw error;
      })
    );
  }

  removeBooksByLibraryId(libraryId: number): void {
    const currentState = this.bookStateSubject.value;
    const currentBooks = currentState.books || [];
    const filteredBooks = currentBooks.filter(book => book.libraryId !== libraryId);
    this.bookStateSubject.next({...currentState, books: filteredBooks});
  }

  removeBooksFromShelf(shelfId: number): void {
    const currentState = this.bookStateSubject.value;
    const currentBooks = currentState.books || [];
    const updatedBooks = currentBooks.map(book => ({
      ...book,
      shelves: book.shelves?.filter(shelf => shelf.id !== shelfId),
    }));
    this.bookStateSubject.next({...currentState, books: updatedBooks});
  }

  getBookSetting(bookId: number): Observable<BookSetting> {
    return this.http.get<BookSetting>(`${this.url}/${bookId}/viewer-setting`);
  }

  updateViewerSetting(bookSetting: BookSetting, bookId: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${bookId}/viewer-setting`, bookSetting);
  }

  updateLastReadTime(bookId: number) {
    const timestamp = new Date().toISOString();
    const currentState = this.bookStateSubject.value;
    const updatedBooks = (currentState.books || []).map(book =>
      book.id === bookId ? {...book, lastReadTime: timestamp} : book
    );
    this.bookStateSubject.next({...currentState, books: updatedBooks});
  }

  readBook(bookId: number, reader?: "ngx" | "streaming"): void {
    const book = this.bookStateSubject.value.books?.find(b => b.id === bookId);
    if (!book) {
      console.error('Book not found');
      return;
    }

    let url: string | null = null;
    switch (book.bookType) {
      case "PDF":
        url = !reader || reader === "ngx"
          ? `/pdf-viewer/book/${book.id}`
          : `/cbx-viewer/book/${book.id}`;
        break;
      case "EPUB":
        url = `/epub-viewer/book/${book.id}`;
        break;
      case "CBX":
        url = `/cbx-viewer/book/${book.id}`;
        break;
      default:
        console.error('Unsupported book type:', book.bookType);
        return;
    }

    window.open(url, '_blank');
    this.updateLastReadTime(book.id);
  }

  getFileContent(bookId: number): Observable<Blob> {
    return this.http.get<Blob>(`${this.url}/${bookId}/content`, {responseType: 'blob' as 'json'});
  }

  getBookByIdFromAPI(bookId: number, withDescription: boolean) {
    return this.http.get<Book>(`${this.url}/${bookId}`, {
      params: {
        withDescription: withDescription.toString()
      }
    });
  }

  deleteBooks(ids: Set<number>): Observable<BookDeletionResponse> {
    const idList = Array.from(ids);
    const params = new HttpParams().set('ids', idList.join(','));

    return this.http.delete<BookDeletionResponse>(this.url, {params}).pipe(
      tap(response => {
        const currentState = this.bookStateSubject.value;
        const remainingBooks = (currentState.books || []).filter(
          book => !ids.has(book.id)
        );

        this.bookStateSubject.next({
          books: remainingBooks,
          loaded: true,
          error: null,
        });

        if (response.failedFileDeletions?.length > 0) {
          this.messageService.add({
            severity: 'warn',
            summary: 'Some files could not be deleted',
            detail: `Books: ${response.failedFileDeletions.join(', ')}`,
          });
        } else {
          this.messageService.add({
            severity: 'success',
            summary: 'Books Deleted',
            detail: `${idList.length} book(s) deleted successfully.`,
          });
        }
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Delete Failed',
          detail: error?.error?.message || error?.message || 'An error occurred while deleting books.',
        });
        return throwError(() => error);
      })
    );
  }

  deleteAdditionalFile(bookId: number, fileId: number): Observable<void> {
    const deleteUrl = `${this.url}/${bookId}/files/${fileId}`;
    return this.http.delete<void>(deleteUrl).pipe(
      tap(() => {
        const currentState = this.bookStateSubject.value;
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            return {
              ...book,
              alternativeFormats: book.alternativeFormats?.filter(file => file.id !== fileId),
              supplementaryFiles: book.supplementaryFiles?.filter(file => file.id !== fileId)
            };
          }
          return book;
        });

        this.bookStateSubject.next({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: 'File Deleted',
          detail: 'Additional file deleted successfully.'
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Delete Failed',
          detail: error?.error?.message || error?.message || 'An error occurred while deleting the file.'
        });
        return throwError(() => error);
      })
    );
  }

  uploadAdditionalFile(bookId: number, file: File, fileType: AdditionalFileType, description?: string): Observable<AdditionalFile> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('additionalFileType', fileType);
    if (description) {
      formData.append('description', description);
    }

    return this.http.post<AdditionalFile>(`${this.url}/${bookId}/files`, formData).pipe(
      tap((newFile) => {
        const currentState = this.bookStateSubject.value;
        const updatedBooks = (currentState.books || []).map(book => {
          if (book.id === bookId) {
            const updatedBook = {...book};
            if (fileType === AdditionalFileType.ALTERNATIVE_FORMAT) {
              updatedBook.alternativeFormats = [...(book.alternativeFormats || []), newFile];
            } else {
              updatedBook.supplementaryFiles = [...(book.supplementaryFiles || []), newFile];
            }
            return updatedBook;
          }
          return book;
        });

        this.bookStateSubject.next({
          ...currentState,
          books: updatedBooks
        });

        this.messageService.add({
          severity: 'success',
          summary: 'File Uploaded',
          detail: 'Additional file uploaded successfully.'
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Upload Failed',
          detail: error?.error?.message || error?.message || 'An error occurred while uploading the file.'
        });
        return throwError(() => error);
      })
    );
  }

  downloadFile(bookId: number): void {
    const downloadUrl = `${this.url}/${bookId}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, `book_${bookId}`);
  }

  downloadAdditionalFile(bookId: number, fileId: number): void {
    const downloadUrl = `${this.url}/${bookId}/files/${fileId}/download`;
    this.fileDownloadService.downloadFile(downloadUrl, `additional_file_${fileId}`);
  }

  savePdfProgress(bookId: number, page: number, percentage: number): Observable<void> {
    const body = {
      bookId: bookId,
      pdfProgress: {
        page: page,
        percentage: percentage
      }
    }
    return this.http.post<void>(`${this.url}/progress`, body);
  }

  saveEpubProgress(bookId: number, cfi: string, percentage: number): Observable<void> {
    const body = {
      bookId: bookId,
      epubProgress: {
        cfi: cfi,
        percentage: percentage
      }
    };
    return this.http.post<void>(`${this.url}/progress`, body);
  }

  saveCbxProgress(bookId: number, page: number, percentage: number): Observable<void> {
    const body = {
      bookId: bookId,
      cbxProgress: {
        page: page,
        percentage: percentage
      }
    };
    return this.http.post<void>(`${this.url}/progress`, body);
  }

  updateDateFinished(bookId: number, dateFinished: string | null): Observable<void> {
    const body = {
      bookId: bookId,
      dateFinished: dateFinished
    };
    return this.http.post<void>(`${this.url}/progress`, body).pipe(
      tap(() => {
        // Update the book in the state
        const currentState = this.bookStateSubject.value;
        if (currentState.books) {
          const updatedBooks = currentState.books.map(book => {
            if (book.id === bookId) {
              return {...book, dateFinished: dateFinished || undefined};
            }
            return book;
          });
          this.bookStateSubject.next({
            ...currentState,
            books: updatedBooks
          });
        }
      })
    );
  }

  regenerateCovers(): Observable<void> {
    return this.http.post<void>(`${this.url}/regenerate-covers`, {});
  }

  regenerateCover(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${bookId}/regenerate-cover`, {});
  }


  /*------------------ All the metadata related calls go here ------------------*/

  fetchBookMetadata(bookId: number, request: FetchMetadataRequest): Observable<BookMetadata[]> {
    return this.http.post<BookMetadata[]>(`${this.url}/${bookId}/metadata/prospective`, request);
  }

  updateBookMetadata(bookId: number | undefined, wrapper: MetadataUpdateWrapper, mergeCategories: boolean): Observable<BookMetadata> {
    const params = new HttpParams().set('mergeCategories', mergeCategories.toString());
    return this.http.put<BookMetadata>(`${this.url}/${bookId}/metadata`, wrapper, {params}).pipe(
      map(updatedMetadata => {
        this.handleBookMetadataUpdate(bookId!, updatedMetadata);
        return updatedMetadata;
      })
    );
  }

  updateBooksMetadata(request: BulkMetadataUpdateRequest, mergeCategories: boolean): Observable<BookMetadata[]> {
    const params = new HttpParams().set('mergeCategories', mergeCategories.toString());
    return this.http.put<BookMetadata[]>(`${this.url}/bulk-edit-metadata`, request, {params}).pipe(
      map(updatedMetadataList => {
        request.bookIds.forEach((id, index) => {
          this.handleBookMetadataUpdate(id, updatedMetadataList[index]);
        });
        return updatedMetadataList;
      })
    );
  }

  toggleAllLock(bookIds: Set<number>, lock: string): Observable<void> {
    const requestBody = {
      bookIds: Array.from(bookIds),
      lock: lock
    };
    return this.http.put<BookMetadata[]>(`${this.url}/metadata/toggle-all-lock`, requestBody).pipe(
      tap((updatedMetadataList) => {
        const currentState = this.bookStateSubject.value;
        const updatedBooks = (currentState.books || []).map(book => {
          const updatedMetadata = updatedMetadataList.find(meta => meta.bookId === book.id);
          return updatedMetadata ? {...book, metadata: updatedMetadata} : book;
        });
        this.bookStateSubject.next({...currentState, books: updatedBooks});
      }),
      map(() => void 0),
      catchError((error) => {
        throw error;
      })
    );
  }

  getComicInfoMetadata(bookId: number): Observable<BookMetadata> {
    return this.http.get<BookMetadata>(`${this.url}/${bookId}/cbx/metadata/comicinfo`);
  }

  autoRefreshMetadata(metadataRefreshRequest: MetadataRefreshRequest): Observable<any> {
    return this.http.put<void>(`${this.url}/metadata/refresh`, metadataRefreshRequest).pipe(
      map(() => {
        this.messageService.add({
          severity: 'success',
          summary: 'Metadata Update Scheduled',
          detail: 'The metadata update for the selected books has been successfully scheduled.'
        });
        return {success: true};
      }),
      catchError((e) => {
        if (e.status === 409) {
          this.messageService.add({
            severity: 'error',
            summary: 'Task Already Running',
            life: 5000,
            detail: 'A metadata refresh task is already in progress. Please wait for it to complete before starting another one.'
          });
        } else {
          this.messageService.add({
            severity: 'error',
            summary: 'Metadata Update Failed',
            life: 5000,
            detail: 'An unexpected error occurred while scheduling the metadata update. Please try again later or contact support if the issue persists.'
          });
        }
        return of({success: false});
      })
    );
  }

  getUploadCoverUrl(bookId: number): string {
    return this.url + '/' + bookId + "/metadata/cover/upload"
  }

  uploadCoverFromUrl(bookId: number, url: string): Observable<BookMetadata> {
    return this.http
      .post<BookMetadata>(`${this.url}/${bookId}/metadata/cover/from-url`, {url})
      .pipe(
        tap(updatedMetadata =>
          this.handleBookMetadataUpdate(bookId, updatedMetadata)
        )
      );
  }

  getBookRecommendations(bookId: number, limit: number = 20): Observable<BookRecommendation[]> {
    return this.http.get<BookRecommendation[]>(`${this.url}/${bookId}/recommendations`, {
      params: {limit: limit.toString()}
    });
  }

  getBooksInSeries(bookId: number): Observable<Book[]> {
    return this.bookStateSubject.asObservable().pipe(
      filter(state => state.loaded),
      first(),
      map(state => {
        const allBooks = state.books || [];
        const currentBook = allBooks.find(b => b.id === bookId);

        if (!currentBook || !currentBook.metadata?.seriesName) {
          return [];
        }

        const seriesName = currentBook.metadata.seriesName.toLowerCase();
        return allBooks.filter(b => b.metadata?.seriesName?.toLowerCase() === seriesName);
      })
    );
  }

  resetProgress(bookIds: number | number[], type: ResetProgressType): Observable<Book[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];
    const params = new HttpParams().set('type', ResetProgressTypes[type]);
    return this.http.post<Book[]>(`${this.url}/reset-progress`, ids, {params}).pipe(
      tap(updatedBooks => updatedBooks.forEach(book => this.handleBookUpdate(book)))
    );
  }

  updateBookReadStatus(bookIds: number | number[], status: ReadStatus): Observable<Book[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];
    return this.http.put<Book[]>(`${this.url}/read-status`, {ids, status}).pipe(
      tap(updatedBooks => {
        updatedBooks.forEach(updatedBook => this.handleBookUpdate(updatedBook));
      })
    );
  }


  /*------------------ All the websocket handlers go below ------------------*/

  handleNewlyCreatedBook(book: Book): void {
    const currentState = this.bookStateSubject.value;
    const updatedBooks = currentState.books ? [...currentState.books] : [];
    const bookIndex = updatedBooks.findIndex(existingBook => existingBook.id === book.id);
    if (bookIndex > -1) {
      updatedBooks[bookIndex] = book;
    } else {
      updatedBooks.push(book);
    }
    this.bookStateSubject.next({...currentState, books: updatedBooks});
  }

  handleRemovedBookIds(removedBookIds: number[]): void {
    const currentState = this.bookStateSubject.value;
    const filteredBooks = (currentState.books || []).filter(book => !removedBookIds.includes(book.id));
    this.bookStateSubject.next({...currentState, books: filteredBooks});
  }

  handleBookUpdate(updatedBook: Book) {
    const currentState = this.bookStateSubject.value;
    const updatedBooks = (currentState.books || []).map(book =>
      book.id === updatedBook.id ? updatedBook : book
    );
    this.bookStateSubject.next({...currentState, books: updatedBooks});
  }

  handleMultipleBookUpdates(updatedBooks: Book[]): void {
    const currentState = this.bookStateSubject.value;
    const currentBooks = currentState.books || [];

    const updatedMap = new Map(updatedBooks.map(book => [book.id, book]));

    const mergedBooks = currentBooks.map(book =>
      updatedMap.has(book.id) ? updatedMap.get(book.id)! : book
    );

    this.bookStateSubject.next({...currentState, books: mergedBooks});
  }

  handleBookMetadataUpdate(bookId: number, updatedMetadata: BookMetadata) {
    const currentState = this.bookStateSubject.value;
    const updatedBooks = (currentState.books || []).map(book => {
      return book.id == bookId ? {...book, metadata: updatedMetadata} : book
    });
    this.bookStateSubject.next({...currentState, books: updatedBooks})
  }

  toggleFieldLocks(bookIds: number[] | Set<number>, fieldActions: Record<string, 'LOCK' | 'UNLOCK'>): Observable<void> {
    const bookIdSet = bookIds instanceof Set ? bookIds : new Set(bookIds);

    const requestBody = {
      bookIds: Array.from(bookIdSet),
      fieldActions
    };

    return this.http.put<void>(`${this.url}/metadata/toggle-field-locks`, requestBody).pipe(
      tap(() => {
        const currentState = this.bookStateSubject.value;
        const updatedBooks = (currentState.books || []).map(book => {
          if (!bookIdSet.has(book.id)) return book;
          const updatedMetadata = {...book.metadata};
          for (const [field, action] of Object.entries(fieldActions)) {
            const lockField = field.endsWith('Locked') ? field : `${field}Locked`;
            if (lockField in updatedMetadata) {
              (updatedMetadata as any)[lockField] = action === 'LOCK';
            }
          }
          return {
            ...book,
            metadata: updatedMetadata
          };
        });
        this.bookStateSubject.next({
          ...currentState,
          books: updatedBooks as Book[]
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Field Lock Update Failed',
          detail: 'Failed to update metadata field locks. Please try again.',
        });
        throw error;
      })
    );
  }

  restoreMetadata(bookId: number) {
    return this.http.post<BookMetadata>(`${this.url}/${bookId}/metadata/restore`, null).pipe(
      map(updatedMetadata => {
        this.handleBookMetadataUpdate(bookId, updatedMetadata);
        return updatedMetadata;
      })
    );
  }

  getBackupMetadata(bookId: number) {
    return this.http.get<any>(`${this.url}/${bookId}/metadata/restore`);
  }

}
