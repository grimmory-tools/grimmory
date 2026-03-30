import {describe, expect, it} from 'vitest';

import {DEFAULT_DASHBOARD_CONFIG, ScrollerType} from './dashboard-config.model';

describe('dashboard-config.model', () => {
  it('ships with four enabled default scrollers', () => {
    expect(DEFAULT_DASHBOARD_CONFIG.scrollers).toHaveLength(4);
    expect(DEFAULT_DASHBOARD_CONFIG.scrollers.every((scroller) => scroller.enabled)).toBe(true);
  });

  it('uses ordered defaults and a shared max-items value across all defaults', () => {
    const maxItems = DEFAULT_DASHBOARD_CONFIG.scrollers.map((scroller) => scroller.maxItems);

    expect(DEFAULT_DASHBOARD_CONFIG.scrollers.map((scroller) => scroller.order)).toEqual([1, 2, 3, 4]);
    expect(new Set(maxItems).size).toBe(1);
    expect(maxItems[0]).toBeGreaterThan(0);
  });

  it('covers the expected default scroller types', () => {
    expect(DEFAULT_DASHBOARD_CONFIG.scrollers.map((scroller) => scroller.type)).toEqual([
      ScrollerType.LAST_LISTENED,
      ScrollerType.LAST_READ,
      ScrollerType.LATEST_ADDED,
      ScrollerType.RANDOM
    ]);
  });
});
