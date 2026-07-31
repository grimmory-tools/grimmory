import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects, flushQueryAsync} from '../../../core/testing/query-testing';
import type {Book, BookMetadata} from '../model/book.model';
import type {Shelf} from '../model/shelf.model';
import {AuthService} from '../../../shared/service/auth.service';
import {BookPatchService} from './book-patch.service';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {BookSocketService} from './book-socket.service';
import {BookService} from './book.service';
import {AppBooksApiService} from './app-books-api.service';

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
  let router: Router;

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
        {
          provide: AppBooksApiService,
          useValue: {
            enableGlobalFilterOptions: vi.fn(),
            books: () => [],
            globalFilterOptions: () => ({
              authors: [{name: 'Le Guin', count: 1}, {name: 'Pratchett', count: 1}],
              categories: [{name: 'Fantasy', count: 2}, {name: 'Humor', count: 1}],
              moods: [{name: 'Calm', count: 2}, {name: 'Funny', count: 1}],
              tags: [{name: 'Classic', count: 2}, {name: 'Satire', count: 1}],
              publishers: [{name: 'Ace', count: 1}, {name: 'Corgi', count: 1}],
              series: [{name: 'Earthsea', count: 1}, {name: 'Discworld', count: 1}],
            }),
          },
        },
        {provide: AuthService, useValue: authService},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: Router, useValue: {navigate: vi.fn()}},
        {
          provide: BookSocketService,
          useValue: {
            handleNewlyCreatedBook: vi.fn(),
            handleRemovedBookIds: vi.fn(),
            handleBookUpdate: vi.fn(),
            handleMultipleBookUpdates: vi.fn(),
            handleBookMetadataUpdate: vi.fn(),
            handleMultipleBookCoverPatches: vi.fn(),
            handleBookRecommendationsUpdate: vi.fn(),
          },
        },
        {
          provide: BookPatchService,
          useValue: {
            updateLastReadTime: vi.fn(),
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
    router = TestBed.inject(Router);
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

  it('lazily fetches the legacy catalog only when a remaining consumer reads it', async () => {
    setup();

    const response = [
      buildBook(1, {
        metadata: {
          authors: ['Le Guin', 'Le Guin'],
          categories: ['Fantasy'],
          moods: ['Calm'],
          tags: ['Classic', 'Classic'],
          publisher: 'Ace',
          seriesName: 'Earthsea',
        },
      }),
      buildBook(2, {
        metadata: {
          authors: ['Pratchett'],
          categories: ['Fantasy', 'Humor'],
          moods: ['Calm', 'Funny'],
          tags: ['Classic', 'Satire'],
          publisher: 'Corgi',
          seriesName: 'Discworld',
        },
      }),
    ];

    expect(service.books()).toEqual([]);
    // Requested but not yet fetching: a lazily-enabled query is not "loading" until it runs.
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBeNull();

    await flushBooksQuery();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    expect(request.request.method).toBe('GET');
    expect(service.isBooksLoading()).toBe(true);
    request.flush(response);
    await flushBooksQuery();

    expect(service.books()).toEqual(response);
    expect(service.findBookById(2)).toEqual(response[1]);
    expect(service.findBookById(999)).toBeUndefined();

    // getBooksByIds resolves cached ids from the legacy list in requested order and fetches only
    // the ids missing from the cache (999) from /books/batch.
    let byIds: Book[] = [];
    service.getBooksByIds([2, 999, 1]).subscribe(books => (byIds = books));
    const batchReq = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/batch'));
    expect(batchReq.request.params.getAll('ids')).toEqual(['999']);
    batchReq.flush([]);
    expect(byIds).toEqual([response[1], response[0]]);
    expect(service.uniqueMetadata()).toEqual({
      authors: ['Le Guin', 'Pratchett'],
      categories: ['Fantasy', 'Humor'],
      moods: ['Calm', 'Funny'],
      tags: ['Classic', 'Satire'],
      publishers: ['Ace', 'Corgi'],
      series: ['Earthsea', 'Discworld'],
    });
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBeNull();
  });

  it('does not report loading when the legacy catalog was never requested', () => {
    setup();

    // The dashboard reads isBooksLoading without ever consuming books(), so the
    // lazily-enabled legacy query stays idle. A disabled query must not look "loading".
    expect(service.isBooksLoading()).toBe(false);
    httpTestingController.expectNone(req => req.url.endsWith('/api/v1/books'));
  });

  it('disables retries for the legacy flat catalog query', () => {
    setup();

    const options = service.getBooksQueryOptions();

    expect(options.retry).toBe(false);
  });

  it('disables retries for queued recommendation requests', () => {
    setup();

    const options = service.bookRecommendationsQueryOptions(42, 20);

    expect(options.retry).toBe(false);
  });

  it('gates the requested legacy catalog on the auth token', async () => {
    setup(null);

    expect(service.books()).toEqual([]);
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBeNull();
    httpTestingController.expectNone(req => req.url.endsWith('/api/v1/books'));

    authService.token.set('token-123');
    await flushBooksQuery();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    expect(service.isBooksLoading()).toBe(true);
    request.flush([buildBook(7)]);
    await flushBooksQuery();

    expect(service.books()).toEqual([buildBook(7)]);
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBeNull();
  });

  it('surfaces query errors through booksError and clears the loading flag', async () => {
    setup();

    service.books();
    await flushBooksQuery();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    request.flush({message: 'boom'}, {status: 500, statusText: 'Server Error'});
    await flushBooksQuery();

    expect(service.books()).toEqual([]);
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBe('Failed to load books');
    // A server-side failure is not the size cap.
    expect(service.legacyCatalogTooLarge()).toBe(false);
  });

  it('flags the legacy catalog as too large when the server refuses it', async () => {
    setup();

    service.books();
    await flushBooksQuery();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    request.flush(
      {message: 'The flat books endpoint is disabled for catalogs over 10000 books'},
      {status: 400, statusText: 'Bad Request'}
    );
    await flushBooksQuery();

    // Screens must be able to say why they are blank instead of rendering an empty library.
    expect(service.legacyCatalogTooLarge()).toBe(true);
    expect(service.books()).toEqual([]);
  });

  it('navigates EPUB reads without streaming query params so streaming is the default reader path', () => {
    setup();
    queryClientHarness.queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(42, {primaryFile: {id: 1, bookId: 42, bookType: 'EPUB'}}),
    ]);

    service.readBook(42);

    expect(router.navigate).toHaveBeenCalledWith(['/ebook-reader/book/42'], undefined);
  });

  it('keeps an explicit blob fallback for EPUB compatibility mode', () => {
    setup();
    queryClientHarness.queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(43, {primaryFile: {id: 1, bookId: 43, bookType: 'EPUB'}}),
    ]);

    service.readBook(43, 'epub-blob');

    expect(router.navigate).toHaveBeenCalledWith(['/ebook-reader/book/43'], {queryParams: {streaming: false}});
  });

  it('reports a known unreadable document without navigating to the reader', () => {
    setup();
    queryClientHarness.queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(44, {
        primaryFile: {
          id: 1,
          bookId: 44,
          bookType: 'DOC',
          documentParseStatus: 'UNREADABLE',
        },
      }),
    ]);

    service.readBook(44);

    expect(router.navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(MessageService).add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'book.bookService.toast.unreadableDocumentSummary',
      detail: 'book.bookService.toast.unreadableDocumentDetail',
    });
  });

  it('invalidates book list caches when removing a shelf from books', async () => {
    setup();

    const targetShelf = buildShelf(10, {name: 'Favorites'});
    const untouchedShelf = buildShelf(11, {name: 'Archive'});
    const invalidateSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    const initialBooks = [
      buildBook(1, {shelves: [targetShelf, untouchedShelf]}),
      buildBook(2, {shelves: [targetShelf]}),
    ];

    service.books();
    await flushBooksQuery();
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books')).flush(initialBooks);
    await flushBooksQuery();

    service.removeBooksFromShelf(10);
    await flushBooksQuery();

    expect(queryClientHarness.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual(initialBooks);
    expect(invalidateSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
    expect(invalidateSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
  });

  it('does not fabricate the books query cache when removing a shelf before it is cached', () => {
    setup();

    service.removeBooksFromShelf(10);

    expect(queryClientHarness.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toBeUndefined();
  });

  it('removes the books query cache when the auth token is cleared', async () => {
    setup();

    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries');

    service.books();
    await flushBooksQuery();
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books')).flush([
      buildBook(1),
      buildBook(2),
    ]);
    await flushBooksQuery();

    expect(queryClientHarness.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      buildBook(1),
      buildBook(2),
    ]);

    authService.token.set(null);
    await flushBooksQuery();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY});
    expect(queryClientHarness.queryClient.getQueryData(BOOKS_QUERY_KEY)).toBeUndefined();
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBeNull();
  });
});
