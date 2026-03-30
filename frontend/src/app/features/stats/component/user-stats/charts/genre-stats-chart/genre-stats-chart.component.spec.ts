import {TestBed} from '@angular/core/testing';
import type {Scale} from 'chart.js';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {fakeChartTooltipContext, nextChartEmission} from '../../../../../../core/testing/chart-testing';
import {UserStatsService, type GenreStatsResponse} from '../../../../../settings/user-management/user-stats.service';
import {GenreStatsChartComponent} from './genre-stats-chart.component';

describe('GenreStatsChartComponent', () => {
  let getGenreStats: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    getGenreStats = vi.fn(() => of([]));

    TestBed.configureTestingModule({
      providers: [
        {provide: UserStatsService, useValue: {getGenreStats}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function getTooltipLabelCallback(component: GenreStatsChartComponent): ((context: ReturnType<typeof fakeChartTooltipContext>) => string) | undefined {
    return component.chartOptions?.plugins?.tooltip?.callbacks?.label as
      | ((context: ReturnType<typeof fakeChartTooltipContext>) => string)
      | undefined;
  }

  function getScaleTickCallback(
    component: GenreStatsChartComponent,
    axis: 'x' | 'y',
  ): ((this: Scale, value: string | number, index: number, ticks: {value: number}[]) => string | string[] | number | null | undefined) | undefined {
    const scale = component.chartOptions?.scales?.[axis] as {
      ticks?: {
        callback?: (this: Scale, value: string | number, index: number, ticks: {value: number}[]) => string | string[] | number | null | undefined;
      };
    } | undefined;

    return scale?.ticks?.callback;
  }

  it('sorts genres by duration, limits the list, and formats tooltip output without rendering a live chart', async () => {
    const stats: GenreStatsResponse[] = [
      {genre: 'Very Long Genre Label', bookCount: 2, totalSessions: 5, totalDurationSeconds: 3600, averageSessionsPerBook: 2.5},
      {genre: 'Sci-Fi', bookCount: 4, totalSessions: 8, totalDurationSeconds: 5400, averageSessionsPerBook: 2},
      {genre: 'Fantasy', bookCount: 3, totalSessions: 7, totalDurationSeconds: 1800, averageSessionsPerBook: 2.3},
    ];
    getGenreStats.mockReturnValue(of(stats));

    const component = TestBed.runInInjectionContext(() => new GenreStatsChartComponent());
    component.maxGenres = 2;

    const nextEmission = nextChartEmission(component.chartData$);
    component.ngOnInit();
    const chartData = await nextEmission;

    expect(chartData.labels).toEqual(['Sci-Fi', 'Very Long Genre Label']);
    expect(chartData.datasets[0]?.data).toEqual([5400, 3600]);

    const tooltipLabel = getTooltipLabelCallback(component)?.(
      fakeChartTooltipContext({
        data: [5400, 3600],
        labels: ['Sci-Fi', 'Very Long Genre Label'],
        dataIndex: 1,
      })
    );
    expect(tooltipLabel).toBe('Very Long Genre Label: 1h 0m');

    const tickCallback = getScaleTickCallback(component, 'x');
    expect(tickCallback?.call({} as Scale, 0, 1, [])).toBe('Very Long Ge...');
  });

  it('translates y-axis durations across seconds, minutes, hours, and days', () => {
    const component = TestBed.runInInjectionContext(() => new GenreStatsChartComponent());
    const tickCallback = getScaleTickCallback(component, 'y');

    expect(tickCallback?.call({} as Scale, 45, 0, [])).toBe('45 statsUser.genreStats.sec');
    expect(tickCallback?.call({} as Scale, 600, 0, [])).toBe('10 statsUser.genreStats.min');
    expect(tickCallback?.call({} as Scale, 7200, 0, [])).toBe('2 statsUser.genreStats.hrs');
    expect(tickCallback?.call({} as Scale, 90000, 0, [])).toBe('1 statsUser.genreStats.day 1 statsUser.genreStats.hr');
  });

  it('logs and preserves the empty chart state when the stats request fails', () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    getGenreStats.mockReturnValue(throwError(() => new Error('boom')));

    const component = TestBed.runInInjectionContext(() => new GenreStatsChartComponent());

    component.ngOnInit();

    expect(errorSpy).toHaveBeenCalled();
    expect(component.chartOptions?.plugins?.legend?.display).toBe(false);
    expect(component.chartOptions?.plugins?.tooltip?.enabled).toBe(true);
  });
});
