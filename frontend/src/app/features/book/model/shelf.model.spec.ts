import {describe, expect, expectTypeOf, it} from 'vitest';

import {Shelf} from './shelf.model';
import {SortDirection} from './sort.model';

describe('shelf.model', () => {
  it('supports optional presentation and ownership fields', () => {
    const shelf: Shelf = {
      id: 3,
      name: 'Favorites',
      icon: 'pi pi-heart',
      publicShelf: true,
      userId: 9,
      bookCount: 24,
      sort: {
        label: 'Title',
        field: 'title',
        direction: SortDirection.ASCENDING
      }
    };

    expect(shelf.name).toBe('Favorites');
    expect(shelf.sort?.direction).toBe(SortDirection.ASCENDING);
  });

  it('allows nullable icon values', () => {
    expectTypeOf<Shelf['icon']>().toEqualTypeOf<string | null | undefined>();
  });
});
