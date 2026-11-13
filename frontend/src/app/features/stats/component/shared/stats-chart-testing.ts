import {signal, type Type} from '@angular/core';
import {TestBed} from '@angular/core/testing';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {type StatsChart} from '../../card/stats-chart-definition';
import {STATS_CARD, type StatsCardState} from '../../card/stats-context';

export const STATS_CARD_TESTING_PROVIDER = {
  provide: STATS_CARD,
  useValue: {state: signal<StatsCardState>('ready').asReadonly(), description: signal('').asReadonly()},
};

type ChartData<T> = T extends StatsChart<infer TData> ? TData : never;

export function createStatsChart<T extends StatsChart<unknown>>(component: Type<T>, data: ChartData<T>): T {
  TestBed.configureTestingModule({imports: [component, getTranslocoModule({preloadLangs: true})]});
  TestBed.overrideComponent(component, {set: {template: '', imports: []}});
  const fixture = TestBed.createComponent(component);
  fixture.componentRef.setInput('data', data);
  return fixture.componentInstance;
}

