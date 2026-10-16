import {convertToParamMap} from '@angular/router';
import {describe, expect, it} from 'vitest';

import {EMPTY_FACET_SELECTION} from '../data/book-query-params';
import {
  bookBrowseScope,
  scopedFacetSelection,
  UNSHELVED_BROWSE_SCOPE,
  type BookBrowseScope,
} from './book-browse-scope';

const ROW_SCOPE: BookBrowseScope = {
  kind: 'dashboardRow',
  rowId: '3',
  titleKey: 'dashboard.scroller.continueReading',
  facets: {read_status: ['READING'], '-file_type': ['AUDIOBOOK']},
};

describe('bookBrowseScope', () => {
  it('maps each scoped route onto its stable facet', () => {
    expect(bookBrowseScope(convertToParamMap({libraryId: '3'}), {}))
      .toEqual({kind: 'library', entityId: 3, facets: {'+library': ['3']}});
    expect(bookBrowseScope(convertToParamMap({shelfId: '7'}), {}))
      .toEqual({kind: 'shelf', entityId: 7, facets: {'+shelf': ['7']}});
    expect(bookBrowseScope(convertToParamMap({magicShelfId: '9'}), {}))
      .toEqual({kind: 'magicShelf', entityId: 9, facets: {'+shelf': ['magic:9']}});
  });

  it('treats unscoped and malformed routes as all books', () => {
    expect(bookBrowseScope(convertToParamMap({}), {})).toBeNull();
    expect(bookBrowseScope(convertToParamMap({libraryId: 'abc'}), {})).toBeNull();
    expect(bookBrowseScope(convertToParamMap({libraryId: '-2'}), {})).toBeNull();
  });
});

describe('scopedFacetSelection', () => {
  it('passes selections through unchanged without a scope', () => {
    const selection = {genre: ['Fantasy']};
    expect(scopedFacetSelection(selection, null)).toBe(selection);
  });

  it('adds the scope as a must-have alongside other picks, even on an empty selection', () => {
    const library = bookBrowseScope(convertToParamMap({libraryId: '3'}), {});
    expect(scopedFacetSelection({genre: ['Fantasy'], library: ['99']}, library)).toEqual({
      genre: ['Fantasy'],
      library: ['99'],
      '+library': ['3'],
    });

    expect(scopedFacetSelection(EMPTY_FACET_SELECTION, UNSHELVED_BROWSE_SCOPE)).toEqual({'+shelf_status': ['unshelved']});
  });

  it('keeps user picks alongside marked scope facets, and the scope value for any-of facets', () => {
    expect(scopedFacetSelection({'-file_type': ['PDF'], read_status: ['UNREAD'], genre: ['Fantasy']}, ROW_SCOPE)).toEqual({
      genre: ['Fantasy'],
      read_status: ['READING'],
      '-file_type': ['PDF', 'AUDIOBOOK'],
    });
  });
});
