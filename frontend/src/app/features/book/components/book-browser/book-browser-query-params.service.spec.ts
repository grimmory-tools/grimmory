import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, ParamMap, Router} from '@angular/router';
import {vi, describe, beforeEach, afterEach, expect, it} from 'vitest';

import {EntityType} from './book-browser.component';
import {
  BookBrowserQueryParamsService,
  QUERY_PARAMS,
  VIEW_MODES
} from './book-browser-query-params.service';
import {SortDirection, SortOption} from '../../model/sort.model';
import {EntityViewPreferences} from '../../../settings/user-management/user.service';

describe('BookBrowserQueryParamsService', () => {
  let service: BookBrowserQueryParamsService;
  let routerNavigate: ReturnType<typeof vi.fn>;
  let routeSnapshot: {
    queryParams: Record<string, string | null>;
    queryParamMap: ParamMap;
  };
  let activatedRoute: {snapshot: typeof routeSnapshot};

  const sortOptions: SortOption[] = [
    {label: 'Added On', field: 'addedOn', direction: SortDirection.DESCENDING},
    {label: 'Title', field: 'title', direction: SortDirection.ASCENDING},
    {label: 'Authors', field: 'author', direction: SortDirection.ASCENDING}
  ];

  beforeEach(() => {
    routerNavigate = vi.fn();
    routeSnapshot = {
      queryParams: {},
      queryParamMap: convertToParamMap({})
    };
    activatedRoute = {snapshot: routeSnapshot};

    TestBed.configureTestingModule({
      providers: [
        BookBrowserQueryParamsService,
        {provide: Router, useValue: {navigate: routerNavigate}},
        {provide: ActivatedRoute, useValue: activatedRoute}
      ]
    });

    service = TestBed.inject(BookBrowserQueryParamsService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('parses explicit query params, filters, and toggle state', () => {
    const result = service.parseQueryParams(
      convertToParamMap({
        [QUERY_PARAMS.VIEW]: VIEW_MODES.TABLE,
        [QUERY_PARAMS.SORT]: 'title:desc,author:asc',
        [QUERY_PARAMS.FILTER]: 'tag:fiction|sci%20fi,series:A%3AB',
        [QUERY_PARAMS.FMODE]: 'or',
        [QUERY_PARAMS.FROM]: 'toggle'
      }),
      undefined,
      undefined,
      undefined,
      sortOptions,
      'and'
    );

    expect(result).toEqual({
      viewMode: VIEW_MODES.TABLE,
      sortOption: {label: 'Title', field: 'title', direction: SortDirection.DESCENDING},
      sortCriteria: [
        {label: 'Title', field: 'title', direction: SortDirection.DESCENDING},
        {label: 'Authors', field: 'author', direction: SortDirection.ASCENDING}
      ],
      filters: {
        tag: ['fiction', 'sci fi'],
        series: ['A:B']
      },
      filterMode: 'or',
      viewModeFromToggle: true
    });
  });

  it('uses entity overrides, legacy preferences, and default sort fallback', () => {
    const userPrefs: EntityViewPreferences = {
      global: {
        sortKey: 'addedOn',
        sortDir: 'ASC',
        sortCriteria: [
          {field: 'title', direction: 'DESC'}
        ],
        view: 'TABLE',
        coverSize: 1,
        seriesCollapsed: false,
        overlayBookType: true
      },
      overrides: [
        {
          entityType: 'LIBRARY',
          entityId: 7,
          preferences: {
            sortKey: 'authors',
            sortDir: 'DESC',
            sortCriteria: [
              {field: 'authors', direction: 'DESC'}
            ],
            view: 'GRID',
            coverSize: 1,
            seriesCollapsed: false,
            overlayBookType: false
          }
        }
      ]
    };

    const overrideResult = service.parseQueryParams(
      convertToParamMap({
        [QUERY_PARAMS.FMODE]: 'single'
      }),
      userPrefs,
      EntityType.LIBRARY,
      7,
      sortOptions,
      'and'
    );

    expect(overrideResult.viewMode).toBe(VIEW_MODES.GRID);
    expect(overrideResult.filterMode).toBe('single');
    expect(overrideResult.sortCriteria).toEqual([
      {label: 'authors', field: 'authors', direction: SortDirection.DESCENDING}
    ]);

    const legacyResult = service.parseQueryParams(
      convertToParamMap({}),
      {
        global: {
          sortKey: 'title',
          sortDir: 'DESC',
          view: 'TABLE',
          coverSize: 1,
          seriesCollapsed: false,
          overlayBookType: true
        },
        overrides: []
      },
      undefined,
      undefined,
      sortOptions,
      'and'
    );

    expect(legacyResult.sortOption).toEqual({
      label: 'Title',
      field: 'title',
      direction: SortDirection.DESCENDING
    });
    expect(legacyResult.viewMode).toBe('table');

    const fallbackResult = service.parseQueryParams(
      convertToParamMap({}),
      {
        global: {
          sortKey: 'missing',
          sortDir: 'DESC',
          view: 'TABLE',
          coverSize: 1,
          seriesCollapsed: false,
          overlayBookType: true
        },
        overrides: []
      },
      undefined,
      undefined,
      sortOptions,
      'and'
    );

    expect(fallbackResult.sortOption).toEqual({
      label: 'Added On',
      field: 'addedOn',
      direction: SortDirection.DESCENDING
    });
  });

  it('serializes and deserializes sorts and filters while ignoring malformed input', () => {
    expect(service.serializeSort([
      {label: 'Title', field: 'title', direction: SortDirection.ASCENDING},
      {label: 'Added On', field: 'addedOn', direction: SortDirection.DESCENDING}
    ])).toBe('title:asc,addedOn:desc');

    expect(service.deserializeSort('title:desc,missing:asc', sortOptions)).toEqual([
      {label: 'Title', field: 'title', direction: SortDirection.DESCENDING}
    ]);

    expect(service.serializeFilters({
      tag: ['sci fi', 'new:release'],
      series: ['A/B']
    })).toBe('tag:sci%20fi|new%3Arelease,series:A%2FB');

    expect(service.deserializeFilters('tag:sci%20fi|new%3Arelease,broken,series:A%2FB')).toEqual({
      tag: ['sci fi', 'new:release'],
      series: ['A/B']
    });
  });

  it('updates query params for view mode, sort, filters, and sync changes', () => {
    routeSnapshot.queryParams = {
      foo: 'bar',
      [QUERY_PARAMS.SORT]: 'title:asc',
      [QUERY_PARAMS.DIRECTION]: 'desc',
      [QUERY_PARAMS.VIEW]: VIEW_MODES.GRID,
      [QUERY_PARAMS.FMODE]: 'and',
      [QUERY_PARAMS.FILTER]: 'tag:fiction'
    };
    routeSnapshot.queryParamMap = convertToParamMap({
      [QUERY_PARAMS.FILTER]: 'tag:fiction'
    });

    service.updateViewMode(VIEW_MODES.TABLE);
    expect(routerNavigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: {
        [QUERY_PARAMS.VIEW]: VIEW_MODES.TABLE,
        [QUERY_PARAMS.FROM]: 'toggle'
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    }));

    service.updateSort({label: 'Added On', field: 'addedOn', direction: SortDirection.DESCENDING});
    expect(routerNavigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({
        foo: 'bar',
        [QUERY_PARAMS.SORT]: 'addedOn:desc',
        [QUERY_PARAMS.DIRECTION]: null
      }),
      replaceUrl: true
    }));

    routerNavigate.mockClear();
    routeSnapshot.queryParamMap = convertToParamMap({});
    service.updateFilters(null);
    expect(routerNavigate).not.toHaveBeenCalled();

    routeSnapshot.queryParamMap = convertToParamMap({
      [QUERY_PARAMS.FILTER]: 'tag:fiction'
    });
    service.updateFilters({tag: ['fiction', 'sci fi']});
    expect(routerNavigate).toHaveBeenCalledWith([], expect.objectContaining({
      relativeTo: activatedRoute as never,
      queryParams: {[QUERY_PARAMS.FILTER]: 'tag:fiction|sci%20fi'},
      queryParamsHandling: 'merge',
      replaceUrl: false
    }));

    service.updateFilterMode('single', {tag: ['fiction', 'sci fi']});
    expect(routerNavigate).toHaveBeenCalledWith([], expect.objectContaining({
      relativeTo: activatedRoute as never,
      queryParams: {
        [QUERY_PARAMS.FMODE]: 'single',
        [QUERY_PARAMS.FILTER]: null
      },
      queryParamsHandling: 'merge',
      replaceUrl: false
    }));

    service.updateFilterMode('or', {tag: ['fiction']});
    expect(routerNavigate).toHaveBeenCalledWith([], expect.objectContaining({
      relativeTo: activatedRoute as never,
      queryParams: {
        [QUERY_PARAMS.FMODE]: 'or'
      },
      queryParamsHandling: 'merge',
      replaceUrl: false
    }));

    routerNavigate.mockClear();
    service.syncQueryParams('grid', 'and', {tag: ['fiction']});
    expect(routerNavigate).not.toHaveBeenCalled();

    service.syncQueryParams('table', 'single', {tag: ['fiction']});
    expect(routerNavigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: {
        foo: 'bar',
        [QUERY_PARAMS.SORT]: 'title:asc',
        [QUERY_PARAMS.DIRECTION]: 'desc',
        [QUERY_PARAMS.VIEW]: 'table',
        [QUERY_PARAMS.FMODE]: 'single',
        [QUERY_PARAMS.FILTER]: 'tag:fiction'
      },
      replaceUrl: true
    }));
  });

  it('detects when a series filter should force expansion', () => {
    expect(service.shouldForceExpandSeries(convertToParamMap({}))).toBe(false);
    expect(service.shouldForceExpandSeries(convertToParamMap({
      [QUERY_PARAMS.FILTER]: 'tag:fiction,series:space-opera'
    }))).toBe(true);
  });
});
