import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, first, Observable, of, throwError} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, filter, map, tap} from 'rxjs/operators';
import {Book, BookDeletionResponse, BookMetadata, BookRecommendation, BookSetting, BulkMetadataUpdateRequest, MetadataUpdateWrapper, ReadStatus} from '../model/book.model';
import {BookState} from '../model/state/book-state.model';
import {API_CONFIG} from '../../config/api-config';
import {FetchMetadataRequest} from '../metadata/model/request/fetch-metadata-request.model';
import {MetadataRefreshRequest} from '../metadata/model/request/metadata-refresh-request.model';
import {MessageService} from 'primeng/api';

@Injectable({
  providedIn: 'root',
})
export class BookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private bookStateSubject = new BehaviorSubject<BookState>({
    books: null,
    loaded: false,
    error: null,
  });
  bookState$ = this.bookStateSubject.asObservable();

  private http = inject(HttpClient);
  private messageService = inject(MessageService);

  loadBooks(): void {
    const currentState = this.bookStateSubject.value;
    if (currentState.loaded && this.bookStateSubject.value.books != null) {
      return;
    }
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

  updateBookShelves(bookIds: Set<number | undefined>, shelvesToAssign: Set<number | undefined>, shelvesToUnassign: Set<number | undefined>): Observable<Book[]> {
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

  searchBooks(query: string): Book[] {
    if (query.length < 2) {
      return [];
    }
    const state = this.bookStateSubject.value;
    return (state.books || []).filter(book =>
      book.metadata?.title?.toLowerCase().includes(query.toLowerCase()) ||
      book.metadata?.authors?.some(author => author.toLowerCase().includes(query.toLowerCase()))
    );
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

  downloadFile(bookId: number): void {
    const downloadUrl = `${this.url}/${bookId}/download`;
    this.http.get(downloadUrl, {responseType: 'blob', observe: 'response'})
      .subscribe({
        next: (response) => {
          const contentDisposition = response.headers.get('Content-Disposition');
          const filename = contentDisposition
            ? contentDisposition.match(/filename="(.+?)"/)?.[1] || `book_${bookId}.pdf`
            : `book_${bookId}.pdf`;
          this.saveFile(response.body as Blob, filename);
        },
        error: (err) => console.error('Error downloading file:', err),
      });
  }

  private saveFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
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
    return this.url + '/' + bookId + "/metadata/cover"
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
        return allBooks.filter(b =>
          b.id !== bookId &&
          b.metadata?.seriesName?.toLowerCase() === seriesName
        );
      })
    );
  }

  resetProgress(bookId: number): Observable<Book> {
    return this.http.post<Book>(`${this.url}/${bookId}/reset-progress`, null).pipe(
      tap(updatedBook => this.handleBookUpdate(updatedBook))
    );
  }

  updateBookReadStatus(id: number, status: ReadStatus): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/read-status`, {status}).pipe(
      tap(() => {
        const currentState = this.bookStateSubject.value;
        if (!currentState.books) return;
        const updatedBooks = currentState.books.map(book =>
          book.id === id ? {...book, readStatus: status} : book
        );
        this.bookStateSubject.next({...currentState, books: updatedBooks});
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
