import {computed, inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {concatMap, from, lastValueFrom, map, Observable, toArray} from 'rxjs';
import {injectInfiniteQuery, injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {
  AppBookFilters,
  AppBookQuickSearchResult,
  AppCatalogSummary,
  AppBookSort,
  AppBookSummary,
  AppFilterOptions,
  AppPageResponse,
} from '../model/app-book.model';
import {Book, BOOK_TYPES, BookFile, BookType, ReadStatus} from '../model/book.model';

const PAGE_SIZE = 50;
// Cap the pages the infinite query retains so a full-list refetch (and memory) stays bounded even
// after scrolling deep into a large library. Evicted pages are refetched on demand via
// getPreviousPageParam/getNextPageParam as the user scrolls back toward them.
const MAX_RETAINED_PAGES = 20;
const BOOK_TYPE_SET = new Set<BookType>(BOOK_TYPES);
const READ_STATUSES = new Set<ReadStatus>(Object.values(ReadStatus) as ReadStatus[]);

@Injectable({providedIn: 'root'})
export class AppBooksApiService {

  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);

  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/app/books`;
  private readonly seriesUrl = `${API_CONFIG.BASE_URL}/api/v1/app/series`;
  private readonly filterOptionsUrl = `${API_CONFIG.BASE_URL}/api/v1/app/filter-options`;
  private readonly token = this.authService.token;

  private readonly _filters = signal<AppBookFilters>({});
  private readonly _sort = signal<AppBookSort>({field: 'addedOn', dir: 'desc'});
  private readonly _search = signal('');
  private readonly _booksEnabled = signal(false);
  private readonly _filterOptionsEnabled = signal(false);
  private readonly _globalFilterOptionsEnabled = signal(false);
  private readonly _catalogSummaryEnabled = signal(false);
  private globalFilterOptionsEnableScheduled = false;
  private catalogSummaryEnableScheduled = false;
  private readonly mappedBooks = new Map<number, { fingerprint: string; book: Book }>();

  readonly booksQuery = injectInfiniteQuery(() => ({
    queryKey: ['app-books', this._filters(), this._sort(), this._search()] as const,
    queryFn: ({pageParam}: {pageParam: number}) => {
      const params = this.buildParams(pageParam);
      return lastValueFrom(this.http.get<AppPageResponse<AppBookSummary>>(this.booksUrl, {params}));
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage: AppPageResponse<AppBookSummary>) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    getPreviousPageParam: (firstPage: AppPageResponse<AppBookSummary>) =>
      firstPage.hasPrevious ? firstPage.page - 1 : undefined,
    maxPages: MAX_RETAINED_PAGES,
    enabled: !!this.token() && this._booksEnabled(),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  }));

  readonly filterOptionsQuery = injectQuery(() => ({
    queryKey: this.filterOptionsQueryKey(),
    queryFn: () => {
      const filters = this._filters();
      let params = new HttpParams();
      if (filters.libraryId) params = params.set('libraryId', filters.libraryId.toString());
      if (filters.shelfId) params = params.set('shelfId', filters.shelfId.toString());
      if (filters.magicShelfId) params = params.set('magicShelfId', filters.magicShelfId.toString());
      return lastValueFrom(this.http.get<AppFilterOptions>(this.filterOptionsUrl, {params}));
    },
    enabled: !!this.token() && this._filterOptionsEnabled(),
    staleTime: 10 * 60_000,
  }));

  readonly globalFilterOptionsQuery = injectQuery(() => ({
    queryKey: ['app-filter-options', 'global'] as const,
    queryFn: () => lastValueFrom(this.http.get<AppFilterOptions>(this.filterOptionsUrl)),
    enabled: !!this.token() && this._globalFilterOptionsEnabled(),
    staleTime: 10 * 60_000,
  }));

  readonly catalogSummaryQuery = injectQuery(() => ({
    queryKey: ['app-catalog-summary'] as const,
    queryFn: () => lastValueFrom(this.http.get<AppCatalogSummary>(`${this.booksUrl}/summary`)),
    enabled: !!this.token() && this._catalogSummaryEnabled(),
    staleTime: 10 * 60_000,
  }));

  /** Accumulated books from all loaded pages, mapped to the Book interface. */
  readonly books = computed<Book[]>(() => {
    const data = this.booksQuery.data();
    if (!data) return [];
    const visibleIds = new Set<number>();
    const books = data.pages.flatMap(page => page.content.map(summary => {
      visibleIds.add(summary.id);
      return this.summaryToStableBook(summary);
    }));
    for (const id of this.mappedBooks.keys()) {
      if (!visibleIds.has(id)) {
        this.mappedBooks.delete(id);
      }
    }
    return books;
  });

  readonly totalElements = computed(() => {
    const data = this.booksQuery.data();
    if (!data || data.pages.length === 0) return 0;
    return data.pages[0].totalElements;
  });

  readonly hasNextPage = computed(() => this.booksQuery.hasNextPage());
  readonly hasPreviousPage = computed(() => this.booksQuery.hasPreviousPage());
  readonly isLoading = computed(() => this.booksQuery.isLoading());
  readonly isFetchingNextPage = computed(() => this.booksQuery.isFetchingNextPage());
  readonly isFetchingPreviousPage = computed(() => this.booksQuery.isFetchingPreviousPage());

  // Global index of the first retained book. With maxPages, the earliest pages are evicted as the
  // user scrolls down, so the accumulated books() array is a sliding window, not a prefix from 0:
  // callers must map a global (virtual) index to books()[globalIndex - firstLoadedIndex()].
  readonly firstLoadedIndex = computed(() => {
    const data = this.booksQuery.data();
    if (!data || data.pages.length === 0) return 0;
    // Pages before the first retained one are always full (eviction only removes from the front,
    // never the partial last page), and the client fixes the page size via PAGE_SIZE, so the global
    // index of books()[0] is exactly page * PAGE_SIZE.
    return data.pages[0].page * PAGE_SIZE;
  });
  readonly isError = computed(() => this.booksQuery.isError());
  readonly hasData = computed(() => this.booksQuery.data() !== undefined);
  readonly error = computed<string | null>(() => {
    if (!this.booksQuery.isError()) return null;
    const err = this.booksQuery.error();
    return err instanceof Error ? err.message : 'Failed to load books';
  });

  private readonly _filterOptions = computed(() => this.filterOptionsQuery.data() ?? null);
  readonly filterOptions = this._filterOptions;
  readonly globalFilterOptions = computed(() => this.globalFilterOptionsQuery.data() ?? null);
  readonly catalogSummary = computed(() => {
    this.enableCatalogSummary();
    return this.catalogSummaryQuery.data() ?? null;
  });

  // Individual signals for each filter type for more granular reactivity
  readonly authorOptions = computed(() => this._filterOptions()?.authors ?? []);
  readonly languageOptions = computed(() => this._filterOptions()?.languages ?? []);
  readonly categoryOptions = computed(() => this._filterOptions()?.categories ?? []);
  readonly seriesOptions = computed(() => this._filterOptions()?.series ?? []);
  readonly publisherOptions = computed(() => this._filterOptions()?.publishers ?? []);
  readonly tagOptions = computed(() => this._filterOptions()?.tags ?? []);
  readonly moodOptions = computed(() => this._filterOptions()?.moods ?? []);
  readonly narratorOptions = computed(() => this._filterOptions()?.narrators ?? []);
  readonly ageRatingOptions = computed(() => this._filterOptions()?.ageRatings ?? []);
  readonly contentRatingOptions = computed(() => this._filterOptions()?.contentRatings ?? []);
  readonly matchScoreOptions = computed(() => this._filterOptions()?.matchScores ?? []);
  readonly publishedYearOptions = computed(() => this._filterOptions()?.publishedYears ?? []);
  readonly fileSizeOptions = computed(() => this._filterOptions()?.fileSizes ?? []);
  readonly personalRatingOptions = computed(() => this._filterOptions()?.personalRatings ?? []);
  readonly amazonRatingOptions = computed(() => this._filterOptions()?.amazonRatings ?? []);
  readonly goodreadsRatingOptions = computed(() => this._filterOptions()?.goodreadsRatings ?? []);
  readonly hardcoverRatingOptions = computed(() => this._filterOptions()?.hardcoverRatings ?? []);
  readonly lubimyczytacRatingOptions = computed(() => this._filterOptions()?.lubimyczytacRatings ?? []);
  readonly ranobedbRatingOptions = computed(() => this._filterOptions()?.ranobedbRatings ?? []);
  readonly audibleRatingOptions = computed(() => this._filterOptions()?.audibleRatings ?? []);
  readonly pageCountOptions = computed(() => this._filterOptions()?.pageCounts ?? []);
  readonly shelfStatusOptions = computed(() => this._filterOptions()?.shelfStatuses ?? []);
  readonly readStatusOptions = computed(() => this._filterOptions()?.readStatuses ?? []);
  readonly fileTypeOptions = computed(() => this._filterOptions()?.fileTypes ?? []);
  readonly comicCharacterOptions = computed(() => this._filterOptions()?.comicCharacters ?? []);
  readonly comicTeamOptions = computed(() => this._filterOptions()?.comicTeams ?? []);
  readonly comicLocationOptions = computed(() => this._filterOptions()?.comicLocations ?? []);
  readonly comicCreatorOptions = computed(() => this._filterOptions()?.comicCreators ?? []);
  readonly shelfOptions = computed(() => this._filterOptions()?.shelves ?? []);
  readonly libraryOptions = computed(() => this._filterOptions()?.libraries ?? []);

  setFilters(filters: AppBookFilters): void {
    if (!appBookFiltersEqual(this._filters(), filters)) {
      this._filters.set(filters);
    }
  }

  setBooksEnabled(enabled: boolean): void {
    this._booksEnabled.set(enabled);
  }

  setSort(sort: AppBookSort): void {
    const current = this._sort();
    if (current.field !== sort.field || current.dir !== sort.dir) {
      this._sort.set(sort);
    }
  }

  setSearch(search: string): void {
    if (this._search() !== search) {
      this._search.set(search);
    }
  }

  setFilterOptionsEnabled(enabled: boolean): void {
    this._filterOptionsEnabled.set(enabled);
  }

  enableGlobalFilterOptions(): void {
    if (this._globalFilterOptionsEnabled() || this.globalFilterOptionsEnableScheduled) return;
    this.globalFilterOptionsEnableScheduled = true;
    queueMicrotask(() => {
      this.globalFilterOptionsEnableScheduled = false;
      this._globalFilterOptionsEnabled.set(true);
    });
  }

  private enableCatalogSummary(): void {
    if (this._catalogSummaryEnabled() || this.catalogSummaryEnableScheduled) return;
    this.catalogSummaryEnableScheduled = true;
    queueMicrotask(() => {
      this.catalogSummaryEnableScheduled = false;
      this._catalogSummaryEnabled.set(true);
    });
  }

  searchBooks(query: string, size = 20): Observable<AppPageResponse<AppBookSummary>> {
    const params = new HttpParams()
      .set('q', query.trim())
      .set('page', '0')
      .set('size', Math.max(1, size).toString());

    return this.http.get<AppPageResponse<AppBookSummary>>(`${this.booksUrl}/search`, {params});
  }

  quickSearchBooks(query: string, limit = 50): Observable<AppBookQuickSearchResult[]> {
    const params = new HttpParams()
      .set('q', query.trim())
      .set('limit', Math.max(1, Math.min(limit, 50)).toString());

    return this.http.get<AppBookQuickSearchResult[]>(`${this.booksUrl}/quick-search`, {params});
  }

  semanticSearchBooks(query: string, limit = 50): Observable<AppBookQuickSearchResult[]> {
    const params = new HttpParams()
      .set('q', query.trim())
      .set('limit', Math.max(1, Math.min(limit, 50)).toString());

    return this.http.get<AppBookQuickSearchResult[]>(`${this.booksUrl}/semantic-search`, {params});
  }

  getContinueReading(limit = 10): Observable<Book[]> {
    return this.getSummaryList('continue-reading', limit);
  }

  getContinueListening(limit = 10): Observable<Book[]> {
    return this.getSummaryList('continue-listening', limit);
  }

  getRecentlyAdded(limit = 10): Observable<Book[]> {
    return this.getSummaryList('recently-added', limit);
  }

  getRandom(limit = 10): Observable<Book[]> {
    const params = new HttpParams().set('page', '0').set('size', Math.max(1, limit).toString());
    return this.http.get<AppPageResponse<AppBookSummary>>(`${this.booksUrl}/random`, {params}).pipe(
      map(response => response.content.map(summaryToBook)),
    );
  }

  getPage(filters: AppBookFilters, sort: AppBookSort, size = PAGE_SIZE, search = ''): Observable<Book[]> {
    let params = this.buildFilterParamsFor(filters, search);
    params = params
      .set('page', '0')
      .set('size', Math.max(1, size).toString())
      .set('sort', sort.field)
      .set('dir', sort.dir);
    return this.http.get<AppPageResponse<AppBookSummary>>(this.booksUrl, {params}).pipe(
      map(response => response.content.map(summaryToBook)),
    );
  }

  getSeriesBooks(seriesName: string): Observable<Book[]> {
    const params = new HttpParams().set('name', seriesName);
    return this.http.get<AppBookSummary[]>(`${this.seriesUrl}/books/all`, {params}).pipe(
      map(summaries => summaries.map(summaryToBook)),
    );
  }

  getCount(filters: AppBookFilters): Observable<number> {
    const params = this.buildFilterParamsFor(filters, '')
      .set('page', '0')
      .set('size', '1');
    return this.http.get<AppPageResponse<AppBookSummary>>(this.booksUrl, {params}).pipe(
      map(response => response.totalElements),
    );
  }

  findExistingIsbns(libraryId: number, isbns: string[]): Observable<Set<string>> {
    const params = new HttpParams().set('libraryId', libraryId.toString());
    return this.http.post<string[]>(`${this.booksUrl}/isbn-matches`, isbns, {params}).pipe(
      map(matches => new Set(matches)),
    );
  }

  getBooksByIds(bookIds: number[]): Observable<Book[]> {
    if (bookIds.length === 0) return from([[]]);
    const batches: number[][] = [];
    for (let offset = 0; offset < bookIds.length; offset += 500) {
      batches.push(bookIds.slice(offset, offset + 500));
    }

    return from(batches).pipe(
      concatMap(batch => this.http.post<AppBookSummary[]>(`${this.booksUrl}/summaries`, batch)),
      toArray(),
      map(responses => {
        const booksById = new Map(
          responses.flatMap(response => response).map(summary => [summary.id, summaryToBook(summary)]),
        );
        return bookIds.flatMap(bookId => {
          const book = booksById.get(bookId);
          return book ? [book] : [];
        });
      }),
    );
  }

  fetchNextPage(): void {
    this.booksQuery.fetchNextPage();
  }

  fetchPreviousPage(): void {
    this.booksQuery.fetchPreviousPage();
  }

  /** Fetch all book IDs matching the current filters (no pagination). */
  fetchAllBookIds(): Observable<number[]> {
    const params = this.buildFilterParams();
    return this.http.get<number[]>(`${this.booksUrl}/ids`, {params});
  }

  /** Invalidate the books query to force a refresh from the server. */
  invalidate(): void {
    this.queryClient.invalidateQueries({queryKey: ['app-books']});
    this.queryClient.invalidateQueries({queryKey: ['app-filter-options']});
    this.queryClient.invalidateQueries({queryKey: ['app-catalog-summary']});
  }

  private filterOptionsQueryKey(): readonly unknown[] {
    const {libraryId, shelfId, magicShelfId} = this._filters();
    return libraryId || shelfId || magicShelfId
      ? ['app-filter-options', libraryId, shelfId, magicShelfId] as const
      : ['app-filter-options', 'global'] as const;
  }

  private buildParams(page: number): HttpParams {
    const sort = this._sort();

    return this.buildFilterParams()
      .set('page', page.toString())
      .set('size', PAGE_SIZE.toString())
      .set('sort', sort.field)
      .set('dir', sort.dir);
  }

  private buildFilterParams(): HttpParams {
    return this.buildFilterParamsFor(this._filters(), this._search());
  }

  private buildFilterParamsFor(filters: AppBookFilters, search: string): HttpParams {

    let params = new HttpParams();

    if (search) params = params.set('search', search);
    if (filters.libraryId) params = params.set('libraryId', filters.libraryId.toString());
    if (filters.shelfId) params = params.set('shelfId', filters.shelfId.toString());
    if (filters.magicShelfId) params = params.set('magicShelfId', filters.magicShelfId.toString());
    if (filters.unshelved) params = params.set('unshelved', 'true');
    if (filters.minRating != null) params = params.set('minRating', filters.minRating.toString());
    if (filters.maxRating != null) params = params.set('maxRating', filters.maxRating.toString());
    if (filters.filterMode) params = params.set('filterMode', filters.filterMode);

    // Array filters: use append() to produce repeated query params (e.g. ?authors=A&authors=B)
    const arrayFilters: [string, string[] | undefined][] = [
      ['authors', filters.authors],
      ['language', filters.language],
      ['series', filters.series],
      ['category', filters.category],
      ['publisher', filters.publisher],
      ['tag', filters.tag],
      ['mood', filters.mood],
      ['narrator', filters.narrator],
      ['ageRating', filters.ageRating],
      ['contentRating', filters.contentRating],
      ['matchScore', filters.matchScore],
      ['publishedDate', filters.publishedDate],
      ['fileSize', filters.fileSize],
      ['personalRating', filters.personalRating],
      ['amazonRating', filters.amazonRating],
      ['goodreadsRating', filters.goodreadsRating],
      ['hardcoverRating', filters.hardcoverRating],
      ['lubimyczytacRating', filters.lubimyczytacRating],
      ['ranobedbRating', filters.ranobedbRating],
      ['audibleRating', filters.audibleRating],
      ['pageCount', filters.pageCount],
      ['shelfStatus', filters.shelfStatus],
      ['comicCharacter', filters.comicCharacter],
      ['comicTeam', filters.comicTeam],
      ['comicLocation', filters.comicLocation],
      ['comicCreator', filters.comicCreator],
      ['shelves', filters.shelves],
      ['libraries', filters.libraries],
      ['status', filters.status],
      ['fileType', filters.fileType],
    ];
    for (const [key, values] of arrayFilters) {
      if (values?.length) {
        for (const v of values) {
          params = params.append(key, v);
        }
      }
    }

    return params;
  }

  private getSummaryList(path: string, limit: number): Observable<Book[]> {
    const params = new HttpParams().set('limit', Math.max(1, limit).toString());
    return this.http.get<AppBookSummary[]>(`${this.booksUrl}/${path}`, {params}).pipe(
      map(summaries => summaries.map(summaryToBook)),
    );
  }

  private summaryToStableBook(summary: AppBookSummary): Book {
    const fingerprint = summaryFingerprint(summary);
    const cached = this.mappedBooks.get(summary.id);
    if (cached?.fingerprint === fingerprint) {
      return cached.book;
    }

    const book = summaryToBook(summary);
    this.mappedBooks.set(summary.id, {fingerprint, book});
    return book;
  }

}

function summaryFingerprint(summary: AppBookSummary): string {
  return JSON.stringify(summary);
}

/**
 * Maps a server-side AppBookSummary to a Book-shaped object
 * compatible with book browser card and table views.
 */
export function summaryToBook(summary: AppBookSummary): Book {
  return {
    id: summary.id,
    libraryId: summary.libraryId,
    libraryName: '',
    readStatus: summaryToReadStatus(summary.readStatus),
    personalRating: summary.personalRating,
    addedOn: summary.addedOn ?? undefined,
    lastReadTime: summary.lastReadTime ?? undefined,
    isPhysical: summary.isPhysical ?? false,
    fileSizeKb: summary.fileSizeKb ?? undefined,
    metadataMatchScore: summary.metadataMatchScore,
    metadata: {
      bookId: summary.id,
      title: summary.title,
      authors: summary.authors ?? [],
      publisher: summary.publisher ?? undefined,
      seriesName: summary.seriesName ?? undefined,
      seriesNumber: summary.seriesNumber,
      categories: summary.categories ?? [],
      tags: summary.tags ?? [],
      moods: summary.moods ?? [],
      language: summary.language ?? undefined,
      narrator: summary.narrator ?? undefined,
      isbn13: summary.isbn13 ?? undefined,
      isbn10: summary.isbn10 ?? undefined,
      coverUpdatedOn: summary.coverUpdatedOn ?? undefined,
      audiobookCoverUpdatedOn: summary.audiobookCoverUpdatedOn ?? undefined,
      publishedDate: summary.publishedDate ?? undefined,
      pageCount: summary.pageCount,
      ageRating: summary.ageRating,
      contentRating: summary.contentRating,
      amazonRating: summary.amazonRating,
      amazonReviewCount: summary.amazonReviewCount,
      goodreadsRating: summary.goodreadsRating,
      goodreadsReviewCount: summary.goodreadsReviewCount,
      hardcoverRating: summary.hardcoverRating,
      hardcoverReviewCount: summary.hardcoverReviewCount,
      ranobedbRating: summary.ranobedbRating,
      lubimyczytacRating: summary.lubimyczytacRating,
      audibleRating: summary.audibleRating,
      audibleReviewCount: summary.audibleReviewCount,
      allMetadataLocked: summary.allMetadataLocked ?? false,
    },
    primaryFile: summaryToPrimaryFile(summary),
    pdfProgress: summary.readProgress != null
      ? {page: 0, percentage: summary.readProgress}
      : undefined,
    shelves: [],
  };
}

function summaryToPrimaryFile(summary: AppBookSummary): BookFile | undefined {
  if (summary.primaryFileId == null) return undefined;

  const primaryFile: BookFile = {
    id: summary.primaryFileId,
    bookId: summary.id,
    extension: summaryToPrimaryFileExtension(summary),
    fileSizeKb: summary.fileSizeKb ?? undefined,
    fileName: summary.primaryFileName ?? undefined,
  };

  const bookType = summaryToBookType(summary.primaryFileType);
  if (bookType) {
    primaryFile.bookType = bookType;
  }
  if (summary.primaryFileDocumentParseStatus) {
    primaryFile.documentParseStatus = summary.primaryFileDocumentParseStatus;
  }

  return primaryFile;
}

function summaryToBookType(value: string | null): BookType | undefined {
  return value != null && BOOK_TYPE_SET.has(value as BookType) ? value as BookType : undefined;
}

function summaryToReadStatus(value: string | null): ReadStatus {
  const readStatus = value as ReadStatus;
  return value != null && READ_STATUSES.has(readStatus) ? readStatus : ReadStatus.UNSET;
}

function summaryToPrimaryFileExtension(summary: AppBookSummary): string | undefined {
  const fileName = summary.primaryFileName;
  if (fileName) {
    const dotIndex = fileName.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex < fileName.length - 1) {
      return fileName.slice(dotIndex + 1).toLowerCase();
    }
  }

  return summary.primaryFileType?.toLowerCase();
}

function appBookFiltersEqual(left: AppBookFilters, right: AppBookFilters): boolean {
  const keys = new Set([...Object.keys(left), ...Object.keys(right)] as (keyof AppBookFilters)[]);
  for (const key of keys) {
    if (!filterValueEqual(left[key], right[key])) {
      return false;
    }
  }
  return true;
}

function filterValueEqual(left: AppBookFilters[keyof AppBookFilters], right: AppBookFilters[keyof AppBookFilters]): boolean {
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) {
      return false;
    }
    return left.every((value, index) => value === right[index]);
  }
  return left === right;
}
