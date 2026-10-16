import {HttpTestingController, type TestRequest} from '@angular/common/http/testing';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushQueryAsync} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {StatsCardComponent} from '../card/stats-card.component';
import {STATS_CONTEXT} from '../card/stats-context';
import {LIBRARY_STATS_CHARTS} from './library-stats.registry';
import {READING_STATS_CHARTS} from './reading-stats.registry';

function emptyResponse({request}: TestRequest): object {
  if (request.url.endsWith('/reading/days')) return {rows: [], streaks: {current: 0, longest: 0, readingDays: 0}};
  if (request.url.endsWith('/reading/week')) return {rows: [], truncated: false};
  if (request.url.endsWith('/reading/slots')) return {rows: [], days: [0, 0, 0, 0, 0, 0, 0]};
  if (request.url.endsWith('/reading/flow')) return {byYear: [], byRating: []};
  if (request.url.endsWith('/reading/series/summary')) {
    return {top: [], total: 0, averageCompletion: null, measured: 0, completed: 0, inProgress: 0, notStarted: 0};
  }
  if (request.url.endsWith('/reading/series')) return {rows: [], matching: 0};
  return {rows: []};
}

describe('stats charts without data', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date(2026, 0, 2, 12));
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: createAuthServiceStub()},
        {provide: STATS_CONTEXT, useValue: {scope: signal({facets: {}, facetLogic: 'and', tz: 'UTC'})}},
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it.each([...LIBRARY_STATS_CHARTS, ...READING_STATS_CHARTS].map((chart) => [chart.id, chart] as const))(
    'draws %s in its own layout while loading and without data, with its plot standing in',
    async (_id, chart) => {
      const fixture = TestBed.createComponent(StatsCardComponent);
      fixture.componentRef.setInput('definition', chart);
      await flushQueryAsync();
      const card: HTMLElement = fixture.nativeElement;

      expect(card.querySelector('app-stats-chart-plot [role="status"]')).not.toBeNull();
      expect(card.querySelector('tanstack-chart')).toBeNull();

      for (let round = 0; round < 2; round++) {
        for (const request of http.match(() => true)) request.flush(emptyResponse(request));
        await flushQueryAsync();
      }

      expect(card.querySelector('[role="status"]')).toBeNull();
      expect(card.querySelector('app-message')).toBeNull();
      expect(fixture.componentInstance.state()).toBe('empty');
      expect(card.querySelector('app-stats-chart-plot p')?.textContent?.trim()).toBe('No data available');
      expect(card.querySelector('tanstack-chart')).toBeNull();
    },
  );
});
