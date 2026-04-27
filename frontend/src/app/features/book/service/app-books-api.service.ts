import {computed, inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {lastValueFrom, Observable} from 'rxjs';
import {injectInfiniteQuery, injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {
  AppBookFilters,
  AppBookSort,
  AppBookSummary,
  AppFilterOptions,
  AppPageResponse,
} from '../model/app-book.model';
import {Book, BookType, ReadStatus} from '../model/book.model';

const PAGE_SIZE = 50;

@Injectable({providedIn: 'root'})
export class AppBooksApiService {

  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);

  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/app/books`;
  private readonly filterOptionsUrl = `${API_CONFIG.BASE_URL}/api/v1/app/filter-options`;
  private readonly token = this.authService.token;

  private readonly _filters = signal<AppBookFilters>({});
  private readonly _sort = signal<AppBookSort>({field: 'addedOn', dir: 'desc'});
  private readonly _search = signal('');

  readonly booksQuery = injectInfiniteQuery(() => ({
    queryKey: ['app-books', this._filters(), this._sort(), this._search()] as const,
    queryFn: ({pageParam}: {pageParam: number}) => {
      const params = this.buildParams(pageParam);
      return lastValueFrom(this.http.get<AppPageResponse<AppBookSummary>>(this.booksUrl, {params}));
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage: AppPageResponse<AppBookSummary>) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    enabled: !!this.token(),
    staleTime: 5 * 60_000,
  }));

  readonly filterOptionsQuery = injectQuery(() => ({
    queryKey: ['app-filter-options', this._filters().libraryId, this._filters().shelfId, this._filters().magicShelfId] as const,
    queryFn: () => {
      const filters = this._filters();
      let params = new HttpParams();
      if (filters.libraryId) params = params.set('libraryId', filters.libraryId.toString());
      if (filters.shelfId) params = params.set('shelfId', filters.shelfId.toString());
      if (filters.magicShelfId) params = params.set('magicShelfId', filters.magicShelfId.toString());
      return lastValueFrom(this.http.get<AppFilterOptions>(this.filterOptionsUrl, {params}));
    },
    enabled: !!this.token(),
    staleTime: 10 * 60_000,
  }));

  /** Accumulated books from all loaded pages, mapped to the Book interface. */
  readonly books = computed<Book[]>(() => {
    const data = this.booksQuery.data();
    if (!data) return [];
    return data.pages.flatMap(page => page.content.map(summaryToBook));
  }, {
    equal: (a, b) => {
      if (a.length !== b.length) return false;
      for (let i = 0; i < a.length; i++) {
        if (a[i].id !== b[i].id) return false;
        if (a[i].metadata?.coverUpdatedOn !== b[i].metadata?.coverUpdatedOn) return false;
        if (a[i].metadata?.audiobookCoverUpdatedOn !== b[i].metadata?.audiobookCoverUpdatedOn) return false;
      }
      return true;
    }
  });

  readonly totalElements = computed(() => {
    const data = this.booksQuery.data();
    if (!data || data.pages.length === 0) return 0;
    return data.pages[0].totalElements;
  });

  readonly hasNextPage = computed(() => this.booksQuery.hasNextPage());
  readonly isLoading = computed(() => this.booksQuery.isPending());
  readonly isFetchingNextPage = computed(() => this.booksQuery.isFetchingNextPage());
  readonly isError = computed(() => this.booksQuery.isError());
  readonly error = computed<string | null>(() => {
    if (!this.booksQuery.isError()) return null;
    const err = this.booksQuery.error();
    return err instanceof Error ? err.message : 'Failed to load books';
  });

  private readonly _filterOptions = computed(() => this.filterOptionsQuery.data() ?? null);
  readonly filterOptions = this._filterOptions;

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
    if (JSON.stringify(this._filters()) !== JSON.stringify(filters)) {
      this._filters.set(filters);
    }
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

  searchBooks(query: string, size = 20): Observable<AppPageResponse<AppBookSummary>> {
    const params = new HttpParams()
      .set('q', query.trim())
      .set('page', '0')
      .set('size', Math.max(1, size).toString());

    return this.http.get<AppPageResponse<AppBookSummary>>(`${this.booksUrl}/search`, {params});
  }

  fetchNextPage(): void {
    this.booksQuery.fetchNextPage();
  }

  /** Fetch all book IDs matching the current filters (no pagination). */
  fetchAllBookIds(): Observable<number[]> {
    const params = this.buildFilterParams();
    return this.http.get<number[]>(`${this.booksUrl}/ids`, {params});
  }

  /** Invalidate the books query to force a refresh from the server. */
  invalidate(): void {
    void this.queryClient.invalidateQueries({queryKey: ['app-books']});
    void this.queryClient.invalidateQueries({queryKey: ['app-filter-options']});
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
    const filters = this._filters();
    const search = this._search();

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
}

/**
 * Maps a server-side AppBookSummary to a Book-shaped object
 * compatible with BookCardComponent's @Input() book property.
 */
function summaryToBook(summary: AppBookSummary): Book {
  return {
    id: summary.id,
    libraryId: summary.libraryId,
    readStatus: (summary.readStatus as ReadStatus) ?? ReadStatus.UNSET,
    personalRating: summary.personalRating ?? 0,
    addedOn: summary.addedOn,
    lastReadTime: summary.lastReadTime,
    isPhysical: summary.isPhysical ?? false,
    fileSizeKb: summary.fileSizeKb ?? undefined,
    metadataMatchScore: summary.metadataMatchScore,
    metadata: {
      bookId: summary.id,
      title: summary.title,
      authors: summary.authors ?? [],
      seriesName: summary.seriesName,
      seriesNumber: summary.seriesNumber,
      coverUpdatedOn: summary.coverUpdatedOn,
      audiobookCoverUpdatedOn: summary.audiobookCoverUpdatedOn,
      publishedDate: summary.publishedDate ?? undefined,
      pageCount: summary.pageCount,
      ageRating: summary.ageRating,
      contentRating: summary.contentRating,
    },
    primaryFile: summary.primaryFileType
      ? {bookType: summary.primaryFileType as BookType, extension: summary.primaryFileType.toLowerCase()}
      : null,
    pdfProgress: summary.readProgress != null
      ? {page: 0, percentage: summary.readProgress}
      : null,
    epubProgress: null,
    cbxProgress: null,
    shelves: [],
  } as unknown as Book;
}
