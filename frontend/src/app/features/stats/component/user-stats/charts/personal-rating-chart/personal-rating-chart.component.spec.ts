import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';
import {BookService} from '../../../../../book/service/book.service';
import type {Book} from '../../../../../book/model/book.model';
import {PersonalRatingChartComponent} from './personal-rating-chart.component';

describe('PersonalRatingChartComponent', () => {
  const allRatingLabels = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];

  const translate = vi.fn((key: string, params?: Record<string, unknown>) =>
    params ? `${key}:${JSON.stringify(params)}` : key
  );
  const books = vi.fn(() => [] as Book[]);
  const isBooksLoading = vi.fn(() => false);

  beforeEach(() => {
    books.mockReset();
    books.mockReturnValue([]);
    isBooksLoading.mockReset();
    isBooksLoading.mockReturnValue(false);
    translate.mockClear();

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
    return TestBed.runInInjectionContext(() => new PersonalRatingChartComponent());
  }

  function ratedBook(personalRating: number | null | undefined): Book {
    return {personalRating} as Book;
  }

  it('counts personal ratings across the 1-10 range and ignores invalid values', () => {
    books.mockReturnValue([
      ratedBook(1),
      ratedBook(2),
      ratedBook(2),
      ratedBook(10),
      ratedBook(10),
      ratedBook(10),
      ratedBook(0),
      ratedBook(11),
      ratedBook(null),
      ratedBook(undefined),
    ]);

    const component = createComponent();
    const chartData = component.chartData();

    expect(chartData.labels).toEqual(allRatingLabels);
    expect(chartData.datasets[0]?.data).toEqual([1, 2, 0, 0, 0, 0, 0, 0, 0, 3]);
  });

  it('retains zero-count buckets in chartData for missing rating ranges', () => {
    books.mockReturnValue([ratedBook(7)]);

    const component = createComponent();
    const chartData = component.chartData();

    expect(chartData.labels).toEqual(allRatingLabels);
    expect(chartData.datasets[0]?.data).toEqual([0, 0, 0, 0, 0, 0, 1, 0, 0, 0]);
  });

  it('builds tooltip title and singular/plural labels via translation callbacks', () => {
    const component = createComponent();
    const callbacks = component.chartOptions?.plugins?.tooltip?.callbacks as
      | {
        title?: (context: {label?: string}[]) => string;
        label?: (context: {parsed: {y: number}}) => string;
      }
      | undefined;

    translate.mockClear();

    const title = callbacks?.title?.([{label: '8'}]);
    const singularLabel = callbacks?.label?.({parsed: {y: 1}});
    const pluralLabel = callbacks?.label?.({parsed: {y: 4}});

    expect(title).toBe('statsUser.personalRating.tooltipTitle:{"label":"8"}');
    expect(singularLabel).toBe('statsUser.personalRating.tooltipBook:{"value":1}');
    expect(pluralLabel).toBe('statsUser.personalRating.tooltipBooks:{"value":4}');
    expect(translate).toHaveBeenCalledWith('statsUser.personalRating.tooltipTitle', {label: '8'});
    expect(translate).toHaveBeenCalledWith('statsUser.personalRating.tooltipBook', {value: 1});
    expect(translate).toHaveBeenCalledWith('statsUser.personalRating.tooltipBooks', {value: 4});
  });

  it('returns empty fallback chart data when chartData computation throws', () => {
    const chartError = new Error('chart data failure');
    books.mockImplementation(() => {
      throw chartError;
    });
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const component = createComponent();
    const chartData = component.chartData();

    expect(errorSpy).toHaveBeenCalledWith('Error updating personal rating chart data:', chartError);
    expect(chartData.labels).toEqual([]);
    expect(chartData.datasets[0]?.data).toEqual([]);
    expect(chartData.datasets[0]?.label).toBe('statsUser.personalRating.booksByPersonalRating');
    expect(chartData.datasets[0]?.backgroundColor).toEqual([
      '#DC2626',
      '#EA580C',
      '#F59E0B',
      '#EAB308',
      '#FACC15',
      '#BEF264',
      '#65A30D',
      '#16A34A',
      '#059669',
      '#2563EB',
    ]);
  });
});
