import {computed, effect, inject, Injectable, signal} from '@angular/core';
import {concatMap, first, from, lastValueFrom, map, Observable, of, switchMap, throwError, toArray} from 'rxjs';
import {HttpClient, HttpErrorResponse, HttpParams} from '@angular/common/http';
import {catchError, tap} from 'rxjs/operators';
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
  invalidateAppBooksQueries,
  invalidateBooksQuery,
  invalidateLegacyBooksQuery,
  patchBooksInCache,
  removeBookQueries,
} from './book-query-cache';
import {AppBooksApiService} from './app-books-api.service';

// Max ids per /books/batch GET request; keeps the ids query string within URL-length limits.
const BOOKS_BATCH_FETCH_SIZE = 200;

@Injectable({
  providedIn: 'root',
})
export class BookService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private readonly http = inject(HttpClient);
  private readonly messageService = inject(MessageService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly bookSocketService = inject(BookSocketService);
  private readonly bookPatchService = inject(BookPatchService);
  private readonly queryClient = inject(QueryClient);
  private readonly appBooksApi = inject(AppBooksApiService);
  private readonly t = inject(TranslocoService);
  private readonly token = this.authService.token;
  private readonly legacyCatalogEnabled = signal(false);
  private legacyCatalogRequested = false;

  private readonly booksQuery = injectQuery(() => ({
    ...this.getBooksQueryOptions(),
    enabled: !!this.token() && this.legacyCatalogEnabled(),
  }));

  /**
   * Transitional legacy catalog accessor.
   *
   * The full-catalog query stays disabled until an unmigrated screen actually reads it.
   * Scheduling the enable outside the current reactive computation avoids a signal write
   * from inside a consumer's computed/effect. Remove this accessor with the final legacy
   * catalog consumers.
   */
  books = (): Book[] => {
    if (!this.legacyCatalogRequested) {
      this.legacyCatalogRequested = true;
      queueMicrotask(() => this.legacyCatalogEnabled.set(true));
    }
    return this.booksQuery.data() ?? [];
  };

  /** Pre-computed unique metadata values for autocomplete across the app. */
  readonly uniqueMetadata = computed(() => {
    this.appBooksApi.enableGlobalFilterOptions();
    const options = this.appBooksApi.globalFilterOptions();

    return {
      authors: options?.authors.map(option => option.name) ?? [],
      categories: options?.categories.map(option => option.name) ?? [],
      moods: options?.moods.map(option => option.name) ?? [],
      tags: options?.tags.map(option => option.name) ?? [],
      publishers: options?.publishers.map(option => option.name) ?? [],
      series: options?.series.map(option => option.name) ?? [],
    };
  });

  booksError = computed<string | null>(() => {
    if (!this.token() || !this.booksQuery.isError()) {
      return null;
    }

    const error = this.booksQuery.error();
    return error instanceof Error ? error.message : 'Failed to load books';
  });

  /**
   * True when the server refused the flat catalog because it exceeds the legacy size cap.
   *
   * The screens still reading the full catalog aggregate it client-side and have no paginated
   * equivalent yet, so they cannot render at all here. They must say so rather than show an empty
   * result, which reads as an empty library. A 400 identifies this unambiguously: the flat
   * endpoint's only other parameters are booleans with defaults, so the cap is its sole 400.
   */
  readonly legacyCatalogTooLarge = computed(() => {
    if (!this.token() || !this.booksQuery.isError()) {
      return false;
    }
    const error = this.booksQuery.error();
    return error instanceof HttpErrorResponse && error.status === 400;
  });

  // Use isLoading (isPending && isFetching), not isPending: the legacy catalog query is
  // lazily enabled, and a disabled/idle query reports isPending forever. Screens that read
  // this flag without consuming books() (e.g. the dashboard) would otherwise spin endlessly.
  isBooksLoading = computed(() => !!this.token() && this.booksQuery.isLoading());

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: BOOKS_QUERY_KEY});
      }
    });
  }

  getBooksQueryOptions() {
    return queryOptions({
      queryKey: BOOKS_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<Book[]>(this.url, {params: {stripForListView: false}})),
      staleTime: 5 * 60_000,
      // Never retried: every attempt materializes the whole flat catalog, and on large catalogs
      // the server refuses it with a 400 that no amount of retrying will change.
      retry: false,
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

  bookRecommendationsQueryOptions(bookId: number, limit: number) {
    return queryOptions({
      queryKey: bookRecommendationsQueryKey(bookId, limit),
      queryFn: () => lastValueFrom(this.http.get<BookRecommendation[]>(`${this.url}/${bookId}/recommendations`, {
        params: {limit: limit.toString()}
      })),
      retry: false,
    });
  }

  removeBooksFromShelf(_shelfId: number): void {
    invalidateLegacyBooksQuery(this.queryClient);
    invalidateAppBooksQueries(this.queryClient);
  }

  /*------------------ Book Retrieval ------------------*/

  findBookById(bookId: number): Book | undefined {
    return this.appBooksApi.books().find(book => +book.id === +bookId)
      ?? this.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)?.find(book => +book.id === +bookId);
  }

  // Resolve full Book objects for the given ids. The paginated app-books cache only retains a
  // bounded window (maxPages), so any requested book outside the loaded window is fetched from the
  // server (/books/batch returns full DTOs, incl. shelves/file info the summary cache lacks). The
  // result preserves the requested id order.
  getBooksByIds(bookIds: number[]): Observable<Book[]> {
    if (bookIds.length === 0) return of([]);
    const idSet = new Set(bookIds.map(id => +id));
    const cached = new Map<number, Book>();
    for (const book of this.appBooksApi.books()) {
      if (idSet.has(+book.id)) cached.set(+book.id, book);
    }
    for (const book of this.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY) ?? []) {
      if (idSet.has(+book.id) && !cached.has(+book.id)) cached.set(+book.id, book);
    }

    const order = (extra: Map<number, Book>): Book[] =>
      bookIds.flatMap(id => {
        const book = cached.get(+id) ?? extra.get(+id);
        return book ? [book] : [];
      });

    const missing = bookIds.filter(id => !cached.has(+id));
    if (missing.length === 0) {
      return of(order(new Map()));
    }
    return this.fetchBooksByIdsFromApi(missing).pipe(
      map(fetched => order(new Map(fetched.map(book => [+book.id, book])))),
    );
  }

  private fetchBooksByIdsFromApi(ids: number[]): Observable<Book[]> {
    const batches: number[][] = [];
    // Chunk to keep the /batch GET query string within URL-length limits (ids are query params).
    for (let offset = 0; offset < ids.length; offset += BOOKS_BATCH_FETCH_SIZE) {
      batches.push(ids.slice(offset, offset + BOOKS_BATCH_FETCH_SIZE));
    }
    return from(batches).pipe(
      concatMap(batch => {
        let params = new HttpParams();
        for (const id of batch) {
          params = params.append('ids', id.toString());
        }
        return this.http.get<Book[]>(`${this.url}/batch`, {params});
      }),
      toArray(),
      map(responses => responses.flat()),
    );
  }

  getBooksInSeries(bookId: number): Observable<Book[]> {
    return from(this.ensureBookDetail(bookId, false)).pipe(
      switchMap(book => book.metadata?.seriesName
        ? this.appBooksApi.getSeriesBooks(book.metadata.seriesName)
        : from([[] as Book[]])),
      first()
    );
  }

  getBookRecommendations(bookId: number, limit: number = 20): Observable<BookRecommendation[]> {
    return from(this.queryClient.ensureQueryData(this.bookRecommendationsQueryOptions(bookId, limit)));
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

  readBook(bookId: number, reader?: 'epub-streaming' | 'epub-blob', explicitBookType?: BookType): void {
    const book = this.findBookById(bookId);

    if (book) {
      this.navigateToReader(book, bookId, reader, explicitBookType);
      return;
    }

    this.ensureBookDetail(bookId, false).then(detail => {
      this.navigateToReader(detail, bookId, reader, explicitBookType);
    }).catch(() => {
      console.error('Book not found:', bookId);
    });
  }

  private navigateToReader(book: Book, bookId: number, reader?: 'epub-streaming' | 'epub-blob', explicitBookType?: BookType): void {

    const bookType: BookType | undefined = explicitBookType ?? book.primaryFile?.bookType;
    const isAlternativeFormat = explicitBookType && explicitBookType !== book.primaryFile?.bookType;
    const selectedFile = isAlternativeFormat
      ? book.alternativeFormats?.find(file => file.bookType === explicitBookType)
      : book.primaryFile;

    if (bookType === 'DOC' && selectedFile?.documentParseStatus === 'UNREADABLE') {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('book.bookService.toast.unreadableDocumentSummary'),
        detail: this.t.translate('book.bookService.toast.unreadableDocumentDetail')
      });
      return;
    }

    let baseUrl: string | null = null;
    const queryParams: Partial<{streaming: boolean; bookType: BookType}> = {};

    switch (bookType) {
      case 'PDF':
        baseUrl = 'pdf-reader';
        break;

      case 'EPUB':
        baseUrl = 'ebook-reader';
        if (reader === 'epub-blob') {
          queryParams['streaming'] = false;
        }
        break;

      // DOC is here because Word documents are served to this reader as a rendition synthesised
      // on the server, not because the file itself is an ebook.
      case 'FB2':
      case 'MOBI':
      case 'AZW3':
      case 'DOC':
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

  handleLibraryScanComplete(): void {
    invalidateBooksQuery(this.queryClient);
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

  handleBookRecommendationsUpdate(bookId: number): void {
    this.bookSocketService.handleBookRecommendationsUpdate(bookId);
  }
}
