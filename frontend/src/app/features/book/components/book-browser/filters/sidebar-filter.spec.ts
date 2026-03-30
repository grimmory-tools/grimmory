import {describe, expect, it} from 'vitest';

import {Book, ReadStatus} from '../../../model/book.model';
import {
  doesBookMatchFilter,
  doesBookMatchReadStatus,
  filterBooksByFilters,
  isFileSizeInRange,
  isMatchScoreInRange,
  isPageCountInRange,
  isRatingInRange,
  isRatingInRange10
} from './sidebar-filter';

function makeBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    readStatus: ReadStatus.UNREAD,
    fileSizeKb: 1500,
    metadataMatchScore: 87,
    metadata: {
      bookId: id,
      title: `Title ${id}`,
      authors: [`Author ${id}`],
      categories: [`Category ${id}`],
      seriesName: `Series ${id}`,
      publisher: 'Publisher',
      publishedDate: '2024-02-01',
      pageCount: 230,
      language: 'en',
      tags: ['tag-a'],
      moods: ['mood-a'],
      amazonRating: 4.3,
      goodreadsRating: 4.1,
      hardcoverRating: 4.2,
      narrator: 'Narrator',
      ageRating: 16,
      contentRating: 'PG-13',
      comicMetadata: {
        characters: ['hero'],
        teams: ['team-a'],
        locations: ['earth'],
        pencillers: ['Penciller'],
        inkers: ['Inker'],
        colorists: ['Colorist'],
        letterers: ['Letterer'],
        coverArtists: ['Cover'],
        editors: ['Editor']
      }
    },
    shelves: [{id: 7, name: 'Favorites'}],
    ...overrides
  };
}

describe('sidebar-filter', () => {
  it('covers the numeric helper ranges and read status matching', () => {
    expect(isRatingInRange(null, 1)).toBe(false);
    expect(isRatingInRange(4.3, '4')).toBe(true);
    expect(isRatingInRange10(4.4, 4)).toBe(true);
    expect(isFileSizeInRange(undefined, 1)).toBe(false);
    expect(isFileSizeInRange(1500, 1)).toBe(true);
    expect(isPageCountInRange(230, '3')).toBe(true);
    expect(isMatchScoreInRange(87, 2)).toBe(true);
    expect(isMatchScoreInRange(87, 9)).toBe(false);
    expect(doesBookMatchReadStatus(makeBook(1), [ReadStatus.UNREAD])).toBe(true);
    expect(doesBookMatchReadStatus(makeBook(1, {readStatus: undefined}), [ReadStatus.UNSET])).toBe(true);
  });

  it('matches all filter branches across or, and, and not modes', () => {
    const book = makeBook(1);
    const books = [book, makeBook(2, {
      readStatus: ReadStatus.READING,
      metadata: {
        bookId: 2,
        title: 'Other',
        authors: ['Other Author'],
        categories: ['Other Category'],
        seriesName: 'Other Series',
        publisher: 'Other Publisher',
        publishedDate: '2021-01-01',
        pageCount: 50,
        language: 'fr',
        tags: ['other-tag'],
        moods: ['other-mood'],
        amazonRating: 3.1,
        goodreadsRating: 3.2,
        hardcoverRating: 3.3,
        narrator: 'Someone Else',
        ageRating: 12,
        contentRating: 'R',
        comicMetadata: {
          characters: ['villain'],
          teams: ['team-b'],
          locations: ['mars'],
          pencillers: ['Alt Penciller'],
          inkers: ['Alt Inker'],
          colorists: ['Alt Colorist'],
          letterers: ['Alt Letterer'],
          coverArtists: ['Alt Cover'],
          editors: ['Alt Editor']
        }
      },
      shelves: []
    })];

    expect(doesBookMatchFilter(book, 'author', ['Author 1'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'category', ['Category 1'], 'and')).toBe(true);
    expect(doesBookMatchFilter(book, 'series', ['Series 1'], 'not')).toBe(true);
    expect(doesBookMatchFilter(book, 'bookType', ['PHYSICAL'], 'or')).toBe(false);
    expect(doesBookMatchFilter(book, 'readStatus', [ReadStatus.UNREAD], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'publisher', ['Publisher'], 'and')).toBe(true);
    expect(doesBookMatchFilter(book, 'matchScore', [2], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'library', [1], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'shelf', [7], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'shelfStatus', ['shelved'], 'or')).toBe(true);
    expect(doesBookMatchFilter(makeBook(3, {shelves: []}), 'shelfStatus', ['unshelved'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'tag', ['tag-a'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'publishedDate', ['2024'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'fileSize', [1], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'amazonRating', [4], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'goodreadsRating', [4], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'hardcoverRating', [4], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'language', ['en'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'pageCount', [3], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'mood', ['mood-a'], 'and')).toBe(true);
    expect(doesBookMatchFilter(book, 'ageRating', ['16'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'contentRating', ['PG-13'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'narrator', ['Narrator'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'comicCharacter', ['hero'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'comicTeam', ['team-a'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'comicLocation', ['earth'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'comicCreator', ['Penciller:penciller'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'unknown', ['x'], 'or')).toBe(false);

    expect(doesBookMatchFilter(makeBook(4, {isPhysical: true}), 'bookType', ['PHYSICAL'], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'author', [], 'or')).toBe(true);
    expect(doesBookMatchFilter(book, 'author', [], 'and')).toBe(false);

    expect(filterBooksByFilters(books, null, 'or')).toBe(books);
    expect(filterBooksByFilters(books, {author: ['Author 1']}, 'or')).toEqual([book]);
    expect(filterBooksByFilters(books, {author: ['Other Author']}, 'not')).toEqual([book]);
    expect(filterBooksByFilters(books, {author: ['Author 1'], category: ['Category 1']}, 'and')).toEqual([book]);
    expect(filterBooksByFilters(books, {author: ['Other Author'], category: ['Category 1']}, 'or', 'author')).toEqual([book]);
  });
});
