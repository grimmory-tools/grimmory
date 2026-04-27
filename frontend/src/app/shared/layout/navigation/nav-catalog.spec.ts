import { describe, expect, it, vi } from 'vitest';

import { buildAllNavPages, buildHomeNavItems, buildQuickActionNavItems, findPageNavItem } from './nav-catalog';

const translate = (key: string): string => key;

describe('nav-catalog', () => {
  it('builds the shared home navigation items from the common model', () => {
    expect(buildHomeNavItems(translate).map((item) => item.id)).toEqual([
      'dashboard',
      'allBooks',
      'series',
      'authors',
      'notebook',
    ]);
  });

  it('gates palette page items by permission while keeping the common pages available', () => {
    expect(buildAllNavPages(translate, {}).map((item) => item.id)).toEqual([
      'dashboard',
      'allBooks',
      'series',
      'authors',
      'notebook',
      'settings',
    ]);

    expect(buildAllNavPages(translate, {
      canAccessLibraryStats: true,
      canAccessUserStats: true,
      canEditMetadata: true,
      canAccessBookdrop: true,
    }).map((item) => item.id)).toEqual([
      'dashboard',
      'allBooks',
      'series',
      'authors',
      'notebook',
      'settings',
      'libraryStats',
      'readingStats',
      'metadataManager',
      'bookdrop',
    ]);
  });

  it('looks up a single page nav item by id, gated by permissions', () => {
    expect(findPageNavItem('dashboard', translate, {})?.id).toBe('dashboard');
    expect(findPageNavItem('bookdrop', translate, {})).toBeNull();
    expect(findPageNavItem('bookdrop', translate, { canAccessBookdrop: true })?.id).toBe('bookdrop');
    expect(findPageNavItem('metadataManager', translate, { canManageLibrary: true })).toBeNull();
    expect(findPageNavItem('metadataManager', translate, { canEditMetadata: true })?.id).toBe('metadataManager');
    expect(findPageNavItem('unknown', translate, {})).toBeNull();
  });

  it('gates quick actions by permission while preserving the always-available actions', () => {
    const handlers = {
      createLibrary: vi.fn(),
      createShelf: vi.fn(),
      createMagicShelf: vi.fn(),
      uploadBook: vi.fn(),
    };

    expect(buildQuickActionNavItems(translate, {}, handlers).map((item) => item.id)).toEqual([
      'createShelf',
      'createMagicShelf',
    ]);

    expect(buildQuickActionNavItems(translate, {
      canManageLibrary: true,
      canUpload: true,
    }, handlers).map((item) => item.id)).toEqual([
      'createLibrary',
      'createShelf',
      'createMagicShelf',
      'uploadBook',
    ]);
  });
});
