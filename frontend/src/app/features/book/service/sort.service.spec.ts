import {describe, expect, it, vi} from 'vitest';

import {SortDirection, SortOption} from '../model/sort.model';
import {Book, ReadStatus} from '../model/book.model';
import {SortService} from './sort.service';

function makeBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    fileName: `Book ${id}`,
    primaryFile: {id, bookId: id, bookType: 'EPUB'},
    metadata: {
      bookId: id,
      title: `Book ${id}`,
      authors: [`Author ${id}`],
      seriesName: `Series ${id}`,
      publishedDate: '2024-01-01',
      pageCount: id * 100,
    },
    ...overrides,
  };
}

describe('SortService', () => {
  const service = new SortService();

  it('returns the original array when no sort is provided', () => {
    const books = [makeBook(1), makeBook(2)];

    expect(service.applySort(books, null)).toBe(books);
    expect(service.applyMultiSort(books, [])).toBe(books);
  });

  it('sorts by title and file name using natural ordering', () => {
    const books = [
      makeBook(2, {fileName: 'Book 10', metadata: {bookId: 2, title: 'Book 10'}}),
      makeBook(1, {fileName: 'Book 2', metadata: {bookId: 1, title: 'Book 2'}}),
    ];
    const sortOption: SortOption = {label: 'Title', field: 'title', direction: SortDirection.ASCENDING};

    expect(service.applySort(books, sortOption).map(book => book.id)).toEqual([1, 2]);
  });

  it('sorts by primaryFile file name if file name is missing', () => {
    const books = [
      makeBook(2, {primaryFile: { id: 2, bookId: 2, fileName: 'Book 2' }, metadata: {bookId: 2, title: 'Book 10'}}),
      makeBook(1, {fileName: 'Book 1', primaryFile: { id: 1, bookId: 1, fileName: 'Book 3' }, metadata: {bookId: 1, title: 'Book 2'}}),
    ];
    const sortOption: SortOption = {label: 'File Name', field: 'fileName', direction: SortDirection.ASCENDING};

    expect(service.applySort(books, sortOption).map(book => book.id)).toEqual([1, 2]);
  });

  it('sorts by array fields and read status rank', () => {
    const books = [
      makeBook(1, {metadata: {bookId: 1, authors: ['Jane Zed']}, readStatus: ReadStatus.READ}),
      makeBook(2, {metadata: {bookId: 2, authors: ['Adam Alpha']}, readStatus: ReadStatus.READING}),
      makeBook(3, {metadata: {bookId: 3, authors: ['Jane Zed']}, readStatus: ReadStatus.UNREAD}),
    ];

    expect(service.applyMultiSort(books, [
      {label: 'Authors', field: 'authorSurnameVorname', direction: SortDirection.ASCENDING},
    ]).map(book => book.id)).toEqual([2, 1, 3]);

    expect(service.applyMultiSort(books, [
      {label: 'Status', field: 'readStatus', direction: SortDirection.DESCENDING},
    ]).map(book => book.id)).toEqual([1, 2, 3]);
  });

  it('keeps null values sorted after non-null values and warns on unknown fields', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const books = [
      makeBook(1, {metadata: {bookId: 1}}),
      makeBook(2, {metadata: {bookId: 2, publishedDate: '2023-01-01'}}),
    ];

    expect(service.applyMultiSort(books, [
      {label: 'Published', field: 'publishedDate', direction: SortDirection.ASCENDING},
    ]).map(book => book.id)).toEqual([2, 1]);

    expect(service.applyMultiSort(books, [
      {label: 'Unknown', field: 'doesNotExist', direction: SortDirection.ASCENDING},
    ])).toEqual(books);
    expect(warnSpy).toHaveBeenCalledWith('[SortService] No extractor for field: doesNotExist');
  });
});
