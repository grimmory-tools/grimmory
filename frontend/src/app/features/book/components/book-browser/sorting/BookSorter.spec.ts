import {describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {CdkDragDrop} from '@angular/cdk/drag-drop';

import {SortDirection, SortOption} from '../../../model/sort.model';
import {BookSorter} from './BookSorter';

describe('BookSorter', () => {
  it('uses default labels when no transloco service is provided', () => {
    const sorter = new BookSorter(() => undefined);

    expect(sorter.sortOptions[0]).toMatchObject({
      field: 'title',
      label: 'Title',
      direction: SortDirection.ASCENDING
    });
  });

  it('uses translated labels when transloco is available', () => {
    const transloco = {
      translate: vi.fn((key: string) => `translated:${key}`)
    } as unknown as TranslocoService;

    const sorter = new BookSorter(() => undefined, transloco);

    expect(sorter.sortOptions[0].label).toBe('translated:book.sorting.options.title');
  });

  it('supports legacy selectedSort getter and setter', () => {
    const sorter = new BookSorter(() => undefined);
    const sort: SortOption = {label: 'Title', field: 'title', direction: SortDirection.ASCENDING};

    sorter.selectedSort = sort;
    expect(sorter.selectedSort).toEqual(sort);

    sorter.selectedSort = undefined;
    expect(sorter.selectedSortCriteria).toEqual([]);
  });

  it('sorts by a field and toggles the primary direction on repeat', () => {
    const onSortChange = vi.fn();
    const sorter = new BookSorter(onSortChange);

    sorter.sortBooks('title');
    expect(sorter.selectedSortCriteria).toEqual([
      {label: 'Title', field: 'title', direction: SortDirection.ASCENDING}
    ]);

    sorter.sortBooks('title');
    expect(sorter.selectedSortCriteria[0].direction).toBe(SortDirection.DESCENDING);
    expect(onSortChange).toHaveBeenCalledTimes(2);
  });

  it('adds, removes, toggles, and reorders criteria', () => {
    const onSortChange = vi.fn();
    const sorter = new BookSorter(onSortChange);

    sorter.addSortCriterion('title');
    sorter.addSortCriterion('author');
    sorter.addSortCriterion('title');
    expect(sorter.selectedSortCriteria.map((criterion) => criterion.field)).toEqual(['title', 'author']);

    sorter.toggleCriterionDirection(1);
    expect(sorter.selectedSortCriteria[1].direction).toBe(SortDirection.DESCENDING);

    sorter.reorderCriteria({
      previousIndex: 1,
      currentIndex: 0
    } as unknown as CdkDragDrop<SortOption[]>);
    expect(sorter.selectedSortCriteria.map((criterion) => criterion.field)).toEqual(['author', 'title']);

    sorter.removeSortCriterion(0);
    expect(sorter.selectedSortCriteria.map((criterion) => criterion.field)).toEqual(['title']);
  });

  it('omits already-used fields from available options and marks the primary icon', () => {
    const sorter = new BookSorter(() => undefined);

    sorter.setSortCriteria([
      {label: 'Title', field: 'title', direction: SortDirection.ASCENDING}
    ]);

    const titleOption = sorter.sortOptions.find((option) => option.field === 'title') as SortOption & {icon?: string};

    expect(sorter.getAvailableSortOptions().some((option) => option.field === 'title')).toBe(false);
    expect(titleOption.icon).toBe('pi pi-arrow-up');
  });
});
