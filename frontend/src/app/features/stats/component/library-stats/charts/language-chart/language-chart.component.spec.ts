import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {Book} from '../../../../../book/model/book.model';
import {BookService} from '../../../../../book/service/book.service';
import {LanguageChartComponent} from './language-chart.component';
import {LibraryFilterService} from '../../service/library-filter.service';

const EXPECTED_LANGUAGE_COLORS = [
  '#2563EB',
  '#0D9488',
  '#7C3AED',
  '#DC2626',
  '#F59E0B',
  '#16A34A',
  '#EC4899',
  '#8B5CF6',
  '#06B6D4',
  '#EA580C',
  '#6366F1',
  '#14B8A6',
  '#F43F5E',
  '#84CC16',
  '#A855F7',
];

interface LanguageTooltipContext {
  parsed: number;
  dataset: {
    data: number[];
  };
  label: string;
}

describe('LanguageChartComponent', () => {
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

  function createBook(id: number, libraryId: number, language?: string): Book {
    return {
      id,
      title: `Book ${id}`,
      libraryId,
      libraryName: `Library ${libraryId}`,
      metadata: language === undefined
        ? {bookId: id}
        : {bookId: id, language},
    };
  }

  function createComponent(): LanguageChartComponent {
    return TestBed.runInInjectionContext(() => new LanguageChartComponent());
  }

  function getTooltipLabelCallback(component: LanguageChartComponent): ((context: LanguageTooltipContext) => string) | undefined {
    return component.chartOptions?.plugins?.tooltip?.callbacks?.label as
      | ((context: LanguageTooltipContext) => string)
      | undefined;
  }

  it('returns empty stats and chart data while books are still loading', () => {
    books.set([
      createBook(1, 1, ' EN '),
      createBook(2, 1, 'spa'),
    ]);
    isBooksLoading.set(true);

    const component = createComponent();

    expect(component.totalBooks()).toBe(0);
    expect(component.booksWithLanguage()).toBe(0);
    expect(component.languageStats()).toEqual([]);
    expect(component.chartData()).toEqual({labels: [], datasets: []});
  });

  it('aggregates exact normalized keys, shapes mapped and fallback labels, and filters books to the selected library', () => {
    books.set([
      createBook(1, 1, ' EN '),
      createBook(2, 1, 'en'),
      createBook(3, 1, 'eng'),
      createBook(4, 1, ' English '),
      createBook(5, 1, 'spa'),
      createBook(6, 1, 'Spanish'),
      createBook(7, 1, 'klingon'),
      createBook(8, 1, '   '),
      createBook(9, 1),
      createBook(10, 2, 'en'),
    ]);
    selectedLibrary.set(1);

    const component = createComponent();

    expect(component.totalBooks()).toBe(9);
    expect(component.booksWithLanguage()).toBe(7);
    expect(component.languageStats()).toEqual([
      {language: 'en', displayName: 'English', count: 2, percentage: (2 / 7) * 100},
      {language: 'eng', displayName: 'English', count: 1, percentage: (1 / 7) * 100},
      {language: 'english', displayName: 'English', count: 1, percentage: (1 / 7) * 100},
      {language: 'spa', displayName: 'Spanish', count: 1, percentage: (1 / 7) * 100},
      {language: 'spanish', displayName: 'Spanish', count: 1, percentage: (1 / 7) * 100},
      {language: 'klingon', displayName: 'Klingon', count: 1, percentage: (1 / 7) * 100},
    ]);
  });

  it('keeps only the top fifteen languages and exposes deterministic pie-chart labels, counts, and colors', () => {
    const generatedBooks: Book[] = [];
    let nextId = 1;

    Array.from({length: 17}, (_, index) => {
      const language = `lang${String(index + 1).padStart(2, '0')}`;
      const count = 17 - index;

      Array.from({length: count}, () => {
        generatedBooks.push(createBook(nextId, 1, language));
        nextId += 1;
      });
    });

    books.set(generatedBooks);

    const component = createComponent();
    const chartData = component.chartData();
    const dataset = chartData.datasets[0];

    expect(component.languageStats().length).toBe(15);
    expect(chartData.labels).toEqual([
      'Lang01',
      'Lang02',
      'Lang03',
      'Lang04',
      'Lang05',
      'Lang06',
      'Lang07',
      'Lang08',
      'Lang09',
      'Lang10',
      'Lang11',
      'Lang12',
      'Lang13',
      'Lang14',
      'Lang15',
    ]);

    expect(dataset).toBeDefined();
    if (!dataset) {
      throw new Error('Expected a chart dataset');
    }

    expect(dataset.data).toEqual([17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3]);
    expect(dataset.backgroundColor).toEqual(EXPECTED_LANGUAGE_COLORS);
    expect(dataset.borderColor).toEqual(EXPECTED_LANGUAGE_COLORS.map(() => 'rgba(255, 255, 255, 0.2)'));
    expect(dataset.borderWidth).toBe(2);
    expect(dataset.hoverBorderColor).toBe('#ffffff');
    expect(dataset.hoverBorderWidth).toBe(3);
  });

  it('formats the tooltip callback from computed chart data without a live Chart.js instance', () => {
    books.set([
      createBook(1, 1, 'en'),
      createBook(2, 1, ' EN '),
      createBook(3, 1, 'en'),
      createBook(4, 1, 'spa'),
      createBook(5, 1, 'spanish'),
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
      label: 'English',
    })).toBe('statsLibrary.language.tooltipLabel|label=English|value=3|percentage=60.0');
    expect(translate).toHaveBeenCalledWith('statsLibrary.language.tooltipLabel', {
      label: 'English',
      value: 3,
      percentage: '60.0',
    });
  });
});
