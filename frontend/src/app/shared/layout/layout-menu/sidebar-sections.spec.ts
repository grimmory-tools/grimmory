import { describe, expect, it, vi } from 'vitest';

import { Library } from '../../../features/book/model/library.model';
import { Shelf } from '../../../features/book/model/shelf.model';
import { MagicShelf } from '../../../features/magic-shelf/service/magic-shelf.service';
import { normalizeSortPref } from '../sidebar-sort-preferences';
import {
  buildHomeSection,
  buildLibrarySection,
  buildMagicShelfSection,
  buildShelfSection,
} from './sidebar-sections';

const translate = (key: string): string => key;

function library(overrides: Partial<Library> & { name: string }): Library {
  return { paths: [], watch: false, ...overrides };
}

function shelf(overrides: Partial<Shelf> & { name: string }): Shelf {
  return { ...overrides };
}

function magicShelf(overrides: Partial<MagicShelf> & { name: string }): MagicShelf {
  return { filterJson: '{}', ...overrides };
}

const libraryDeps = {
  health: { isUnhealthy: (id: number) => id === 99 },
  menuItems: { initializeLibraryMenuItems: vi.fn(() => []) },
};

const shelfDeps = {
  menuItems: { initializeShelfMenuItems: vi.fn(() => []) },
};

const magicShelfDeps = {
  menuItems: { initializeMagicShelfMenuItems: vi.fn(() => []) },
};

describe('normalizeSortPref', () => {
  it('defaults to name/asc and only honors known field/order values', () => {
    expect(normalizeSortPref({}, { field: 'name', order: 'asc' })).toEqual({ field: 'name', order: 'asc' });
    expect(normalizeSortPref({ field: 'id', order: 'desc' }, { field: 'name', order: 'asc' })).toEqual({ field: 'id', order: 'desc' });
    expect(normalizeSortPref({ field: 'bogus', order: 'sideways' }, { field: 'name', order: 'asc' })).toEqual({ field: 'name', order: 'asc' });
  });
});

describe('buildHomeSection', () => {
  it('emits the standard home items and attaches counts only to counted entries', () => {
    const [section] = buildHomeSection(translate, { allBooks: 12, series: 3, authors: 7 });

    expect(section.id).toBe('home');
    expect(section.expandable).toBe(true);
    expect(section.items?.map((item) => item.id)).toEqual([
      'dashboard', 'allBooks', 'series', 'authors', 'notebook',
    ]);

    const counts = Object.fromEntries(
      (section.items ?? []).map((item) => [item.id, item.bookCount]),
    );
    expect(counts).toEqual({
      dashboard: undefined,
      allBooks: 12,
      series: 3,
      authors: 7,
      notebook: undefined,
    });
  });
});

describe('buildLibrarySection', () => {
  it('returns no section when no libraries are persisted', () => {
    expect(buildLibrarySection([], new Map(), { field: 'name', order: 'asc' }, translate, libraryDeps)).toEqual([]);
    expect(buildLibrarySection(
      [library({ name: 'pending' })],
      new Map(),
      { field: 'name', order: 'asc' },
      translate,
      libraryDeps,
    )).toEqual([]);
  });

  it('sorts ascending and descending by name', () => {
    const libs = [
      library({ id: 1, name: 'Charlie' }),
      library({ id: 2, name: 'Alpha' }),
      library({ id: 3, name: 'Bravo' }),
    ];

    const asc = buildLibrarySection(libs, new Map(), { field: 'name', order: 'asc' }, translate, libraryDeps);
    expect(asc[0].items?.map((item) => item.label)).toEqual(['Alpha', 'Bravo', 'Charlie']);

    const desc = buildLibrarySection(libs, new Map(), { field: 'name', order: 'desc' }, translate, libraryDeps);
    expect(desc[0].items?.map((item) => item.label)).toEqual(['Charlie', 'Bravo', 'Alpha']);
  });

  it('sorts by id when requested', () => {
    const libs = [
      library({ id: 3, name: 'Gamma' }),
      library({ id: 1, name: 'Alpha' }),
      library({ id: 2, name: 'Beta' }),
    ];

    const result = buildLibrarySection(libs, new Map(), { field: 'id', order: 'asc' }, translate, libraryDeps);
    expect(result[0].items?.map((item) => item.id)).toEqual(['library:1', 'library:2', 'library:3']);
  });

  it('renders each library with route, count, health, and icon metadata', () => {
    const libs = [library({ id: 5, name: 'Fiction', icon: 'pi-book', iconType: 'PRIME_NG' })];
    const counts = new Map([[5, 42]]);

    const [section] = buildLibrarySection(libs, counts, { field: 'name', order: 'asc' }, translate, libraryDeps);
    expect(section.items?.[0]).toMatchObject({
      id: 'library:5',
      label: 'Fiction',
      type: 'library',
      icon: 'pi-book',
      iconType: 'PRIME_NG',
      routerLink: ['/library/5/books'],
      bookCount: 42,
      unhealthy: false,
    });
  });

  it('flags unhealthy libraries via the injected health service', () => {
    const libs = [library({ id: 99, name: 'Broken' })];

    const [section] = buildLibrarySection(libs, new Map(), { field: 'name', order: 'asc' }, translate, libraryDeps);
    expect(section.items?.[0].unhealthy).toBe(true);
  });
});

