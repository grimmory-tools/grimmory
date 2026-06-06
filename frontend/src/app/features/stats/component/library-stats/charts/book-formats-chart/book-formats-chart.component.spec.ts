import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {Book, BookType} from '../../../../../book/model/book.model';
import {BookService} from '../../../../../book/service/book.service';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookFormatsChartComponent} from './book-formats-chart.component';

const EXPECTED_FORMAT_COLORS = [
  '#0D9488',
  '#E11D48',
  '#6B7280',
  '#6B7280',
];

interface BookFormatsTooltipContext {
  parsed: number;
  dataset: {
    data: number[];
  };
  label: string;
}

describe('BookFormatsChartComponent', () => {
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

  function createBook(id: number, libraryId: number, bookType?: BookType): Book {
    return {
      id,
      title: `Book ${id}`,
      libraryId,
      libraryName: `Library ${libraryId}`,
      primaryFile: bookType === undefined
        ? undefined
        : {id: id * 10, bookId: id, bookType},
    };
  }

  function createComponent(): BookFormatsChartComponent {
    return TestBed.runInInjectionContext(() => new BookFormatsChartComponent());
  }

  function getTooltipLabelCallback(component: BookFormatsChartComponent): ((context: BookFormatsTooltipContext) => string) | undefined {
    return component.chartOptions?.plugins?.tooltip?.callbacks?.label as
      | ((context: BookFormatsTooltipContext) => string)
      | undefined;
  }

  it('filters books to the selected library and aggregates primary formats into descending stats', () => {
    books.set([
      createBook(1, 1, 'EPUB'),
      createBook(2, 1, 'EPUB'),
      createBook(3, 1, 'EPUB'),
      createBook(4, 1, 'EPUB'),
      createBook(5, 1, 'PDF'),
      createBook(6, 1, 'PDF'),
      createBook(7, 1, 'PDF'),
      createBook(8, 1, 'AUDIOBOOK'),
      createBook(9, 1, 'AUDIOBOOK'),
      createBook(10, 1),
      createBook(11, 2, 'CBX'),
      createBook(12, 2, 'CBX'),
    ]);
    selectedLibrary.set(1);

    const component = createComponent();

    expect(component.totalBooks()).toBe(10);
    expect(component.formatStats()).toEqual([
      {format: 'EPUB', count: 4, percentage: 40},
      {format: 'PDF', count: 3, percentage: 30},
      {format: 'AUDIOBOOK', count: 2, percentage: 20},
      {format: 'Unknown', count: 1, percentage: 10},
    ]);
  });

  it('builds deterministic pie chart labels, counts, and colors from computed format stats', () => {
    books.set([
      createBook(1, 1, 'EPUB'),
      createBook(2, 1, 'EPUB'),
      createBook(3, 1, 'EPUB'),
      createBook(4, 1, 'EPUB'),
      createBook(5, 1, 'PDF'),
      createBook(6, 1, 'PDF'),
      createBook(7, 1, 'PDF'),
      createBook(8, 1, 'AUDIOBOOK'),
      createBook(9, 1, 'AUDIOBOOK'),
      createBook(10, 1),
    ]);

    const component = createComponent();
    const chartData = component.chartData();
    const dataset = chartData.datasets[0];

    expect(chartData.labels).toEqual(['EPUB', 'PDF', 'AUDIOBOOK', 'Unknown']);

    expect(dataset).toBeDefined();
    if (!dataset) {
      throw new Error('Expected a chart dataset');
    }

    expect(dataset.data).toEqual([4, 3, 2, 1]);
    expect(dataset.backgroundColor).toEqual(EXPECTED_FORMAT_COLORS);
    expect(dataset.borderColor).toEqual(EXPECTED_FORMAT_COLORS.map(() => 'rgba(255, 255, 255, 0.2)'));
    expect(dataset.borderWidth).toBe(2);
    expect(dataset.hoverBorderColor).toBe('#ffffff');
    expect(dataset.hoverBorderWidth).toBe(3);
  });

  it('formats the tooltip callback from computed chart data without a live Chart.js instance', () => {
    books.set([
      createBook(1, 1, 'EPUB'),
      createBook(2, 1, 'EPUB'),
      createBook(3, 1, 'EPUB'),
      createBook(4, 1, 'PDF'),
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
      parsed: 3,
      dataset: {data: dataset.data as number[]},
      label: 'EPUB',
    })).toBe('statsLibrary.bookFormats.tooltipLabel|label=EPUB|value=3|percentage=75.0');
  });
});
