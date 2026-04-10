import {Injector, runInInjectionContext} from '@angular/core';
import {ActivatedRouteSnapshot, DetachedRouteHandle} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookBrowserScrollService} from '../features/book/components/book-browser/book-browser-scroll.service';
import {BookSelectionService} from '../features/book/components/book-browser/book-selection.service';
import {CustomReuseStrategy} from './custom-reuse-strategy';

function createRoute(path: string, params: Record<string, string> = {}): ActivatedRouteSnapshot {
  return {
    routeConfig: {path},
    params
  } as ActivatedRouteSnapshot;
}

describe('CustomReuseStrategy', () => {
  const handle = {componentRef: {} as never} as DetachedRouteHandle;

  let strategy: CustomReuseStrategy;
  let scrollService: BookBrowserScrollService;
  let selectionService: BookSelectionService;

  beforeEach(() => {
    vi.restoreAllMocks();

    scrollService = new BookBrowserScrollService();
    selectionService = new BookSelectionService();
    const injector = Injector.create({
      providers: [
        {provide: BookBrowserScrollService, useValue: scrollService},
        {provide: BookSelectionService, useValue: selectionService},
      ]
    });
    strategy = runInInjectionContext(injector, () => new CustomReuseStrategy());
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('stores and reattaches book browser routes with their saved scroll position', async () => {
    vi.useFakeTimers();
    const route = createRoute('all-books', {libraryId: '42'});
    const scrollElement = document.createElement('div');
    scrollElement.className = 'virtual-scroller';
    const querySelectorSpy = vi.spyOn(document, 'querySelector').mockReturnValue(scrollElement);
    const deselectSpy = vi.spyOn(selectionService, 'deselectAll');

    scrollService.savePosition('all-books:42', 321);
    strategy.store(route, handle);

    expect(strategy.shouldDetach(route)).toBe(true);
    expect(strategy.shouldAttach(route)).toBe(true);
    expect(deselectSpy).toHaveBeenCalledTimes(1);

    const restored = strategy.retrieve(route);
    await vi.runAllTimersAsync();

    expect(restored).toBe(handle);
    expect(querySelectorSpy).toHaveBeenCalledWith('.virtual-scroller');
    expect(scrollElement.scrollTop).toBe(321);
  });

  it('ignores non-book routes when storing or attaching', () => {
    const route = createRoute('login');
    const deselectSpy = vi.spyOn(selectionService, 'deselectAll');

    strategy.store(route, handle);

    expect(strategy.shouldDetach(route)).toBe(false);
    expect(strategy.shouldAttach(route)).toBe(false);
    expect(strategy.retrieve(route)).toBeNull();
    expect(deselectSpy).not.toHaveBeenCalled();
  });

  it('ignores null handles for book browser routes', () => {
    const route = createRoute('series', {seriesId: '77'});
    const deselectSpy = vi.spyOn(selectionService, 'deselectAll');

    strategy.store(route, null);

    expect(strategy.shouldDetach(route)).toBe(true);
    expect(strategy.shouldAttach(route)).toBe(false);
    expect(strategy.retrieve(route)).toBeNull();
    expect(deselectSpy).not.toHaveBeenCalled();
  });

  it('returns a stored handle without touching the DOM when no scroll position was saved', () => {
    const route = createRoute('authors');
    const querySelectorSpy = vi.spyOn(document, 'querySelector');
    const deselectSpy = vi.spyOn(selectionService, 'deselectAll');

    strategy.store(route, handle);

    expect(deselectSpy).toHaveBeenCalledOnce();
    expect(strategy.shouldAttach(route)).toBe(true);
    expect(strategy.retrieve(route)).toBe(handle);
    expect(querySelectorSpy).not.toHaveBeenCalled();
  });

  it('reuses routes only when the route config and params both match', () => {
    const routeConfig = {path: 'series'};
    const current = {
      routeConfig,
      params: {seriesId: '12'}
    } as unknown as ActivatedRouteSnapshot;
    const same = {
      routeConfig,
      params: {seriesId: '12'}
    } as unknown as ActivatedRouteSnapshot;
    const differentParams = {
      routeConfig,
      params: {seriesId: '99'}
    } as unknown as ActivatedRouteSnapshot;
    const differentConfig = {
      routeConfig: {path: 'authors'},
      params: {seriesId: '12'}
    } as unknown as ActivatedRouteSnapshot;

    expect(strategy.shouldReuseRoute(same, current)).toBe(true);
    expect(strategy.shouldReuseRoute(differentParams, current)).toBe(false);
    expect(strategy.shouldReuseRoute(differentConfig, current)).toBe(false);
  });

  it('evicts the oldest route when storing more than MAX_STORED_ROUTES (3)', () => {
    const route1 = createRoute('library/:libraryId/books', {libraryId: '1'});
    const route2 = createRoute('library/:libraryId/books', {libraryId: '2'});
    const route3 = createRoute('library/:libraryId/books', {libraryId: '3'});
    const route4 = createRoute('library/:libraryId/books', {libraryId: '4'});

    const handle1 = {componentRef: {id: 1} as never} as DetachedRouteHandle;
    const handle2 = {componentRef: {id: 2} as never} as DetachedRouteHandle;
    const handle3 = {componentRef: {id: 3} as never} as DetachedRouteHandle;
    const handle4 = {componentRef: {id: 4} as never} as DetachedRouteHandle;

    strategy.store(route1, handle1);
    strategy.store(route2, handle2);
    strategy.store(route3, handle3);

    // All 3 should be stored
    expect(strategy.shouldAttach(route1)).toBe(true);
    expect(strategy.shouldAttach(route2)).toBe(true);
    expect(strategy.shouldAttach(route3)).toBe(true);

    // Store a 4th — oldest (route1) should be evicted
    strategy.store(route4, handle4);

    expect(strategy.shouldAttach(route1)).toBe(false);
    expect(strategy.shouldAttach(route2)).toBe(true);
    expect(strategy.shouldAttach(route3)).toBe(true);
    expect(strategy.shouldAttach(route4)).toBe(true);
  });

  it('clears scroll positions when evicting old routes', () => {
    const clearSpy = vi.spyOn(scrollService, 'clearPosition');

    const route1 = createRoute('library/:libraryId/books', {libraryId: '10'});
    const route2 = createRoute('library/:libraryId/books', {libraryId: '20'});
    const route3 = createRoute('library/:libraryId/books', {libraryId: '30'});
    const route4 = createRoute('library/:libraryId/books', {libraryId: '40'});

    scrollService.savePosition('library/:libraryId/books:10', 100);

    strategy.store(route1, handle);
    strategy.store(route2, handle);
    strategy.store(route3, handle);

    expect(clearSpy).not.toHaveBeenCalled();

    strategy.store(route4, handle);

    expect(clearSpy).toHaveBeenCalledWith('library/:libraryId/books:10');
  });

  it('re-storing an existing route refreshes its LRU position', () => {
    const route1 = createRoute('library/:libraryId/books', {libraryId: '1'});
    const route2 = createRoute('library/:libraryId/books', {libraryId: '2'});
    const route3 = createRoute('library/:libraryId/books', {libraryId: '3'});
    const route4 = createRoute('library/:libraryId/books', {libraryId: '4'});

    strategy.store(route1, handle);
    strategy.store(route2, handle);
    strategy.store(route3, handle);

    // Re-store route1 — makes it most-recently-used
    strategy.store(route1, handle);

    // Now add route4 — route2 should be evicted (it's the oldest)
    strategy.store(route4, handle);

    expect(strategy.shouldAttach(route1)).toBe(true);
    expect(strategy.shouldAttach(route2)).toBe(false);
    expect(strategy.shouldAttach(route3)).toBe(true);
    expect(strategy.shouldAttach(route4)).toBe(true);
  });
});
