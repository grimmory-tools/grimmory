import {type Signal, type Type} from '@angular/core';
import {type QueryFunctionContext} from '@tanstack/angular-query-experimental';

import {type StatsQueryKey, type StatsScopeParams} from '../data/stats-query-keys';
import {type StatsQueryService} from '../data/stats-query.service';
import {type StatsItemDimension, type StatsItemMeasure} from '../data/stats-response.models';

export type StatsChartSize = 'small' | 'medium' | 'wide' | 'full';

export type StatsFilterKind = 'none' | 'year' | 'yearMonth' | 'yearWeekday' | 'week' | 'item';

export const STATS_ITEM_NAMES = {
  author: 'authors',
  genre: 'genres',
  series: 'series',
  publisher: 'publishers',
  tag: 'tags',
  mood: 'moods',
} as const satisfies Record<StatsItemDimension, string>;

export interface StatsFilterValues {
  readonly none: object;
  readonly year: {readonly year: number};
  readonly yearMonth: {readonly year: number | null; readonly month: number | null};
  readonly yearWeekday: {readonly year: number | null; readonly weekday: number | null};
  readonly week: {readonly year: number; readonly week: number};
  readonly item: {readonly item: StatsItemDimension; readonly by: StatsItemMeasure};
}

export type StatsCardParams<K extends StatsFilterKind = StatsFilterKind> =
  StatsScopeParams & StatsFilterValues[K];

export interface StatsQuery<TResponse> {
  readonly options: {
    readonly queryKey: StatsQueryKey;
    readonly queryFn?: (context: QueryFunctionContext<StatsQueryKey>) => TResponse | Promise<TResponse>;
  };
  readonly empty: TResponse;
}

export interface StatsChart<TData> {
  readonly data: Signal<TData>;
}

interface StatsChartBase<TData, K extends StatsFilterKind> {
  readonly id: string;
  readonly nameKey: string;
  readonly titleKey: string;
  readonly descriptionKey?: string;
  readonly size: StatsChartSize;
  readonly filter: K;
  isEmpty(data: NoInfer<TData>): boolean;
  readonly chart: Type<StatsChart<TData>>;
}

interface DirectStatsChart<TData, K extends StatsFilterKind> extends StatsChartBase<TData, K> {
  query(params: StatsCardParams<K>, stats: StatsQueryService): StatsQuery<NoInfer<TData>>;
  readonly select?: undefined;
}

interface SelectedStatsChart<TResponse, TData, K extends StatsFilterKind> extends StatsChartBase<TData, K> {
  query(params: StatsCardParams<K>, stats: StatsQueryService): StatsQuery<TResponse>;
  select(response: TResponse, params: StatsCardParams<K>): NoInfer<TData>;
}

export type StatsChartDefinition<TResponse = unknown, TData = TResponse, K extends StatsFilterKind = StatsFilterKind> =
  | DirectStatsChart<TData, K>
  | SelectedStatsChart<TResponse, TData, K>;

export function defineStatsChart<TData, K extends StatsFilterKind>(definition: DirectStatsChart<TData, K>): StatsChartDefinition;
export function defineStatsChart<TResponse, TData, K extends StatsFilterKind>(
  definition: SelectedStatsChart<TResponse, TData, K>,
): StatsChartDefinition;
export function defineStatsChart(definition: StatsChartDefinition): StatsChartDefinition {
  return definition;
}
