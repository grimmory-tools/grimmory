import {describe, expect, it} from 'vitest';

import {MenuService} from './app.menu.service';

describe('MenuService', () => {
  it('broadcasts menu state changes and reset events', () => {
    const service = new MenuService();
    const menuEvents: unknown[] = [];
    const resets: unknown[] = [];

    const menuSubscription = service.menuSource$.subscribe(event => menuEvents.push(event));
    const resetSubscription = service.resetSource$.subscribe(event => resets.push(event));

    service.onMenuStateChange({key: 'settings'});
    service.reset();

    expect(menuEvents).toEqual([{key: 'settings'}]);
    expect(resets).toEqual([true]);

    menuSubscription.unsubscribe();
    resetSubscription.unsubscribe();
  });
});
