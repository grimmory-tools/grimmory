import {computed, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../../settings/user-management/user.service';
import {UserChartConfigService} from './service/user-chart-config.service';
import {UserStatsComponent} from './user-stats.component';

describe('UserStatsComponent', () => {
  let chartConfigService: {
    charts: ReturnType<typeof signal>;
    visibleCharts: ReturnType<typeof computed>;
    toggleChart: ReturnType<typeof vi.fn>;
    reorderCharts: ReturnType<typeof vi.fn>;
    resetLayout: ReturnType<typeof vi.fn>;
    setAllChartsEnabled: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    const chartSignal = signal([
      {id: 'reading-progress', enabled: true, sizeClass: 'chart-small-square', order: 0},
    ]);
    chartConfigService = {
      charts: chartSignal,
      visibleCharts: computed(() => chartSignal().filter(chart => chart.enabled)),
      toggleChart: vi.fn(),
      reorderCharts: vi.fn(),
      resetLayout: vi.fn(),
      setAllChartsEnabled: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        {
          provide: UserService,
          useValue: {
            currentUser: signal({name: 'Alice Reader', username: 'alice'}),
          },
        },
        {provide: UserChartConfigService, useValue: chartConfigService},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('derives the display name and chart translation key from injected state', () => {
    const component = TestBed.runInInjectionContext(() => new UserStatsComponent());

    expect(component.userName()).toBe('Alice Reader');
    expect(component.chartNameKey('reading-progress')).toBe('chartNames.readingProgress');
  });

  it('forwards chart actions to the chart config service', () => {
    const component = TestBed.runInInjectionContext(() => new UserStatsComponent());

    component.toggleChart('reading-progress');
    component.showAllCharts();
    component.hideAllCharts();
    component.resetLayout();
    component.drop({
      container: {data: chartConfigService.charts()},
      previousIndex: 0,
      currentIndex: 0,
    } as never);

    expect(chartConfigService.toggleChart).toHaveBeenCalledWith('reading-progress');
    expect(chartConfigService.setAllChartsEnabled).toHaveBeenNthCalledWith(1, true);
    expect(chartConfigService.setAllChartsEnabled).toHaveBeenNthCalledWith(2, false);
    expect(chartConfigService.resetLayout).toHaveBeenCalled();
    expect(chartConfigService.reorderCharts).toHaveBeenCalled();
  });
});
