import {TestBed} from '@angular/core/testing';
import {convertToParamMap} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookFilterOrchestrationService} from './book-filter-orchestration.service';
import {SortService} from '../../service/sort.service';

describe('BookFilterOrchestrationService', () => {
  let service: BookFilterOrchestrationService;
  let sortService: {applyMultiSort: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    sortService = {
      applyMultiSort: vi.fn((books: unknown[]) => books),
    };

    TestBed.configureTestingModule({
      providers: [
        BookFilterOrchestrationService,
        {provide: SortService, useValue: sortService},
      ],
    });

    service = TestBed.inject(BookFilterOrchestrationService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('passes searched and collapsed books through the sorter', () => {
    const books = [
      {id: 1, metadata: {title: 'Alpha Flight'}},
      {id: 2, metadata: {title: 'Beta Test'}},
    ] as never[];
    const seriesCollapseFilter = {
      collapseBooks: vi.fn((items: unknown[]) => items),
    };
    const sortCriteria = [{property: 'title', direction: 'ASC'}] as never[];

    const result = service.applyFilters(
      books,
      'alpha',
      null,
      'and',
      seriesCollapseFilter as never,
      false,
      false,
      sortCriteria
    );

    expect(seriesCollapseFilter.collapseBooks).toHaveBeenCalledWith(
      [books[0]],
      false,
      false
    );
    expect(sortService.applyMultiSort).toHaveBeenCalledWith([books[0]], sortCriteria);
    expect(result).toEqual([books[0]]);
  });

  it('forces series expansion only when a series filter is present in the query string', () => {
    expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'author:42,series:Saga'}))).toBe(true);
    expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'author:42'}))).toBe(false);
    expect(service.shouldForceExpandSeries(convertToParamMap({}))).toBe(false);
  });
});
