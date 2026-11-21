import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects, flushQueryAsync} from '../../../core/testing/query-testing';
import type {Book, BookMetadata} from '../model/book.model';
import type {Shelf} from '../model/shelf.model';
import {bookQueryKeys} from '../data/book-query-keys';
import {AuthService} from '../../../shared/service/auth.service';
import {BookPatchService} from './book-patch.service';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {BookSocketService} from './book-socket.service';
import {BookService} from './book.service';

type BuildBookOverrides = Omit<Partial<Book>, 'metadata' | 'shelves'> & {
  metadata?: Partial<BookMetadata>;
  shelves?: Shelf[];
};

function buildShelf(id: number, overrides: Partial<Shelf> = {}): Shelf {
  return {
    id,
    name: `Shelf ${id}`,
    userId: 7,
    bookCount: 0,
    ...overrides,
  };
}

function buildBook(id: number, overrides: BuildBookOverrides = {}): Book {
  const {metadata, ...bookOverrides} = overrides;

  return {
    id,
    libraryId: 1,
    libraryName: 'Main Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`,
      ...metadata,
    },
    ...bookOverrides,
  };
}

async function flushBooksQuery(): Promise<void> {
  await flushQueryAsync();
}

describe('BookService', () => {
  let service: BookService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;

  function setup(initialToken: string | null = 'token-123'): void {
    authService = createAuthServiceStub(initialToken);
    queryClientHarness = createQueryClientHarness();
    queryClientHarness.queryClient.setDefaultOptions({
      queries: {
        retry: false,
      },
    });

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        BookService,
        {provide: AuthService, useValue: authService},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {
          provide: BookSocketService,
          useValue: {
            handleNewlyCreatedBook: vi.fn(),
            handleRemovedBookIds: vi.fn(),
            handleBookUpdate: vi.fn(),
            handleBookMetadataUpdate: vi.fn(),
            handleMultipleBookCoverPatches: vi.fn(),
            handleTaskProgress: vi.fn(),
            handleReconnect: vi.fn(),
          },
        },
        {
          provide: BookPatchService,
          useValue: {
            savePdfProgress: vi.fn(),
            saveCbxProgress: vi.fn(),
            updateDateFinished: vi.fn(),
            resetProgress: vi.fn(),
            updateBookReadStatus: vi.fn(),
            resetPersonalRating: vi.fn(),
            updatePersonalRating: vi.fn(),
            updateBookShelves: vi.fn(),
          },
        },
        {
          provide: TranslocoService,
          useValue: {
            translate: vi.fn((key: string) => key),
          },
        },
      ],
    });

    service = TestBed.inject(BookService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  }

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    httpTestingController?.verify();
    queryClientHarness?.queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('removes a shelf from the cached books query without disturbing other shelf assignments', async () => {
    setup();

    const targetShelf = buildShelf(10, {name: 'Favorites'});
    const untouchedShelf = buildShelf(11, {name: 'Archive'});
    const initialBooks = [
      buildBook(1, {shelves: [targetShelf, untouchedShelf]}),
      buildBook(2, {shelves: [targetShelf]}),
    ];

    queryClientHarness.queryClient.setQueryData(BOOKS_QUERY_KEY, initialBooks);

    queryClientHarness.queryClient.setQueryData(bookQueryKeys.detail(1, false), {id: 1});
    service.removeBooksFromShelf(10);
    await flushBooksQuery();
    const reconciledBooks = [
      buildBook(1, {shelves: [untouchedShelf]}),
      buildBook(2, {shelves: []}),
    ];
    expect(queryClientHarness.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual(reconciledBooks);
    expect(queryClientHarness.queryClient.getQueryState(bookQueryKeys.detail(1, false))?.isInvalidated).toBe(true);
    expect(queryClientHarness.queryClient.getQueryState(BOOKS_QUERY_KEY)?.isInvalidated).toBe(false);
    httpTestingController.expectNone(req => req.url.endsWith('/api/v1/books'));
  });

  it('removes the books query cache when the auth token is cleared', async () => {
    setup();

    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries');

    queryClientHarness.queryClient.setQueryData(BOOKS_QUERY_KEY, [
      buildBook(1),
      buildBook(2),
    ]);

    expect(queryClientHarness.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      buildBook(1),
      buildBook(2),
    ]);

    authService.token.set(null);
    await flushBooksQuery();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY});
    expect(queryClientHarness.queryClient.getQueryData(BOOKS_QUERY_KEY)).toBeUndefined();
  });
});
