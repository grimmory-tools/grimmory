import {convertToParamMap} from '@angular/router';
import {describe, expect, it} from 'vitest';

import {
  bookBrowseScope,
  bookBrowseScopeHiddenFacetKeys,
} from './book-browse-scope';

describe('bookBrowseScope', () => {
  it('maps each scoped route onto its stable facet', () => {
    expect(bookBrowseScope(convertToParamMap({libraryId: '3'}), {}))
      .toEqual({kind: 'library', entityId: 3, facets: {library: {include: ['3'], exclude: [], matchAll: true}}});
    expect(bookBrowseScope(convertToParamMap({shelfId: '7'}), {}))
      .toEqual({kind: 'shelf', entityId: 7, facets: {shelf: {include: ['7'], exclude: [], matchAll: true}}});
    expect(bookBrowseScope(convertToParamMap({magicShelfId: '9'}), {}))
      .toEqual({kind: 'magicShelf', entityId: 9, facets: {shelf: {include: ['magic:9'], exclude: [], matchAll: true}}});
  });

  it('treats unscoped and malformed routes as all books', () => {
    expect(bookBrowseScope(convertToParamMap({}), {})).toBeNull();
    expect(bookBrowseScope(convertToParamMap({libraryId: 'abc'}), {})).toBeNull();
    expect(bookBrowseScope(convertToParamMap({libraryId: '-2'}), {})).toBeNull();
  });
});

describe('bookBrowseScopeHiddenFacetKeys', () => {
  it('hides only the filters a dashboard row sets from the rail', () => {
    expect(bookBrowseScopeHiddenFacetKeys(null)).toEqual(new Set());
    expect(bookBrowseScopeHiddenFacetKeys(bookBrowseScope(convertToParamMap({libraryId: '3'}), {}))).toEqual(new Set());
    expect(bookBrowseScopeHiddenFacetKeys({
      kind: 'dashboardRow',
      titleKey: 'row',
      facets: {read_status: {include: [], exclude: ['READ']}, file_type: {include: ['EPUB'], exclude: []}},
    })).toEqual(new Set(['read_status', 'file_type']));
  });
});
