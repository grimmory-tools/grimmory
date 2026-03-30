import {describe, expect, it} from 'vitest';

import {IconCacheService} from './icon-cache.service';

describe('IconCacheService', () => {
  it('stores, retrieves, removes, and sorts cached icon names', () => {
    const service = new IconCacheService();
    const sanitized = {id: 'sanitized'} as never;

    expect(service.getCachedSanitized('missing')).toBeNull();

    service.cacheIcon('beta', '<svg />', sanitized);
    service.cacheIcon('alpha', '<svg />', sanitized);

    expect(service.getCachedSanitized('alpha')).toBe(sanitized);
    expect(service.getAllIconNames()).toEqual(['alpha', 'beta']);
    expect(service.removeIcon('beta')).toBe(true);
    expect(service.getCachedSanitized('beta')).toBeNull();
    expect(service.removeIcon('beta')).toBe(false);
  });
});
