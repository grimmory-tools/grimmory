import {describe, expect, it} from 'vitest';

import {BookBrowserScrollService} from './book-browser-scroll.service';

describe('BookBrowserScrollService', () => {
  const service = new BookBrowserScrollService();

  it('stores, retrieves, clears, and creates stable keys', () => {
    expect(service.getPosition('books')).toBeUndefined();

    service.savePosition('books', 144);
    service.savePosition('books:library', 288);

    expect(service.getPosition('books')).toBe(144);
    expect(service.getPosition('books:library')).toBe(288);
    expect(service.createKey('/books', {libraryId: '1', shelfId: '2'})).toBe('/books:1-2');
    expect(service.createKey('/books', {})).toBe('/books');

    service.clearPosition('books');
    expect(service.getPosition('books')).toBeUndefined();
  });
});
