import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it, vi} from 'vitest';

import {type RunningMonth} from '../../../shared/stats-chart-data';
import {createStatsChart} from '../../../shared/stats-chart-testing';
import {BooksAddedVsFinishedChartComponent} from './books-added-vs-finished-chart.component';

const MONTHS: RunningMonth[] = [
  {month: '2026-01', added: 0, finished: 2, totalAdded: 0, totalFinished: 2, backlog: 0},
  {month: '2026-02', added: 3, finished: 0, totalAdded: 3, totalFinished: 2, backlog: 3},
  {month: '2026-03', added: 1, finished: 4, totalAdded: 4, totalFinished: 6, backlog: 0},
];

describe('BooksAddedVsFinishedChartComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('reads the backlog, its change since the same month last year and the month with most added, leaving out the change until the history spans a year', () => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date(2027, 1, 20, 12));
    const chart = createStatsChart(BooksAddedVsFinishedChartComponent, MONTHS);

    expect(chart.yearAgo()?.month).toBe('2026-02');
    expect(chart.insights()).toMatchObject([
      {label: 'Current Backlog', value: '0'},
      {label: 'Last 12 Months', value: '-3'},
      {label: 'Books Finished', value: '6'},
      {label: 'Most Added', value: '3'},
    ]);

    TestBed.resetTestingModule();
    vi.setSystemTime(new Date(2026, 2, 20, 12));
    const recent = createStatsChart(BooksAddedVsFinishedChartComponent, MONTHS);

    expect(recent.yearAgo()).toBeNull();
    expect(recent.insights()).toMatchObject([
      {label: 'Current Backlog', value: '0'},
      {label: 'Books Finished', value: '6'},
      {label: 'Most Added', value: '3'},
    ]);
    vi.useRealTimers();
  });
});
