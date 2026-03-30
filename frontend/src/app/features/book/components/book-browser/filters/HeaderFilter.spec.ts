import {describe, expect, it} from 'vitest';

import {Book} from '../../../model/book.model';
import {filterBooksBySearchTerm, normalizeSearchTerm} from './HeaderFilter';

function makeBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    fileName: `Book ${id}.epub`,
    metadata: {
      bookId: id,
      title: `Title ${id}`,
      seriesName: `Series ${id}`,
      authors: [`Author ${id}`],
      categories: [`Category ${id}`],
      isbn10: `ISBN10-${id}`,
      isbn13: `ISBN13-${id}`
    },
    primaryFile: {id, bookId: id, fileName: `Book ${id}.epub`, bookType: 'EPUB'},
    ...overrides
  };
}

describe('HeaderFilter', () => {
  it('normalizes unicode, punctuation, whitespace, and empty input', () => {
    expect(normalizeSearchTerm('')).toBe('');
    expect(normalizeSearchTerm('  Ætna   øresund! ')).toBe('aetna oresund');
    expect(normalizeSearchTerm('Łódź / Straße')).toBe('lodz strasse');
  });

  it('keeps the full book list for short terms and filters by searchable fields', () => {
    const books = [
      makeBook(1),
      makeBook(2, {
        metadata: {
          bookId: 2,
          title: 'The Other Title',
          seriesName: 'Other Series',
          authors: ['Different Author'],
          categories: ['Biography'],
          isbn10: '999',
          isbn13: '888'
        },
        primaryFile: {id: 2, bookId: 2, fileName: 'Notes.txt', bookType: 'EPUB'}
      }),
      makeBook(3, {
        metadata: {
          bookId: 3,
          title: 'Completely Different',
          seriesName: 'Third Series',
          authors: ['Series Match'],
          categories: ['History'],
          isbn10: '111',
          isbn13: '222'
        },
        primaryFile: {id: 3, bookId: 3, fileName: 'Search Target.epub', bookType: 'EPUB'}
      })
    ];

    expect(filterBooksBySearchTerm(books, '')).toBe(books);
    expect(filterBooksBySearchTerm(books, 'a')).toBe(books);

    expect(filterBooksBySearchTerm(books, 'other title').map(book => book.id)).toEqual([2]);
    expect(filterBooksBySearchTerm(books, 'other series').map(book => book.id)).toEqual([2]);
    expect(filterBooksBySearchTerm(books, 'series match').map(book => book.id)).toEqual([3]);
    expect(filterBooksBySearchTerm(books, 'biography').map(book => book.id)).toEqual([2]);
    expect(filterBooksBySearchTerm(books, '222').map(book => book.id)).toEqual([3]);
    expect(filterBooksBySearchTerm(books, 'search target').map(book => book.id)).toEqual([3]);
  });
});
