import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../../../book/model/book.model';
import {BookService} from '../../../../../book/service/book.service';
import {PageCountChartComponent} from './page-count-chart.component';
import {LibraryFilterService} from '../../service/library-filter.service';

const EXPECTED_PAGE_RANGE_LABELS = [
  '0-100',
  '101-200',
  '201-300',
  '301-500',
  '501-750',
  '751-1000',
  '1000+',
];

const EXPECTED_PAGE_RANGE_COLORS = [
  '#06B6D4',
  '#0EA5E9',
  '#3B82F6',
  '#6366F1',
  '#8B5CF6',
  '#A855F7',
  '#D946EF',
];

interface PageCountTooltipTitleContext {
  label: string;
}

interface PageCountTooltipLabelContext {
  parsed: {
    y: number;
  };
}

describe('PageCountChartComponent', () => {
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

  function createBook(id: number, libraryId: number, pageCount?: number | null): Book {
    return {
      id,
      title: `Book ${id}`,
      libraryId,
      libraryName: `Library ${libraryId}`,
      metadata: pageCount === undefined
        ? {bookId: id}
        : {bookId: id, pageCount},
    };
  }

  function createComponent(): PageCountChartComponent {
    return TestBed.runInInjectionContext(() => new PageCountChartComponent());
  }

  function getTooltipTitleCallback(component: PageCountChartComponent): ((context: PageCountTooltipTitleContext[]) => string) | undefined {
    return component.chartOptions?.plugins?.tooltip?.callbacks?.title as
      | ((context: PageCountTooltipTitleContext[]) => string)
      | undefined;
  }

  function getTooltipLabelCallback(component: PageCountChartComponent): ((context: PageCountTooltipLabelContext) => string) | undefined {
    return component.chartOptions?.plugins?.tooltip?.callbacks?.label as
      | ((context: PageCountTooltipLabelContext) => string)
      | undefined;
  }

  it('filters books to the selected library and counts only positive page totals into the fixed buckets', () => {
    books.set([
      createBook(1, 1, 50),
      createBook(2, 1, 1000),
      createBook(3, 1, 0),
      createBook(4, 1, null),
      createBook(5, 1),
      createBook(6, 2, 75),
      createBook(7, 2, 1200),
    ]);
    selectedLibrary.set(1);

    const component = createComponent();
    const chartData = component.chartData();
    const dataset = chartData.datasets[0];

    expect(component.totalBooks()).toBe(2);
    expect(dataset).toBeDefined();
    if (!dataset) {
      throw new Error('Expected a chart dataset');
    }

    expect(dataset.data).toEqual([1, 0, 0, 0, 0, 1, 0]);
  });

  it('builds deterministic bar chart labels, counts, and colors for every page-count range', () => {
    books.set([
      createBook(1, 1, 25),
      createBook(2, 1, 100),
      createBook(3, 1, 101),
      createBook(4, 1, 200),
      createBook(5, 1, 201),
      createBook(6, 1, 300),
      createBook(7, 1, 301),
      createBook(8, 1, 500),
      createBook(9, 1, 501),
      createBook(10, 1, 750),
      createBook(11, 1, 751),
      createBook(12, 1, 1000),
      createBook(13, 1, 1001),
    ]);

    const component = createComponent();
    const chartData = component.chartData();
    const dataset = chartData.datasets[0];

    expect(chartData.labels).toEqual(EXPECTED_PAGE_RANGE_LABELS);
    expect(dataset).toBeDefined();
    if (!dataset) {
      throw new Error('Expected a chart dataset');
    }

    expect(dataset.data).toEqual([2, 2, 2, 2, 2, 2, 1]);
    expect(dataset.backgroundColor).toEqual(EXPECTED_PAGE_RANGE_COLORS);
    expect(dataset.borderColor).toEqual(EXPECTED_PAGE_RANGE_COLORS.map(() => 'rgba(255, 255, 255, 0.2)'));
    expect(dataset.borderWidth).toBe(1);
    expect(dataset.borderRadius).toBe(4);
    expect(dataset.barPercentage).toBe(0.8);
    expect(dataset.categoryPercentage).toBe(0.7);
  });

  it('formats the tooltip title and singular-versus-plural labels without a live Chart.js instance', () => {
    const component = createComponent();
    const tooltipTitle = getTooltipTitleCallback(component);
    const tooltipLabel = getTooltipLabelCallback(component);

    expect(tooltipTitle).toBeDefined();
    expect(tooltipLabel).toBeDefined();
    if (!tooltipTitle || !tooltipLabel) {
      throw new Error('Expected tooltip callbacks');
    }

    expect(tooltipTitle([{label: '301-500'}])).toBe('statsLibrary.pageCount.tooltipTitle|label=301-500');
    expect(tooltipLabel({parsed: {y: 1}})).toBe('statsLibrary.pageCount.tooltipLabel|value=1');
    expect(tooltipLabel({parsed: {y: 3}})).toBe('statsLibrary.pageCount.tooltipLabelPlural|value=3');
  });
});
