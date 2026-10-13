import {signal, type Type} from '@angular/core';
import {TestBed} from '@angular/core/testing';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {STATS_CARD, type StatsCardState} from '../../card/stats-context';

export const STATS_CARD_TESTING_PROVIDER = {
  provide: STATS_CARD,
  useValue: {state: signal<StatsCardState>('ready').asReadonly(), description: signal('').asReadonly()},
};

export function createStatsChart<T>(component: Type<T>, data: unknown): T {
  TestBed.configureTestingModule({imports: [component, getTranslocoModule({preloadLangs: true})]});
  TestBed.overrideComponent(component, {set: {template: '', imports: []}});
  const fixture = TestBed.createComponent(component);
  fixture.componentRef.setInput('data', data);
  return fixture.componentInstance;
}

