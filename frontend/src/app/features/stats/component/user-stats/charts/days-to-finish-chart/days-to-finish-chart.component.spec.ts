import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {type StatsFinishedBook} from '../../../../data/stats-response.models';
import {createStatsChart} from '../../../shared/stats-chart-testing';
import {DaysToFinishChartComponent} from './days-to-finish-chart.component';

function book(id: number, days: number): StatsFinishedBook {
  return {title: `Book ${id}`, days};
}

describe('DaysToFinishChartComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows every band in order with the empty ones at zero and names the quickest and longest', () => {
    const chart = createStatsChart(DaysToFinishChartComponent, {
      year: 2026,
      rows: [
        {band: '3-months-or-more', books: 1, titles: [book(1, 92)]},
        {band: 'under-a-week', books: 6, titles: [book(8, 0), book(2, 1), book(3, 2), book(5, 3), book(6, 4)]},
        {band: '1-to-2-months', books: 2, titles: [book(4, 28), book(7, 50)]},
      ],
      median: 4,
      quickest: book(8, 0),
      longest: book(1, 92),
    });

    expect(chart.bands().map(({id, books}) => [id, books])).toEqual([
      ['under-a-week', 6],
      ['1-to-2-weeks', 0],
      ['2-to-4-weeks', 0],
      ['1-to-2-months', 2],
      ['2-to-3-months', 0],
      ['3-months-or-more', 1],
    ]);
    expect(chart.insights()).toMatchObject([
      {label: 'Books Finished', value: '9'},
      {label: 'Median', value: '4 days'},
      {label: 'Quickest', value: '0 days', detail: 'Book 8'},
      {label: 'Longest', value: '92 days', detail: 'Book 1'},
    ]);
  });
});
