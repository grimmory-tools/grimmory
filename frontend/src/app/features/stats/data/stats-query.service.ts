import {HttpClient} from '@angular/common/http';
import {effect, inject, Service} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {QUERY_DEFAULTS, toAbortablePromise} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {toCollectionHttpParams} from '../../book/data/book-query-params';
import {normalizeStatsScope, type StatsScopeParams, statsQueryKeys} from './stats-query-keys';
import {
  type ProfileRow,
  type StatsActivityMonth,
  type StatsBookFlow,
  type StatsDecadeCell,
  type StatsFinishTimes,
  type StatsLengthCell,
  type SeriesPage,
  type SeriesSort,
  type SeriesStateFilter,
  type SeriesSummary,
  type StatsDistribution,
  type StatsDistributionDimension,
  type StatsDistributionValues,
  type StatsGenreTime,
  type StatsItemDimension,
  type StatsItemMeasure,
  type StatsRows,
  type StatsDays,
  type StatsSlots,
  type StatsComparisonCell,
  type StatsTopName,
  type StatsWeek,
} from './stats-response.models';

const DISTRIBUTION_PAGES = {
  format: 'library',
  language: 'library',
  metadata_score: 'library',
  page_band: 'library',
  publication_year: 'library',
  status: 'reading',
  rating: 'reading',
  stopped: 'reading',
} as const satisfies Record<StatsDistributionDimension, 'library' | 'reading'>;

const NO_ROWS = {rows: []} as const;
const NO_SLOTS: StatsSlots = {rows: [], days: [0, 0, 0, 0, 0, 0, 0]};
const NO_BOOK_FLOW: StatsBookFlow = {byYear: [], byRating: []};
const NO_WEEK: StatsWeek = {rows: [], truncated: false};
const NO_DAYS: StatsDays = {rows: [], streaks: {current: 0, longest: 0, readingDays: 0}};
const NO_FINISH_TIMES: StatsFinishTimes = {rows: [], median: null, quickest: null, longest: null};
const NO_SERIES: SeriesSummary = {top: [], total: 0, averageCompletion: null, measured: 0, completed: 0, inProgress: 0, notStarted: 0};
const NO_SERIES_PAGE: SeriesPage = {rows: [], matching: 0};

export interface SeriesTableRequest {
  readonly search: string;
  readonly state: SeriesStateFilter;
  readonly sort: SeriesSort;
  readonly page: number;
}

@Service()
export class StatsQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/stats`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: statsQueryKeys.all()});
      }
    });
  }

  distribution<D extends StatsDistributionDimension>(scope: StatsScopeParams, dimension: D) {
    return this.query<StatsDistribution<StatsDistributionValues[D]>>(`${DISTRIBUTION_PAGES[dimension]}/distribution/${dimension}`, scope, NO_ROWS);
  }

  length(scope: StatsScopeParams) {
    return this.query<StatsRows<StatsLengthCell>>('reading/length', scope, NO_ROWS);
  }

  comparison(scope: StatsScopeParams) {
    return this.query<StatsRows<StatsComparisonCell>>('reading/comparison', scope, NO_ROWS);
  }

  decade(scope: StatsScopeParams) {
    return this.query<StatsRows<StatsDecadeCell>>('reading/decade', scope, NO_ROWS);
  }

  bookFlow(scope: StatsScopeParams) {
    return this.query<StatsBookFlow>('reading/flow', scope, NO_BOOK_FLOW);
  }

  top(scope: StatsScopeParams, kind: StatsItemDimension, by: StatsItemMeasure) {
    return this.query<StatsRows<StatsTopName>>(`library/top/${kind}`, scope, NO_ROWS, {by});
  }

  activity(scope: StatsScopeParams) {
    return this.query<StatsRows<StatsActivityMonth>>('reading/activity', scope, NO_ROWS);
  }

  slots(scope: StatsScopeParams, year: number | null, month: number | null = null) {
    const period = year === null ? 'all' : month === null ? `${year}` : `${year}-${pad(month)}`;
    return this.query<StatsSlots>('reading/slots', scope, NO_SLOTS, {period});
  }

  days(scope: StatsScopeParams, year: number) {
    return this.query<StatsDays>('reading/days', scope, NO_DAYS, {year: `${year}`});
  }

  genres(scope: StatsScopeParams) {
    return this.query<StatsRows<StatsGenreTime>>('reading/genres', scope, NO_ROWS);
  }

  week(scope: StatsScopeParams, year: number, week: number) {
    return this.query<StatsWeek>('reading/week', scope, NO_WEEK, {week: `${year}-W${pad(week)}`});
  }

  daysToFinish(scope: StatsScopeParams, year: number) {
    return this.query<StatsFinishTimes>('reading/days-to-finish', scope, NO_FINISH_TIMES, {year: `${year}`});
  }

  seriesSummary(scope: StatsScopeParams) {
    return this.query<SeriesSummary>('reading/series/summary', scope, NO_SERIES);
  }

  series(scope: StatsScopeParams, table: SeriesTableRequest) {
    return this.query<SeriesPage>('reading/series', scope, NO_SERIES_PAGE, {
      ...(table.search.trim() ? {search: table.search.trim()} : {}),
      ...(table.state !== 'all' ? {state: table.state} : {}),
      ...(table.sort !== 'progress' ? {sort: table.sort} : {}),
      ...(table.page ? {page: table.page.toString()} : {}),
    });
  }

  profile(scope: StatsScopeParams) {
    return this.query<StatsRows<ProfileRow>>('reading/profile', scope, NO_ROWS);
  }

  private query<T>(path: string, scopeParams: StatsScopeParams, empty: T, extra: Readonly<Record<string, string>> = {}) {
    const scope = normalizeStatsScope(scopeParams);
    const params = toCollectionHttpParams(scope).appendAll({tz: scope.tz, ...extra});

    return {
      options: queryOptions({
        queryKey: statsQueryKeys.endpoint(path, scope, extra),
        queryFn: ({signal}) => toAbortablePromise(this.http.get<T>(`${this.baseUrl}/${path}`, {params}), signal),
        ...QUERY_DEFAULTS,
      }),
      empty,
    };
  }
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}
