import {describe, expect, it} from 'vitest';

import {AuthorSelectionService} from './author-selection.service';

describe('AuthorSelectionService', () => {
  const authors = [
    {id: 1, name: 'Ada', bookCount: 1, hasPhoto: false},
    {id: 2, name: 'Bert', bookCount: 2, hasPhoto: true},
    {id: 3, name: 'Cy', bookCount: 3, hasPhoto: false},
    {id: 4, name: 'Dee', bookCount: 4, hasPhoto: true},
  ];

  it('selects and deselects authors and exposes the selected count', () => {
    const service = new AuthorSelectionService();
    service.setCurrentAuthors(authors);

    service.handleCheckboxClick({
      index: 1,
      author: authors[1],
      selected: true,
      shiftKey: false,
    });

    expect(service.getSelectedIds()).toEqual([2]);
    expect(service.selectedCount()).toBe(1);

    service.handleCheckboxClick({
      index: 1,
      author: authors[1],
      selected: false,
      shiftKey: false,
    });

    expect(service.getSelectedIds()).toEqual([]);
    expect(service.selectedCount()).toBe(0);
  });

  it('supports shift-click range selection and deselection', () => {
    const service = new AuthorSelectionService();
    service.setCurrentAuthors(authors);

    service.handleCheckboxClick({
      index: 1,
      author: authors[1],
      selected: true,
      shiftKey: false,
    });
    service.handleCheckboxClick({
      index: 3,
      author: authors[3],
      selected: true,
      shiftKey: true,
    });

    expect(service.getSelectedIds()).toEqual([2, 3, 4]);

    service.handleCheckboxClick({
      index: 2,
      author: authors[2],
      selected: false,
      shiftKey: true,
    });

    expect(service.getSelectedIds()).toEqual([4]);
  });

  it('selects and clears the full current author list', () => {
    const service = new AuthorSelectionService();
    service.setCurrentAuthors(authors);

    service.selectAll();
    expect(service.getSelectedIds()).toEqual([1, 2, 3, 4]);

    service.deselectAll();
    expect(service.getSelectedIds()).toEqual([]);
    expect(service.selectedCount()).toBe(0);
  });
});
