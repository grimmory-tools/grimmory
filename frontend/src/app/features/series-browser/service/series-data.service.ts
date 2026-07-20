import {computed, inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {lastValueFrom} from 'rxjs';
import {injectInfiniteQuery} from '@tanstack/angular-query-experimental';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {AppPageResponse} from '../../book/model/app-book.model';
import {ReadStatus} from '../../book/model/book.model';
import {AppSeriesSummary, SeriesSummary} from '../model/series.model';

const SERIES_PAGE_SIZE = 50;

export type SeriesBrowserStatusFilter = 'all' | 'not-started' | 'in-progress' | 'completed' | 'abandoned';
export type SeriesBrowserSort = 'name-asc' | 'name-desc' | 'book-count' | 'progress' | 'recently-read' | 'recently-added';

export interface ApiSeriesSort {
  field: string;
  dir: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class SeriesDataService {

  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  private readonly seriesUrl = `${API_CONFIG.BASE_URL}/api/v1/app/series`;
  private readonly token = this.authService.token;
  private readonly enabled = signal(false);
  private readonly search = signal('');
  private readonly status = signal<SeriesBrowserStatusFilter>('all');
  private readonly sort = signal<SeriesBrowserSort>('name-asc');

  readonly seriesQuery = injectInfiniteQuery(() => ({
    queryKey: ['app-series', this.search(), this.status(), this.sort()] as const,
    queryFn: ({pageParam}: {pageParam: number}) => {
      const params = this.buildParams(pageParam);
      return lastValueFrom(this.http.get<AppPageResponse<AppSeriesSummary>>(this.seriesUrl, {params}));
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage: AppPageResponse<AppSeriesSummary>) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    enabled: !!this.token() && this.enabled(),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  }));

  readonly allSeries = computed(() =>
    this.seriesQuery.data()?.pages.flatMap(page => page.content.map(toSeriesSummary)) ?? []
  );
  readonly totalSeries = computed(() =>
    this.seriesQuery.data()?.pages[0]?.totalElements ?? this.allSeries().length
  );
  readonly isLoading = computed(() => this.seriesQuery.isLoading());
  readonly isError = computed(() => this.seriesQuery.isError());
  readonly isFetchingNextPage = computed(() => this.seriesQuery.isFetchingNextPage());

  enable(): void {
    this.enabled.set(true);
  }

  setSearch(value: string): void {
    this.search.set(value);
  }

  setStatus(value: SeriesBrowserStatusFilter): void {
    this.status.set(value);
  }

  setSort(value: SeriesBrowserSort): void {
    this.sort.set(value);
  }

  fetchNextPage(): void {
    if (this.seriesQuery.hasNextPage() && !this.seriesQuery.isFetchingNextPage()) {
      void this.seriesQuery.fetchNextPage();
    }
  }

  private buildParams(page: number): HttpParams {
    const apiSort = toApiSeriesSort(this.sort());
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', SERIES_PAGE_SIZE.toString())
      .set('sort', apiSort.field)
      .set('dir', apiSort.dir);

    const search = this.search().trim();
    if (search) {
      params = params.set('search', search);
    }

    const status = this.status();
    if (status !== 'all') {
      params = params.set('status', status);
    }

    return params;
  }
}

export function toApiSeriesSort(sort: SeriesBrowserSort): ApiSeriesSort {
  switch (sort) {
    case 'name-asc':
      return {field: 'name', dir: 'asc'};
    case 'name-desc':
      return {field: 'name', dir: 'desc'};
    case 'book-count':
      return {field: 'bookCount', dir: 'desc'};
    case 'progress':
      return {field: 'readProgress', dir: 'desc'};
    case 'recently-read':
      return {field: 'lastReadTime', dir: 'desc'};
    case 'recently-added':
      return {field: 'recentlyAdded', dir: 'desc'};
  }
}

export function toSeriesSummary(summary: AppSeriesSummary): SeriesSummary {
  const bookCount = summary.bookCount;
  const readCount = summary.booksRead;
  return {
    seriesName: summary.seriesName,
    authors: summary.authors ?? [],
    categories: [],
    bookCount,
    readCount,
    progress: bookCount > 0 ? readCount / bookCount : 0,
    seriesStatus: toReadStatus(summary.seriesStatus),
    lastReadTime: summary.lastReadTime,
    coverBooks: summary.coverBooks ?? [],
    addedOn: summary.latestAddedOn,
  };
}

function toReadStatus(value: string | null): ReadStatus {
  return Object.values(ReadStatus).includes(value as ReadStatus)
    ? value as ReadStatus
    : ReadStatus.UNREAD;
}
