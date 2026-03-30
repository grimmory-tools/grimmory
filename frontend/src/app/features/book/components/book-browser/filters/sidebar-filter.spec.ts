import {describe, expect, it} from 'vitest';

import {Book, ReadStatus} from '../../../model/book.model';
import {filterBooksByFilters} from './sidebar-filter';

function makeBook(
  id: number,
  overrides: Partial<Book> = {}
): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Test Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`,
      authors: [],
      categories: [],
    },
    shelves: [],
    readStatus: ReadStatus.UNREAD,
    ...overrides,
  };
}

describe('filterBooksByFilters', () => {
  const books: Book[] = [
    makeBook(1, {
      metadata: {
        bookId: 1,
        title: 'Sci-Fi Read',
        authors: ['Ada Lovelace'],
        categories: ['Sci-Fi'],
      },
      readStatus: ReadStatus.READ,
      libraryId: 1,
    }),
    makeBook(2, {
      metadata: {
        bookId: 2,
        title: 'Fantasy Unread',
        authors: ['Grace Hopper'],
        categories: ['Fantasy'],
      },
      readStatus: ReadStatus.UNREAD,
      libraryId: 2,
    }),
    makeBook(3, {
      metadata: {
        bookId: 3,
        title: 'Sci-Fi Unread',
        authors: ['Ada Lovelace'],
        categories: ['Sci-Fi'],
      },
      readStatus: ReadStatus.UNREAD,
      libraryId: 2,
    }),
  ];

  it('requires every selected filter to match in and mode', () => {
    const filtered = filterBooksByFilters(
      books,
      {
        author: ['Ada Lovelace'],
        category: ['Sci-Fi'],
        readStatus: [ReadStatus.UNREAD],
      },
      'and'
    );

    expect(filtered.map(book => book.id)).toEqual([3]);
  });

  it('matches any selected filter in or mode', () => {
    const filtered = filterBooksByFilters(
      books,
      {
        author: ['Grace Hopper'],
        readStatus: [ReadStatus.READ],
      },
      'or'
    );

    expect(filtered.map(book => book.id)).toEqual([1, 2]);
  });

  it('excludes matching books in not mode and ignores the excluded filter type', () => {
    const filtered = filterBooksByFilters(
      books,
      {
        author: ['Ada Lovelace'],
        library: [2],
      },
      'not',
      'library'
    );

    expect(filtered.map(book => book.id)).toEqual([2]);
  });
});
