import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {UserChartConfigService} from './user-chart-config.service';

describe('UserChartConfigService', () => {
  const storageKey = 'userStatsChartConfig';

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [UserChartConfigService]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  it('loads the default chart layout when nothing has been saved', () => {
    const service = TestBed.inject(UserChartConfigService);

    expect(service.charts()).toHaveLength(23);
    expect(service.visibleCharts()).toHaveLength(23);
    expect(service.charts()[0]).toEqual({
      id: 'heatmap',
      enabled: true,
      sizeClass: 'chart-full',
      order: 0,
    });
    expect(localStorage.getItem(storageKey)).toBeNull();
  });

  it('merges saved chart state with the defaults', () => {
    localStorage.setItem(storageKey, JSON.stringify([
      {id: 'heatmap', enabled: false, sizeClass: 'chart-small-square', order: 12},
      {id: 'timeline', enabled: true, sizeClass: 'chart-medium', order: 0},
      {id: 'reading-clock', enabled: false, sizeClass: 'chart-medium', order: 2},
    ]));

    const service = TestBed.inject(UserChartConfigService);

    expect(service.charts()[0]).toEqual({
      id: 'timeline',
      enabled: true,
      sizeClass: 'chart-medium',
      order: 0,
    });
    expect(service.charts().find(chart => chart.id === 'heatmap')).toEqual({
      id: 'heatmap',
      enabled: false,
      sizeClass: 'chart-small-square',
      order: 12,
    });
    expect(service.visibleCharts().map(chart => chart.id)).not.toContain('heatmap');
  });

  it('falls back to defaults when the saved state is invalid', () => {
    localStorage.setItem(storageKey, 'not-json');

    const service = TestBed.inject(UserChartConfigService);

    expect(service.charts()[0].id).toBe('heatmap');
    expect(service.charts()[1].id).toBe('favorite-days');
  });

  it('toggles charts, enables or disables all charts, and restores the default layout', () => {
    const service = TestBed.inject(UserChartConfigService);

    service.toggleChart('timeline');
    expect(service.charts().find(chart => chart.id === 'timeline')?.enabled).toBe(false);

    service.setAllChartsEnabled(false);
    expect(service.visibleCharts()).toEqual([]);

    service.resetLayout();
    expect(service.charts()[0]).toEqual({
      id: 'heatmap',
      enabled: true,
      sizeClass: 'chart-full',
      order: 0,
    });
    expect(service.visibleCharts()).toHaveLength(23);
  });

  it('reorders the selected chart subset and preserves normalized storage order', () => {
    const service = TestBed.inject(UserChartConfigService);

    service.reorderCharts(
      service.charts().filter(chart => chart.id === 'heatmap' || chart.id === 'timeline'),
      0,
      1
    );

    expect(service.charts().slice(0, 4).map(chart => chart.id)).toEqual([
      'timeline',
      'favorite-days',
      'peak-hours',
      'heatmap',
    ]);
    expect(JSON.parse(localStorage.getItem(storageKey) || '[]')[0]).toEqual({
      id: 'timeline',
      enabled: true,
      sizeClass: 'chart-full',
      order: 0,
    });
  });
});
