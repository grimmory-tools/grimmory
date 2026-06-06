import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import type {Book} from '../../../../../book/model/book.model';
import {BookService} from '../../../../../book/service/book.service';
import {ReadingProgressChartComponent} from './reading-progress-chart.component';

describe('ReadingProgressChartComponent', () => {
  const chartColors = ['#6c757d', '#ffc107', '#fd7e14', '#17a2b8', '#6f42c1', '#28a745'];

  const translate = vi.fn((key: string, params?: Record<string, unknown>) =>
    params ? `${key}:${JSON.stringify(params)}` : key
  );
  const books = vi.fn(() => [] as Book[]);
  const isBooksLoading = vi.fn(() => false);

  beforeEach(() => {
    translate.mockClear();
    books.mockReset();
    books.mockReturnValue([]);
    isBooksLoading.mockReset();
    isBooksLoading.mockReturnValue(false);

    TestBed.configureTestingModule({
      providers: [
        {
          provide: BookService,
          useValue: {books, isBooksLoading},
        },
        {
          provide: TranslocoService,
          useValue: {translate},
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createComponent() {
    return TestBed.runInInjectionContext(() => new ReadingProgressChartComponent());
  }

  function bookWithProgress(progress: Partial<Book>): Book {
    return progress as Book;
  }

  function getLegendGenerator(component: ReadingProgressChartComponent) {
    return component.chartOptions?.plugins?.legend?.labels?.generateLabels as
      | ((chart: {
        data: {labels?: string[]; datasets?: {data: number[]; backgroundColor: string[]}[]};
        getDataVisibility?: (index: number) => boolean;
      }) => {
        text: string;
        fillStyle: string;
        strokeStyle: string;
        lineWidth: number;
        hidden: boolean;
        index: number;
        fontColor: string;
      }[])
      | undefined;
  }

  function getTooltipCallbacks(component: ReadingProgressChartComponent) {
    return component.chartOptions?.plugins?.tooltip?.callbacks as
      | {
        title?: (context: {label?: string}[]) => string;
        label?: (context: {parsed: number; dataset: {data: number[]}; label: string}) => string;
      }
      | undefined;
  }

  it('buckets progress boundaries using reader-source precedence and deterministic chart colors', () => {
    books.mockReturnValue([
      bookWithProgress({}),
      bookWithProgress({pdfProgress: {page: 1, percentage: 0.1}, epubProgress: {cfi: 'epub-fallback', percentage: 100}}),
      bookWithProgress({pdfProgress: {page: 25, percentage: 25}}),
      bookWithProgress({pdfProgress: {page: 0, percentage: 0}, epubProgress: {cfi: 'epub-26', percentage: 26}}),
      bookWithProgress({cbxProgress: {page: 50, percentage: 50}}),
      bookWithProgress({epubProgress: {cfi: 'epub-51', percentage: 51}}),
      bookWithProgress({pdfProgress: {page: 0, percentage: 0}, cbxProgress: {page: 75, percentage: 75}}),
      bookWithProgress({koreaderProgress: {percentage: 76}}),
      bookWithProgress({pdfProgress: {page: 0, percentage: 0}, koreaderProgress: {percentage: 99}, koboProgress: {percentage: 100}}),
      bookWithProgress({koboProgress: {percentage: 100}}),
    ]);

    const component = createComponent();
    const chartData = component.chartData();

    expect(chartData.labels).toEqual(['0%', '1-25%', '26-50%', '51-75%', '76-99%', '100%']);
    expect(chartData.datasets[0]?.data).toEqual([1, 2, 2, 2, 2, 1]);
    expect(chartData.datasets[0]?.label).toBe('statsUser.readingProgress.booksByProgress');
    expect(chartData.datasets[0]?.backgroundColor).toEqual(chartColors);
    expect(chartData.datasets[0]?.borderColor).toBe('#ffffff');
    expect(chartData.datasets[0]?.hoverBorderWidth).toBe(3);
  });

  it('retains zero-count buckets in a fixed order when only one progress range has books', () => {
    books.mockReturnValue([
      bookWithProgress({koboProgress: {percentage: 100}}),
    ]);

    const component = createComponent();
    const chartData = component.chartData();

    expect(chartData.labels).toEqual(['0%', '1-25%', '26-50%', '51-75%', '76-99%', '100%']);
    expect(chartData.datasets[0]?.data).toEqual([0, 0, 0, 0, 0, 1]);
    expect(chartData.datasets[0]?.backgroundColor).toEqual(chartColors);
  });

  it('returns fallback empty chart data when chartData computation throws', () => {
    const chartError = new Error('reading progress failure');
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    books.mockImplementation(() => {
      throw chartError;
    });

    const component = createComponent();
    const chartData = component.chartData();

    expect(errorSpy).toHaveBeenCalledWith('Error updating reading progress chart data:', chartError);
    expect(chartData.labels).toEqual([]);
    expect(chartData.datasets[0]?.data).toEqual([]);
    expect(chartData.datasets[0]?.label).toBe('statsUser.readingProgress.booksByProgress');
    expect(chartData.datasets[0]?.backgroundColor).toEqual(chartColors);
    expect(chartData.datasets[0]?.borderWidth).toBe(1);
    expect(chartData.datasets[0]?.hoverBorderWidth).toBe(2);
  });

  it('builds deterministic legend labels from the current doughnut dataset without rendering a chart', () => {
    books.mockReturnValue([
      bookWithProgress({}),
      bookWithProgress({epubProgress: {cfi: 'epub-20', percentage: 20}}),
      bookWithProgress({cbxProgress: {page: 50, percentage: 50}}),
      bookWithProgress({koreaderProgress: {percentage: 80}}),
      bookWithProgress({koboProgress: {percentage: 100}}),
    ]);

    const component = createComponent();
    const chartData = component.chartData();
    const legendItems = getLegendGenerator(component)?.({
      data: {
        labels: chartData.labels as string[],
        datasets: [{
          data: chartData.datasets[0]?.data as number[],
          backgroundColor: chartData.datasets[0]?.backgroundColor as string[],
        }],
      },
      getDataVisibility: (index) => index !== 1,
    });

    expect(legendItems).toEqual([
      {text: '0%: 1', fillStyle: '#6c757d', strokeStyle: '#ffffff', lineWidth: 1, hidden: false, index: 0, fontColor: '#ffffff'},
      {text: '1-25%: 1', fillStyle: '#ffc107', strokeStyle: '#ffffff', lineWidth: 1, hidden: true, index: 1, fontColor: '#ffffff'},
      {text: '26-50%: 1', fillStyle: '#fd7e14', strokeStyle: '#ffffff', lineWidth: 1, hidden: false, index: 2, fontColor: '#ffffff'},
      {text: '51-75%: 0', fillStyle: '#17a2b8', strokeStyle: '#ffffff', lineWidth: 1, hidden: false, index: 3, fontColor: '#ffffff'},
      {text: '76-99%: 1', fillStyle: '#6f42c1', strokeStyle: '#ffffff', lineWidth: 1, hidden: false, index: 4, fontColor: '#ffffff'},
      {text: '100%: 1', fillStyle: '#28a745', strokeStyle: '#ffffff', lineWidth: 1, hidden: false, index: 5, fontColor: '#ffffff'},
    ]);
  });

  it('builds translated tooltip titles and labels with mapped descriptions and singular-plural output', () => {
    const component = createComponent();
    const callbacks = getTooltipCallbacks(component);

    translate.mockClear();

    const title = callbacks?.title?.([{label: '51-75%'}]);
    const singularLabel = callbacks?.label?.({
      parsed: 1,
      dataset: {data: [1, 4]},
      label: '51-75%',
    });
    const pluralLabel = callbacks?.label?.({
      parsed: 4,
      dataset: {data: [1, 4]},
      label: '100%',
    });

    expect(title).toBe('51-75%');
    expect(singularLabel).toBe(
      'statsUser.readingProgress.tooltipLabel:{"value":1,"plural":"","description":"statsUser.readingProgress.halfwayThrough","percentage":"20.0"}'
    );
    expect(pluralLabel).toBe(
      'statsUser.readingProgress.tooltipLabel:{"value":4,"plural":"s","description":"statsUser.readingProgress.completed","percentage":"80.0"}'
    );
    expect(translate).toHaveBeenCalledWith('statsUser.readingProgress.halfwayThrough');
    expect(translate).toHaveBeenCalledWith('statsUser.readingProgress.completed');
    expect(translate).toHaveBeenCalledWith('statsUser.readingProgress.tooltipLabel', {
      value: 1,
      plural: '',
      description: 'statsUser.readingProgress.halfwayThrough',
      percentage: '20.0',
    });
    expect(translate).toHaveBeenCalledWith('statsUser.readingProgress.tooltipLabel', {
      value: 4,
      plural: 's',
      description: 'statsUser.readingProgress.completed',
      percentage: '80.0',
    });
  });
});
