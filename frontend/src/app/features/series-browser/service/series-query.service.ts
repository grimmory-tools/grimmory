import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {infiniteQueryOptions} from '@tanstack/angular-query';

import {API_CONFIG} from '../../../core/config/api-config';
import {httpGet, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AppSeriesPageResponse} from '../model/series.model';

export interface SeriesBrowseParams {
  readonly search: string;
  readonly status: string;
  readonly sort: string;
}

const PAGE_SIZE = 40;

@Service()
export class SeriesQueryService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/app/series`;

  infinitePage(params: SeriesBrowseParams) {
    const sort = toServerSort(params.sort);
    const search = params.search.trim();
    return infiniteQueryOptions({
      queryKey: ['app-series', search, params.status, params.sort],
      queryFn: ({pageParam, signal}) => {
        const requestParams: Record<string, string | number> = {
          page: pageParam,
          size: PAGE_SIZE,
          sort: sort.key,
          dir: sort.direction,
        };
        if (search) requestParams['search'] = search;
        if (params.status !== 'all') requestParams['status'] = params.status;
        return httpGet<AppSeriesPageResponse>(this.http, this.url, signal, requestParams);
      },
      initialPageParam: 0,
      getNextPageParam: page => page.hasNext ? page.page + 1 : undefined,
      ...QUERY_DEFAULTS,
    });
  }
}

function toServerSort(value: string): {key: string; direction: 'asc' | 'desc'} {
  switch (value) {
    case 'name-asc': return {key: 'name', direction: 'asc'};
    case 'name-desc': return {key: 'name', direction: 'desc'};
    case 'book-count': return {key: 'bookCount', direction: 'desc'};
    case 'progress': return {key: 'readProgress', direction: 'desc'};
    case 'recently-read': return {key: 'lastReadTime', direction: 'desc'};
    default: return {key: 'recentlyAdded', direction: 'desc'};
  }
}