describe('buildShelfSection', () => {
  const sort = { field: 'name', order: 'asc' } as const;

  it('renders Unshelved even when no shelves are persisted', () => {
    const [section] = buildShelfSection([], new Map(), 5, sort, translate, shelfDeps);

    expect(section.items).toEqual([
      expect.objectContaining({
        id: 'shelfUnshelved',
        routerLink: ['/unshelved-books'],
        bookCount: 5,
      }),
    ]);
  });

  it('always renders Unshelved as the first item when shelves exist', () => {
    const shelves = [shelf({ id: 1, name: 'Reading' })];
    const [section] = buildShelfSection(shelves, new Map(), 9, sort, translate, shelfDeps);

    expect(section.items?.[0]).toMatchObject({
      id: 'shelfUnshelved',
      routerLink: ['/unshelved-books'],
      bookCount: 9,
    });
  });

  it('pins the Kobo shelf directly after Unshelved when marked as the system shelf', () => {
    const shelves = [
      shelf({ id: 1, name: 'Reading' }),
      shelf({ id: 2, name: 'Kobo', systemKey: 'kobo' }),
      shelf({ id: 3, name: 'Archive' }),
    ];
    const [section] = buildShelfSection(shelves, new Map(), 0, sort, translate, shelfDeps);

    expect(section.items?.map((item) => item.label))
      .toEqual(['layout.menu.unshelved', 'Kobo', 'Archive', 'Reading']);
  });

  it('keeps the standard ordering when no shelf is marked for pinning', () => {
    const shelves = [
      shelf({ id: 1, name: 'Reading' }),
      shelf({ id: 2, name: 'Kobo' }),
      shelf({ id: 3, name: 'Archive' }),
    ];
    const [section] = buildShelfSection(shelves, new Map(), 0, sort, translate, shelfDeps);

    expect(section.items?.map((item) => item.label))
      .toEqual(['layout.menu.unshelved', 'Archive', 'Kobo', 'Reading']);
  });

});

describe('buildMagicShelfSection', () => {
  const sort = { field: 'name', order: 'asc' } as const;

  it('returns no section when no magic shelves are persisted', () => {
    expect(buildMagicShelfSection([], new Map(), sort, translate, magicShelfDeps)).toEqual([]);
  });

  it('renders sorted magic shelves with route, icon, and count', () => {
    const shelves = [
      magicShelf({ id: 2, name: 'Beta', icon: 'pi-sparkles', iconType: 'PRIME_NG' }),
      magicShelf({ id: 1, name: 'Alpha' }),
    ];
    const counts = new Map([[1, 4], [2, 7]]);

    const [section] = buildMagicShelfSection(shelves, counts, sort, translate, magicShelfDeps);
    expect(section.items).toEqual([
      expect.objectContaining({
        id: 'magicShelf:1', label: 'Alpha', routerLink: ['/magic-shelf/1/books'], bookCount: 4,
      }),
      expect.objectContaining({
        id: 'magicShelf:2', label: 'Beta', icon: 'pi-sparkles', iconType: 'PRIME_NG',
        routerLink: ['/magic-shelf/2/books'], bookCount: 7,
      }),
    ]);
  });
});
