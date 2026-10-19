import {
  type BookCollectionFilterParams,
  normalizeBookCollectionFilterParams,
} from '../../book/data/book-query-params';

export interface StatsScopeParams extends BookCollectionFilterParams {
  readonly tz: string;
}

export function normalizeStatsScope(params: StatsScopeParams): StatsScopeParams {
  return {...normalizeBookCollectionFilterParams(params), tz: params.tz};
}

export const statsQueryKeys = {
  all: () => ['stats'] as const,
  endpoint: (path: string, scope: StatsScopeParams, params: Readonly<Record<string, string>>) =>
    [...statsQueryKeys.all(), path, scope, params] as const,
};

export type StatsQueryKey = ReturnType<typeof statsQueryKeys.endpoint>;
