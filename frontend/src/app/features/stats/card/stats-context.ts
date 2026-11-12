import {InjectionToken, type Signal} from '@angular/core';

import {type StatsScopeParams} from '../data/stats-query-keys';

export type StatsPageId = 'library' | 'reading';

export const STATS_PAGE = new InjectionToken<StatsPageId>('STATS_PAGE');

export interface StatsContext {
  readonly scope: Signal<StatsScopeParams>;
}

export const STATS_CONTEXT = new InjectionToken<StatsContext>('STATS_CONTEXT');

export type StatsCardState = 'loading' | 'error' | 'empty' | 'ready';

export interface StatsCardContext {
  readonly state: Signal<StatsCardState>;
  readonly description: Signal<string | undefined>;
}

export const STATS_CARD = new InjectionToken<StatsCardContext>('STATS_CARD');
