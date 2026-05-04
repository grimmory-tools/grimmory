import {ActivatedRouteSnapshot, Route} from '@angular/router';
import {describe, expect, it} from 'vitest';

import {CustomReuseStrategy} from './custom-reuse-strategy';

function createRoute(routeConfig: Route, params: Record<string, string> = {}): ActivatedRouteSnapshot {
  return {
    routeConfig,
    params
  } as ActivatedRouteSnapshot;
}

describe('CustomReuseStrategy', () => {
  const strategy = new CustomReuseStrategy();

  it('reuses routes only when the route config and params both match', () => {
    const routeConfig = {path: 'series'};
    const current = createRoute(routeConfig, {seriesId: '12'});
    const same = createRoute(routeConfig, {seriesId: '12'});
    const sameDifferentOrder = createRoute(routeConfig, {tab: 'books', seriesId: '12'});
    const currentWithTab = createRoute(routeConfig, {seriesId: '12', tab: 'books'});
    const differentParams = createRoute(routeConfig, {seriesId: '99'});
    const differentConfig = createRoute({path: 'authors'}, {seriesId: '12'});

    expect(strategy.shouldReuseRoute(same, current)).toBe(true);
    expect(strategy.shouldReuseRoute(sameDifferentOrder, currentWithTab)).toBe(true);
    expect(strategy.shouldReuseRoute(differentParams, current)).toBe(false);
    expect(strategy.shouldReuseRoute(differentConfig, current)).toBe(false);
  });
});
