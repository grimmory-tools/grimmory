import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {type BrowsePage} from '../../core/data/browse.models';
import {createBrowseResults, type BrowseResultsQuery} from './results';

interface Item {
  id: number;
}

function pageData(ids: number[]): {pages: BrowsePage<Item>[]} {
  return {
    pages: [{
      content: ids.map(id => ({id})),
      page: {number: 0, size: ids.length, totalElements: ids.length, totalPages: 1, cursor: ''},
      links: [],
    }],
  };
}

describe('createBrowseResults', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('holds the last results through a collection change, then drops to the skeleton if the hold runs out', () => {
    const data = signal<{pages: BrowsePage<Item>[]} | undefined>(pageData([1, 2]));
    const identity = signal('a');
    const scrollToTop = vi.fn();
    const query: BrowseResultsQuery<Item> = {
      data,
      isSuccess: () => true,
      isError: () => false,
      isFetchNextPageError: () => false,
      hasNextPage: () => false,
      isFetching: () => false,
      fetchNextPage: () => Promise.resolve(),
      refetch: () => Promise.resolve(),
    };
    const presentation = TestBed.runInInjectionContext(() => createBrowseResults<Item>({
      query,
      listKey: identity,
      artworkUrls: items => items.map(item => `/cover/${item.id}`),
      scrollToTop,
      coverWaitMs: 5,
      staleResultsMs: 5,
    }));
    const ids = () => presentation.items().map(item => item.id);

    TestBed.flushEffects();
    expect(ids()).toEqual([1, 2]);

    identity.set('b');
    data.set(pageData([3]));
    TestBed.flushEffects();
    expect(ids()).toEqual([1, 2]);
    expect(presentation.showingPreviousResults()).toBe(true);

    vi.advanceTimersByTime(5);
    expect(ids()).toEqual([3]);
    expect(scrollToTop).toHaveBeenCalledOnce();

    identity.set('c');
    data.set(undefined);
    TestBed.flushEffects();
    expect(ids()).toEqual([3]);

    vi.advanceTimersByTime(5);
    expect(ids()).toEqual([]);
    expect(presentation.status()).toBe('pending');

    data.set(pageData([4]));
    TestBed.flushEffects();
    expect(ids()).toEqual([4]);
  });
});
