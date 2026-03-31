import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {UserStatsService} from './user-stats.service';

describe('UserStatsService', () => {
  let service: UserStatsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        UserStatsService,
      ],
    });

    service = TestBed.inject(UserStatsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('requests the yearly heatmap with the year query param', () => {
    let result: unknown;
    service.getHeatmapForYear(2026).subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/heatmap')
      && req.params.get('year') === '2026'
    );
    expect(request.request.method).toBe('GET');
    request.flush([{date: '2026-03-26', count: 3}]);

    expect(result).toEqual([{date: '2026-03-26', count: 3}]);
  });

  it('requests the weekly reading timeline with both year and week params', () => {
    service.getTimelineForWeek(2026, 13).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/timeline')
      && req.params.get('year') === '2026'
      && req.params.get('week') === '13'
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('omits optional params when requesting favorite days without filters', () => {
    service.getFavoriteDays().subscribe();

    const request = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/favorite-days')
    );
    expect(request.request.params.keys()).toEqual([]);
    request.flush([]);
  });

  it('includes optional params for peak hours when year and month are provided', () => {
    service.getPeakHours(2026, 3).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/peak-hours')
      && req.params.get('year') === '2026'
      && req.params.get('month') === '3'
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('requests the remaining stats endpoints with the expected URLs', () => {
    service.getGenreStats().subscribe();
    service.getCompletionTimelineForYear(2026).subscribe();
    service.getPageTurnerScores().subscribe();
    service.getCompletionRace(2026).subscribe();
    service.getReadingDates().subscribe();
    service.getSessionScatter(2026).subscribe();

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/user-stats/reading/genres')).flush([]);
    httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/completion-timeline')
      && req.params.get('year') === '2026'
    ).flush([]);
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/user-stats/reading/page-turner-scores')).flush([]);
    httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/completion-race')
      && req.params.get('year') === '2026'
    ).flush([]);
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/user-stats/reading/dates')).flush([]);
    httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/user-stats/reading/session-scatter')
      && req.params.get('year') === '2026'
    ).flush([]);
  });
});
