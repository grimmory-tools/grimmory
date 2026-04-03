import {computed, effect, inject, Injectable} from '@angular/core';
import {first, from, lastValueFrom, Observable, throwError} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, map, tap} from 'rxjs/operators';
import {Book, BookDeletionResponse, BookRecommendation, BookSetting, BookStatusUpdateResponse, BookType, CreatePhysicalBookRequest, PersonalRatingUpdateResponse, ReadStatus} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {MessageService} from 'primeng/api';
import {ResetProgressType} from '../../../shared/constants/reset-progress-type';
import {AuthService} from '../../../shared/service/auth.service';
import {Router} from '@angular/router';
import {BookSocketService} from './book-socket.service';
import {BookPatchService} from './book-patch.service';
import {TranslocoService} from '@jsverse/transloco';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {
  BOOKS_QUERY_KEY,
  bookDetailQueryKey,
  bookRecommendationsQueryKey,
} from './book-query-keys';
import {
  invalidateBooksQuery,
  patchBooksInCache,
  removeBookQueries,
} from './book-query-cache';

@Injectable({
  providedIn: 'root',
})
export class BookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private bookSocketService = inject(BookSocketService);
  private bookPatchService = inject(BookPatchService);
  private queryClient = inject(QueryClient);
  private readonly t = inject(TranslocoService);
  private readonly token = this.authService.token;

  private booksQuery = injectQuery(() => ({
    ...this.getBooksQueryOptions(),
    enabled: !!this.token(),
  }));

  books = computed(() => this.booksQuery.data() ?? []);

  /** Pre-computed unique metadata values for autocomplete across the app. */
  readonly uniqueMetadata = computed(() => {
    const books = this.books();
    const authors = new Set<string>();
    const categories = new Set<string>();
    const moods = new Set<string>();
    const tags = new Set<string>();
    const publishers = new Set<string>();
    const series = new Set<string>();

    for (const book of books) {
      const m = book.metadata;
      if (!m) continue;
      m.authors?.forEach(v => authors.add(v));
      m.categories?.forEach(v => categories.add(v));
      m.moods?.forEach(v => moods.add(v));
      m.tags?.forEach(v => tags.add(v));
      if (m.publisher) publishers.add(m.publisher);
      if (m.seriesName) series.add(m.seriesName);
    }

    return {
      authors: Array.from(authors),
      categories: Array.from(categories),
      moods: Array.from(moods),
      tags: Array.from(tags),
      publishers: Array.from(publishers),
      series: Array.from(series),
    };
  });

  booksError = computed<string | null>(() => {
    if (!this.token() || !this.booksQuery.isError()) {
      return null;
    }

    const error = this.booksQuery.error();
    return error instanceof Error ? error.message : 'Failed to load books';
  });

  isBooksLoading = computed(() => !!this.token() && this.booksQuery.isPending());

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: BOOKS_QUERY_KEY});
      }
    });
  }

  private getBooksQueryOptions() {
    return queryOptions({
      queryKey: BOOKS_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<Book[]>(this.url, {params: {stripForListView: false}})),
      staleTime: 30_000,
    });
  }

  bookDetailQueryOptions(bookId: number, withDescription: boolean) {
    return queryOptions({
      queryKey: bookDetailQueryKey(bookId, withDescription),
      queryFn: () => lastValueFrom(this.http.get<Book>(`${this.url}/${bookId}`, {
        params: {
          withDescription: withDescription.toString()
        }
      }))
    });
  }

  ensureBookDetail(bookId: number, withDescription: boolean): Promise<Book> {
    return this.queryClient.ensureQueryData(this.bookDetailQueryOptions(bookId, withDescription));
  }

  /**
   * Always fetches book detail from the server, bypassing the cache.
   *
   * Used by reader components on initialization to guarantee the latest reading position.
   * While optimistic cache updates keep progress current within a single session,
   * this network request is necessary to pick up progress saved on another device or browser.
   */
  fetchFreshBookDetail(bookId: number, withDescription: boolean): Promise<Book> {
    return this.queryClient.fetchQuery({
      ...this.bookDetailQueryOptions(bookId, withDescription),
      staleTime: 0,
    });
  }

  private getBookRecommendationsQueryOptions(bookId: number, limit: number) {
    return queryOptions({
      queryKey: bookRecommendationsQueryKey(bookId, limit),
      queryFn: () => lastValueFrom(this.http.get<BookRecommendation[]>(`${this.url}/${bookId}/recommendations`, {
        params: {limit: limit.toString()}
      }))
    });
  }

  removeBooksFromShelf(shelfId: number): void {
    this.queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
      (current ?? []).map(book => ({
        ...book,
        shelves: book.shelves?.filter(shelf => shelf.id !== shelfId),
      }))
    );
  }

  /*------------------ Book Retrieval ------------------*/

  findBookById(bookId: number): Book | undefined {
    return this.books().find(book => +book.id === +bookId);
  }

  getBooksByIds(bookIds: number[]): Book[] {
    if (bookIds.length === 0) return [];
    const idSet = new Set(bookIds.map(id => +id));
    return this.books().filter(book => idSet.has(+book.id));
  }

  getBooksInSeries(bookId: number): Observable<Book[]> {
    return from(this.queryClient.ensureQueryData(this.getBooksQueryOptions())).pipe(
      map(books => {
        const currentBook = books.find(book => book.id === bookId);
        if (!currentBook?.metadata?.seriesName) {
          return [];
        }

        const seriesName = currentBook.metadata.seriesName.toLowerCase();
        return books.filter(book => book.metadata?.seriesName?.toLowerCase() === seriesName);
      }),
      first()
    );
  }

  getBookRecommendations(bookId: number, limit: number = 20): Observable<BookRecommendation[]> {
    return from(this.queryClient.ensureQueryData(this.getBookRecommendationsQueryOptions(bookId, limit)));
  }

  /*------------------ Book Operations ------------------*/

  deleteBooks(ids: Set<number>): Observable<BookDeletionResponse> {
    const idList = Array.from(ids);
    const params = new HttpParams().set('ids', idList.join(','));

    return this.http.delete<BookDeletionResponse>(this.url, {params}).pipe(
      tap(response => {
        const deletedIds = response.deleted.length > 0 ? response.deleted : idList;
        invalidateBooksQuery(this.queryClient);
        removeBookQueries(this.queryClient, deletedIds);

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
    return this.bookPatchService.updateBookShelves(bookIds, shelvesToAssign, shelvesToUnassign);
  }

  createPhysicalBook(request: CreatePhysicalBookRequest): Observable<Book> {
    return this.http.post<Book>(`${this.url}/physical`, request).pipe(
      tap(newBook => {
        invalidateBooksQuery(this.queryClient);
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
        patchBooksInCache(this.queryClient, [updatedBook]);
      })
    );
  }

  /*------------------ Reading & Viewer Settings ------------------*/

  readBook(bookId: number, reader?: 'epub-streaming', explicitBookType?: BookType): void {
    const book = this.findBookById(bookId);

    if (!book) {
      console.error('Book not found');
      return;
    }

    const bookType: BookType | undefined = explicitBookType ?? book.primaryFile?.bookType;
    const isAlternativeFormat = explicitBookType && explicitBookType !== book.primaryFile?.bookType;

    let baseUrl: string | null = null;
    const queryParams: Partial<{streaming: true; bookType: BookType}> = {};

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

  handleBookMetadataUpdate(bookId: number): void {
    this.bookSocketService.handleBookMetadataUpdate(bookId);
  }

  handleMultipleBookCoverPatches(patches: { id: number; coverUpdatedOn: string }[]): void {
    this.bookSocketService.handleMultipleBookCoverPatches(patches);
  }
}
