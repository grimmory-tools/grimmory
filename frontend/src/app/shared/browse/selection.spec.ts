import {signal} from '@angular/core';
import {describe, expect, it, vi} from 'vitest';

import {createBrowseSelection, resolveSelectedIds, type BrowseSelectionItem} from './selection';

function harness(total: number, loadedIds: readonly number[]) {
  const filtersKey = signal('filters-a');
  const listKey = signal('list-a');
  const items = signal<readonly BrowseSelectionItem[]>(loadedIds.map(id => ({id})));
  const selection = createBrowseSelection({filtersKey, listKey, items, totalElements: signal(total)});
  return {selection, filtersKey, listKey, items};
}

describe('createBrowseSelection', () => {
  it('select all covers every match, exclusions come off the count and off the resolved ids', async () => {
    const {selection, items} = harness(50, [1, 2, 3, 4, 5]);
    selection.selectAll();
    selection.toggle(items()[1], 1, false);

    expect(selection.count()).toBe(49);
    expect(selection.isSelected(777)).toBe(true);
    expect(selection.isSelected(2)).toBe(false);

    const fetchIds = vi.fn(() => Promise.resolve<readonly number[]>([1, 2, 3]));
    expect(await resolveSelectedIds(selection.state(), fetchIds)).toEqual([1, 3]);
  });

  it('shift-click selects the range from the last plain click', () => {
    const {selection, items} = harness(100, [41, 42, 43, 44, 45, 46, 47]);
    selection.toggle(items()[1], 1, false);
    selection.toggle(items()[4], 4, true);

    expect([42, 43, 44, 45].every(id => selection.isSelected(id))).toBe(true);
    expect(selection.count()).toBe(4);
  });

  it('survives a sort change but resets when the filters change', () => {
    const {selection, items, listKey, filtersKey} = harness(100, [1, 2, 3]);
    selection.toggle(items()[0], 0, false);

    listKey.set('list-b');
    expect(selection.count()).toBe(1);

    filtersKey.set('filters-b');
    expect(selection.count()).toBe(0);
  });

  it('selects all member ids for a grouped item and across a shift range', () => {
    const items = signal([
      {id: 1, memberIds: [1, 2, 3]},
      {id: 4, memberIds: [4]},
      {id: 5, memberIds: [5, 6]},
    ]);
    const selection = createBrowseSelection({
      filtersKey: signal('filters'),
      listKey: signal('list'),
      items,
      totalElements: signal(6),
      itemIds: item => item.memberIds,
    });

    selection.toggle(items()[0], 0, false);
    expect(selection.count()).toBe(3);
    expect(selection.isItemSelected(items()[0])).toBe(true);

    selection.toggle(items()[2], 2, true);
    expect(selection.count()).toBe(6);

    selection.toggle(items()[0], 0, false);
    expect(selection.count()).toBe(3);
    expect(selection.isItemSelected(items()[0])).toBe(false);
  });
});
