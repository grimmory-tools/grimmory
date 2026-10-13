import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {createStatsChart} from '../../../shared/stats-chart-testing';
import {PublicationTimelineChartComponent} from './publication-timeline-chart.component';

describe('PublicationTimelineChartComponent', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('keeps every decade from the oldest to the newest, empty ones included, and counts the books without a year', () => {
    const chart = createStatsChart(PublicationTimelineChartComponent, {
      rows: [
        {value: null, books: 8},
        {value: 1813, books: 1},
        {value: 1905, books: 2},
        {value: 1931, books: 1},
        {value: 2014, books: 3},
      ],
    });

    expect(chart.decades()).toEqual([
      {decade: 'before-1900', books: 1},
      ...['1900', '1910', '1920', '1930', '1940', '1950', '1960', '1970', '1980', '1990', '2000', '2010'].map((decade) => ({
        decade,
        books: {1900: 2, 1930: 1, 2010: 3}[decade] ?? 0,
      })),
    ]);
    expect(chart.insights()).toMatchObject([
      {label: 'Oldest', value: '1813'},
      {label: 'Newest', value: '2014'},
      {label: 'Most Books', value: '2010s'},
      {label: 'No Year', value: '8'},
    ]);
  });
});
