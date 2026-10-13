import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {createStatsChart} from '../../../shared/stats-chart-testing';
import {BookRatingChartComponent} from './book-rating-chart.component';

describe('BookRatingChartComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('rates each band in stars and picks the best-rated range with at least five rated books', () => {
    const chart = createStatsChart(BookRatingChartComponent, {
      kind: 'length',
      rows: [
        {band: '101-to-200', rating: 6, books: 4},
        {band: '101-to-200', rating: 8, books: 1},
        {band: 'over-1000', rating: 9, books: 2},
        {band: '301-to-500', rating: 10, books: 4},
        {band: '301-to-500', rating: 7, books: 2},
      ],
    });

    expect(chart.columns()).toEqual([
      {column: '1–100', books: 0, rating: null},
      {column: '101–200', books: 5, rating: 3.2},
      {column: '201–300', books: 0, rating: null},
      {column: '301–500', books: 6, rating: 4.5},
      {column: '501–750', books: 0, rating: null},
      {column: '751–1000', books: 0, rating: null},
      {column: '1001+', books: 2, rating: 4.5},
    ]);
    expect(chart.best()).toEqual({column: '301–500', books: 6, rating: 4.5});
  });
});
