import {inject, Injectable} from '@angular/core';
import {first, Observable, of, throwError} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, distinctUntilChanged, filter, finalize, map, shareReplay, tap} from 'rxjs/operators';
import {Book, BookDeletionResponse, BookRecommendation, BookSetting, BookStatusUpdateResponse, BookType, CreatePhysicalBookRequest, PersonalRatingUpdateResponse, ReadStatus} from '../model/book.model';
import {BookState} from '../model/state/book-state.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {ResetProgressType} from '../../../shared/constants/reset-progress-type';
import {AuthService} from '../../../shared/service/auth.service';
import {Router} from '@angular/router';
import {BookStateService} from './book-state.service';
import {BookSocketService} from './book-socket.service';
import {BookPatchService} from './book-patch.service';
import {TranslocoService} from '@jsverse/transloco';

@Injectable({
  providedIn: 'root',
})
export class BookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private bookStateService = inject(BookStateService);
  private bookSocketService = inject(BookSocketService);
  private bookPatchService = inject(BookPatchService);
  private readonly t = inject(TranslocoService);

  private loading$: Observable<Book[]> | null = null;

  constructor() {
    this.authService.token$.pipe(
      distinctUntilChanged()
    ).subscribe(token => {
      if (token === null) {
        this.bookStateService.resetBookState();
        this.loading$ = null;
      } else {
        const current = this.bookStateService.getCurrentBookState();
        if (current.loaded && !current.books) {
          this.bookStateService.updateBookState({
            books: null,
            loaded: false,
            error: null,
          });
          this.loading$ = null;
        }
      }
    });
  }

  /*------------------ State Management ------------------*/

  bookState$ = this.bookStateService.bookState$.pipe(
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
    return this.bookStateService.getCurrentBookState();
  }

  private fetchBooks(): Observable<Book[]> {
    return this.http.get<Book[]>(this.url, {params: {stripForListView: false}}).pipe(
      map(bookList => {
        this.bookStateService.updateBookState({
          books: bookList,
          loaded: true,
          error: null,
        });
        return bookList;
      }),
      catchError(error => {
        const curr = this.bookStateService.getCurrentBookState();
        this.bookStateService.updateBookState({
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
      tap(bookList => {
        this.bookStateService.updateBookState({
          books: bookList,
          loaded: true,
          error: null,
        });
      }),
      catchError(error => {
        this.bookStateService.updateBookState({
          books: null,
          loaded: true,
          error: error.message,
        });
        return of(null);
      })
    ).subscribe();
  }

  removeBooksByLibraryId(libraryId: number): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const currentBooks = currentState.books || [];
    const filteredBooks = currentBooks.filter(book => book.libraryId !== libraryId);
    this.bookStateService.updateBookState({...currentState, books: filteredBooks});
  }

  removeBooksFromShelf(shelfId: number): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const currentBooks = currentState.books || [];
    const updatedBooks = currentBooks.map(book => ({
      ...book,
      shelves: book.shelves?.filter(shelf => shelf.id !== shelfId),
    }));
    this.bookStateService.updateBookState({...currentState, books: updatedBooks});
  }

  /*------------------ Book Retrieval ------------------*/

  getBookByIdFromState(bookId: number): Book | undefined {
    const currentState = this.bookStateService.getCurrentBookState();
    return currentState.books?.find(book => +book.id === +bookId);
  }

  getBooksByIdsFromState(bookIds: number[]): Book[] {
    const currentState = this.bookStateService.getCurrentBookState();
    if (!currentState.books || bookIds.length === 0) return [];

    const idSet = new Set(bookIds.map(id => +id));
    return currentState.books.filter(book => idSet.has(+book.id));
  }

  getBookByIdFromAPI(bookId: number, withDescription: boolean): Observable<Book> {
    return this.http.get<Book>(`${this.url}/${bookId}`, {
      params: {
        withDescription: withDescription.toString()
      }
    });
  }

  getBooksInSeries(bookId: number): Observable<Book[]> {
    return this.bookStateService.bookState$.pipe(
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

  getBookRecommendations(bookId: number, limit: number = 20): Observable<BookRecommendation[]> {
    return this.http.get<BookRecommendation[]>(`${this.url}/${bookId}/recommendations`, {
      params: {limit: limit.toString()}
    });
  }

  /*------------------ Book Operations ------------------*/

  deleteBooks(ids: Set<number>): Observable<BookDeletionResponse> {
    const idList = Array.from(ids);
    const params = new HttpParams().set('ids', idList.join(','));

    return this.http.delete<BookDeletionResponse>(this.url, {params}).pipe(
      tap(response => {
        const currentState = this.bookStateService.getCurrentBookState();
        const remainingBooks = (currentState.books || []).filter(
          book => !ids.has(book.id)
        );

        this.bookStateService.updateBookState({
          books: remainingBooks,
          loaded: true,
          error: null,
        });

        if (response.failedFileDeletions?.length > 0) {
          this.messageService.add({
            severity: 'warn',
            summary: this.t.translate('book.bookService.toast.someFilesNotDeletedSummary'),
            detail: this.t.translate('book.bookService.toast.someFilesNotDeletedDetail', {fileNames: response.failedFileDeletions.join(', ')}),
          });
        } else {
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('book.bookService.toast.booksDeletedSummary'),
            detail: this.t.translate('book.bookService.toast.booksDeletedDetail', {count: idList.length}),
          });
        }
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.deleteFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.deleteFailedDetail'),
        });
        return throwError(() => error);
      })
    );
  }

  updateBookShelves(bookIds: Set<number | undefined>, shelvesToAssign: Set<number | null | undefined>, shelvesToUnassign: Set<number | null | undefined>): Observable<Book[]> {
    return this.bookPatchService.updateBookShelves(bookIds, shelvesToAssign, shelvesToUnassign).pipe(
      catchError(error => {
        const currentState = this.bookStateService.getCurrentBookState();
        this.bookStateService.updateBookState({...currentState, error: error.message});
        throw error;
      })
    );
  }

  createPhysicalBook(request: CreatePhysicalBookRequest): Observable<Book> {
    return this.http.post<Book>(`${this.url}/physical`, request).pipe(
      tap(newBook => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = [...(currentState.books || []), newBook];
        this.bookStateService.updateBookState({
          ...currentState,
          books: updatedBooks
        });
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.bookService.toast.physicalBookCreatedSummary'),
          detail: this.t.translate('book.bookService.toast.physicalBookCreatedDetail', {title: newBook.metadata?.title || 'Book'})
        });
      }),
      catchError(error => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.bookService.toast.creationFailedSummary'),
          detail: error?.error?.message || error?.message || this.t.translate('book.bookService.toast.creationFailedDetail')
        });
        return throwError(() => error);
      })
    );
  }

  togglePhysicalFlag(bookId: number, physical: boolean): Observable<Book> {
    return this.http.patch<Book>(`${this.url}/${bookId}/physical`, null, {params: {physical}}).pipe(
      tap(updatedBook => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(b => b.id === bookId ? {...b, isPhysical: physical} : b);
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      })
    );
  }

  /*------------------ Reading & Viewer Settings ------------------*/

  readBook(bookId: number, reader?: 'epub-streaming', explicitBookType?: BookType): void {
    const book = this.bookStateService
      .getCurrentBookState()
      .books?.find(b => b.id === bookId);

    if (!book) {
      console.error('Book not found');
      return;
    }

    const bookType: BookType | undefined = explicitBookType ?? book.primaryFile?.bookType;
    const isAlternativeFormat = explicitBookType && explicitBookType !== book.primaryFile?.bookType;

    let baseUrl: string | null = null;
    let queryParams: Record<string, any> = {};

    switch (bookType) {
      case 'PDF':
        baseUrl = 'pdf-reader';
        break;

      case 'EPUB':
        baseUrl = 'ebook-reader';
        if (reader === 'epub-streaming') {
          queryParams['streaming'] = true;
        }
        break;

      case 'FB2':
      case 'MOBI':
      case 'AZW3':
        baseUrl = 'ebook-reader';
        break;

      case 'CBX':
        baseUrl = 'cbx-reader';
        break;

      case 'AUDIOBOOK':
        baseUrl = 'audiobook-player';
        break;
    }

    if (!baseUrl) {
      console.error('Unsupported book type:', bookType);
      return;
    }

    if (isAlternativeFormat) {
      queryParams['bookType'] = bookType;
    }

    const hasQueryParams = Object.keys(queryParams).length > 0;
    this.router.navigate([`/${baseUrl}/book/${book.id}`], hasQueryParams ? {queryParams} : undefined);

    this.updateLastReadTime(book.id);
  }

  getBookSetting(bookId: number, bookFileId: number): Observable<BookSetting> {
    return this.http.get<BookSetting>(`${this.url}/${bookId}/viewer-setting?bookFileId=${bookFileId}`);
  }

  updateViewerSetting(bookSetting: BookSetting, bookId: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${bookId}/viewer-setting`, bookSetting);
  }

  /*------------------ Progress & Status Tracking ------------------*/

  updateLastReadTime(bookId: number): void {
    this.bookPatchService.updateLastReadTime(bookId);
  }

  savePdfProgress(bookId: number, page: number, percentage: number, bookFileId?: number): Observable<void> {
    return this.bookPatchService.savePdfProgress(bookId, page, percentage, bookFileId);
  }

  saveCbxProgress(bookId: number, page: number, percentage: number, bookFileId?: number): Observable<void> {
    return this.bookPatchService.saveCbxProgress(bookId, page, percentage, bookFileId);
  }

  updateDateFinished(bookId: number, dateFinished: string | null): Observable<void> {
    return this.bookPatchService.updateDateFinished(bookId, dateFinished);
  }

  resetProgress(bookIds: number | number[], type: ResetProgressType): Observable<BookStatusUpdateResponse[]> {
    return this.bookPatchService.resetProgress(bookIds, type);
  }

  updateBookReadStatus(bookIds: number | number[], status: ReadStatus): Observable<BookStatusUpdateResponse[]> {
    return this.bookPatchService.updateBookReadStatus(bookIds, status);
  }

  /*------------------ Personal Rating ------------------*/

  resetPersonalRating(bookIds: number | number[]): Observable<PersonalRatingUpdateResponse[]> {
    return this.bookPatchService.resetPersonalRating(bookIds);
  }

  updatePersonalRating(bookIds: number | number[], rating: number): Observable<PersonalRatingUpdateResponse[]> {
    return this.bookPatchService.updatePersonalRating(bookIds, rating);
  }

  /*------------------ Websocket Handlers ------------------*/

  handleNewlyCreatedBook(book: Book): void {
    this.bookSocketService.handleNewlyCreatedBook(book);
  }

  handleRemovedBookIds(removedBookIds: number[]): void {
    this.bookSocketService.handleRemovedBookIds(removedBookIds);
  }

  handleBookUpdate(updatedBook: Book): void {
    this.bookSocketService.handleBookUpdate(updatedBook);
  }

  handleMultipleBookUpdates(updatedBooks: Book[]): void {
    this.bookSocketService.handleMultipleBookUpdates(updatedBooks);
  }

  handleMultipleBookCoverPatches(patches: { id: number; coverUpdatedOn: string }[]): void {
    this.bookSocketService.handleMultipleBookCoverPatches(patches);
  }
}
