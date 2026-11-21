import {
  type BrowseCollectionFilterParams,
  normalizeBrowseCollectionFilterParams,
} from '../../../core/data/browse-query-params';
import {
  type BookQueryFacetKey,
  isBookQueryFacetKey,
} from '../../book/data/book-query-params';

export interface StatsScopeParams extends BrowseCollectionFilterParams<BookQueryFacetKey> {
  readonly tz: string;
}

export function normalizeStatsScope(params: StatsScopeParams): StatsScopeParams {
  return {...normalizeBrowseCollectionFilterParams(params, isBookQueryFacetKey), tz: params.tz};
}

export const statsQueryKeys = {
  all: () => ['stats'] as const,
  endpoint: (path: string, scope: StatsScopeParams, params: Readonly<Record<string, string>>) =>
    [...statsQueryKeys.all(), path, scope, params] as const,
};

export type StatsQueryKey = ReturnType<typeof statsQueryKeys.endpoint>;
