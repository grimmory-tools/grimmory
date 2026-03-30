import {TestBed} from '@angular/core/testing';
import {convertToParamMap} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {BookBrowserEntityService} from './book-browser-entity.service';
import {Book} from '../../model/book.model';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {EntityType} from './book-browser.component';
import {LibraryService} from '../../service/library.service';
import {ShelfService} from '../../service/shelf.service';
import {MagicShelfService, MagicShelf} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';

function makeBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {bookId: id, title: `Book ${id}`},
    ...overrides
  };
}

describe('BookBrowserEntityService', () => {
  let service: BookBrowserEntityService;
  let libraryService: {libraries: ReturnType<typeof vi.fn>};
  let shelfService: {shelves: ReturnType<typeof vi.fn>};
  let magicShelfService: {findShelfById: ReturnType<typeof vi.fn>};
  let bookRuleEvaluatorService: {evaluateGroup: ReturnType<typeof vi.fn>};

  const library = {
    id: 1,
    name: 'Library',
    watch: true,
    paths: [{path: '/books'}]
  } as Library;
  const shelf = {id: 2, name: 'Shelf'} as Shelf;
  const magicShelf = {
    id: 3,
    name: 'Magic Shelf',
    filterJson: JSON.stringify({group: 'all'})
  } as MagicShelf;

  beforeEach(() => {
    libraryService = {
      libraries: vi.fn(() => [library])
    };
    shelfService = {
      shelves: vi.fn(() => [shelf])
    };
    magicShelfService = {
      findShelfById: vi.fn((id: number) => id === 3 ? magicShelf : null)
    };
    bookRuleEvaluatorService = {
      evaluateGroup: vi.fn((book: Book) => book.id === 2)
    };

    TestBed.configureTestingModule({
      providers: [
        BookBrowserEntityService,
        {provide: LibraryService, useValue: libraryService},
        {provide: ShelfService, useValue: shelfService},
        {provide: MagicShelfService, useValue: magicShelfService},
        {provide: BookRuleEvaluatorService, useValue: bookRuleEvaluatorService}
      ]
    });

    service = TestBed.inject(BookBrowserEntityService);
  });

  it('resolves entity ids from route params in priority order', () => {
    const allParams = convertToParamMap({
      libraryId: '1',
      shelfId: '2',
      magicShelfId: '3'
    });
    const shelfFirstParams = convertToParamMap({
      shelfId: '2',
      magicShelfId: '3'
    });
    const emptyParams = convertToParamMap({});

    expect(service.getEntityInfo(allParams)).toEqual({
      entityId: 1,
      entityType: EntityType.LIBRARY
    });
    expect(service.getEntityInfo(shelfFirstParams)).toEqual({
      entityId: 2,
      entityType: EntityType.SHELF
    });
    expect(service.getEntityInfo(emptyParams)).toEqual({
      entityId: NaN,
      entityType: EntityType.ALL_BOOKS
    });
  });

  it('returns entity records and recognizes library and magic shelf types', () => {
    expect(service.getEntity(1, EntityType.LIBRARY)).toBe(library);
    expect(service.getEntity(2, EntityType.SHELF)).toBe(shelf);
    expect(service.getEntity(3, EntityType.MAGIC_SHELF)).toBe(magicShelf);
    expect(service.getEntity(999, EntityType.ALL_BOOKS)).toBeNull();

    expect(service.isLibrary(library)).toBe(true);
    expect(service.isLibrary(shelf as Library | Shelf | MagicShelf)).toBe(false);
    expect(service.isMagicShelf(magicShelf)).toBe(true);
    expect(service.isMagicShelf(null)).toBe(false);
  });

  it('filters books by entity type and handles magic shelf parsing failures', () => {
    const books = [
      makeBook(1, {libraryId: 1, shelves: [{id: 2} as never]}),
      makeBook(2, {libraryId: 1, shelves: []}),
      makeBook(3, {libraryId: 2, shelves: [{id: 2} as never]})
    ];
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    expect(service.getBooksByEntity(books, 1, EntityType.LIBRARY)).toEqual([
      books[0],
      books[1]
    ]);
    expect(service.getBooksByEntity(books, 2, EntityType.SHELF)).toEqual([
      books[0],
      books[2]
    ]);
    expect(service.getBooksByEntity(books, 0, EntityType.UNSHELVED)).toEqual([
      books[1]
    ]);
    expect(service.getBooksByEntity(books, 0, EntityType.ALL_BOOKS)).toBe(books);

    expect(service.getBooksByEntity(books, 3, EntityType.MAGIC_SHELF)).toEqual([
      books[1]
    ]);
    expect(bookRuleEvaluatorService.evaluateGroup).toHaveBeenCalledWith(
      books[0],
      {group: 'all'},
      books
    );

    magicShelfService.findShelfById.mockReturnValueOnce({
      id: 4,
      name: 'Broken Magic Shelf',
      filterJson: '{broken json'
    } as MagicShelf);

    expect(service.getBooksByEntity(books, 4, EntityType.MAGIC_SHELF)).toEqual([]);
    expect(warnSpy).toHaveBeenCalledWith('Invalid filterJson for MagicShelf');
  });
});
