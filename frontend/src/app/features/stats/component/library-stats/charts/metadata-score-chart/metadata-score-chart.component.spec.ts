import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../../../book/model/book.model';
import {BookService} from '../../../../../book/service/book.service';
import {MetadataScoreChartComponent} from './metadata-score-chart.component';
import {LibraryFilterService} from '../../service/library-filter.service';

const EXPECTED_SCORE_COLORS = [
  '#16A34A',
  '#22C55E',
  '#F59E0B',
  '#F97316',
  '#DC2626',
];

interface MetadataScoreTooltipContext {
  parsed: number;
  dataset: {
    data: number[];
  };
}

describe('MetadataScoreChartComponent', () => {
  const books = signal<Book[]>([]);
  const isBooksLoading = signal(false);
  const selectedLibrary = signal<number | null>(null);
  const translate = vi.fn((key: string, params?: Record<string, number | string>) => {
    if (!params) {
      return key;
    }

    return `${key}|${Object.entries(params).map(([name, value]) => `${name}=${value}`).join('|')}`;
  });

  beforeEach(() => {
    books.set([]);
    isBooksLoading.set(false);
    selectedLibrary.set(null);
    translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        {provide: BookService, useValue: {books, isBooksLoading}},
        {provide: LibraryFilterService, useValue: {selectedLibrary}},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createBook(id: number, libraryId: number, metadataMatchScore: number | null): Book {
    return {
      id,
      title: `Book ${id}`,
      libraryId,
      libraryName: `Library ${libraryId}`,
      metadataMatchScore,
    };
  }

  function createComponent(): MetadataScoreChartComponent {
    return TestBed.runInInjectionContext(() => new MetadataScoreChartComponent());
  }

  function getTooltipLabelCallback(component: MetadataScoreChartComponent): ((context: MetadataScoreTooltipContext) => string) | undefined {
    return component.chartOptions?.plugins?.tooltip?.callbacks?.label as
      | ((context: MetadataScoreTooltipContext) => string)
      | undefined;
  }

  it('returns empty computed stats while loading and when the selected library has no valid scores', () => {
    books.set([
      createBook(1, 1, 95),
      createBook(2, 2, -1),
      createBook(3, 2, null),
    ]);
    selectedLibrary.set(2);
    isBooksLoading.set(true);

    const component = createComponent();

    expect(component.totalBooks()).toBe(0);
    expect(component.averageScore()).toBe(0);
    expect(component.scoreStats()).toEqual([]);
    expect(component.chartData()).toEqual({labels: [], datasets: []});

    isBooksLoading.set(false);

    expect(component.totalBooks()).toBe(0);
    expect(component.averageScore()).toBe(0);
    expect(component.scoreStats()).toEqual([]);
    expect(component.chartData()).toEqual({labels: [], datasets: []});
  });

  it('buckets scores by translated range, filters to the selected library, and rounds the average score', () => {
    books.set([
      createBook(1, 1, 95),
      createBook(2, 1, 89),
      createBook(3, 1, 70),
      createBook(4, 1, 69),
      createBook(5, 1, 50),
      createBook(6, 1, 49),
      createBook(7, 1, 25),
      createBook(8, 1, 24),
      createBook(9, 1, 0),
      createBook(10, 1, -1),
      createBook(11, 1, null),
      createBook(12, 2, 100),
    ]);
    selectedLibrary.set(1);

    const component = createComponent();

    expect(component.totalBooks()).toBe(9);
    expect(component.averageScore()).toBe(52);
    expect(component.scoreStats()).toEqual([
      {range: 'statsLibrary.metadataScore.excellent', count: 1, percentage: 100 / 9, color: '#16A34A'},
      {range: 'statsLibrary.metadataScore.good', count: 2, percentage: 200 / 9, color: '#22C55E'},
      {range: 'statsLibrary.metadataScore.fair', count: 2, percentage: 200 / 9, color: '#F59E0B'},
      {range: 'statsLibrary.metadataScore.poor', count: 2, percentage: 200 / 9, color: '#F97316'},
      {range: 'statsLibrary.metadataScore.veryPoor', count: 2, percentage: 200 / 9, color: '#DC2626'},
    ]);
  });

  it('builds deterministic doughnut chart labels, counts, and colors from computed score stats', () => {
    books.set([
      createBook(1, 1, 95),
      createBook(2, 1, 89),
      createBook(3, 1, 70),
      createBook(4, 1, 69),
      createBook(5, 1, 50),
      createBook(6, 1, 49),
      createBook(7, 1, 25),
      createBook(8, 1, 24),
      createBook(9, 1, 0),
    ]);

    const component = createComponent();
    const chartData = component.chartData();
    const dataset = chartData.datasets[0];

    expect(chartData.labels).toEqual([
      'statsLibrary.metadataScore.excellent',
      'statsLibrary.metadataScore.good',
      'statsLibrary.metadataScore.fair',
      'statsLibrary.metadataScore.poor',
      'statsLibrary.metadataScore.veryPoor',
    ]);

    expect(dataset).toBeDefined();
    if (!dataset) {
      throw new Error('Expected a chart dataset');
    }

    expect(dataset.data).toEqual([1, 2, 2, 2, 2]);
    expect(dataset.backgroundColor).toEqual(EXPECTED_SCORE_COLORS);
    expect(dataset.borderColor).toEqual(EXPECTED_SCORE_COLORS.map(() => 'rgba(255, 255, 255, 0.2)'));
    expect(dataset.borderWidth).toBe(2);
    expect(dataset.hoverBorderColor).toBe('#ffffff');
    expect(dataset.hoverBorderWidth).toBe(3);
  });

  it('formats the tooltip callback from computed chart data without a live Chart.js instance', () => {
    books.set([
      createBook(1, 1, 96),
      createBook(2, 1, 90),
      createBook(3, 1, 12),
    ]);

    const component = createComponent();
    const chartData = component.chartData();
    const dataset = chartData.datasets[0];
    const tooltipLabel = getTooltipLabelCallback(component);

    expect(dataset).toBeDefined();
    expect(tooltipLabel).toBeDefined();
    if (!dataset || !tooltipLabel) {
      throw new Error('Expected tooltip callback and dataset');
    }

    expect(tooltipLabel({
      parsed: 2,
      dataset: {data: dataset.data as number[]},
    })).toBe('statsLibrary.metadataScore.tooltipLabel|value=2|percentage=66.7');
  });
});
