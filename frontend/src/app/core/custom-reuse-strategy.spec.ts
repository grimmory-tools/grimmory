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
});
