import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {createStatsChart} from '../../../shared/stats-chart-testing';
import {ReadingDropOffChartComponent} from './reading-drop-off-chart.component';

describe('ReadingDropOffChartComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('draws the share of books no longer being read that reached each progress value', () => {
    const chart = createStatsChart(ReadingDropOffChartComponent, {rows: [
      {value: 0, books: 6},
      {value: 1, books: 1},
      {value: 50, books: 1},
      {value: 100, books: 2},
    ]});

    expect(chart.points()).toEqual([
      {progress: 0, percent: 100},
      {progress: 1, percent: 100},
      {progress: 50, percent: 75},
      {progress: 100, percent: 50},
    ]);
    expect(chart.insights()).toMatchObject([{label: 'Completion Rate', value: '50%'}]);
  });
});
