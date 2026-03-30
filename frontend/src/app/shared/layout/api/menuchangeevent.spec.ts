import {describe, expect, expectTypeOf, it} from 'vitest';

import {MenuChangeEvent} from './menuchangeevent';

describe('menuchangeevent', () => {
  it('supports route-backed menu change events', () => {
    const event: MenuChangeEvent = {
      key: 'settings',
      routeEvent: true
    };

    expect(event.key).toBe('settings');
    expect(event.routeEvent).toBe(true);
  });

  it('keeps routeEvent optional', () => {
    expectTypeOf<MenuChangeEvent['routeEvent']>().toEqualTypeOf<boolean | undefined>();
  });
});
