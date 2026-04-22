import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {BookService} from './book.service';
import {AuthService} from '../../../shared/service/auth.service';
import {MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {Book, ReadStatus} from '../model/book.model';
import {TranslocoService, provideTransloco} from '@jsverse/transloco';
import {
  createQueryClientHarness,
  flushQueryAsync,
  flushSignalAndQueryEffects,
} from '../../../core/testing/query-testing';
import {BookSocketService} from './book-socket.service';
import {BookPatchService} from './book-patch.service';
import {vi, describe, it, expect, beforeEach, afterEach} from 'vitest';
import {provideHttpClient} from '@angular/common/http';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {signal, WritableSignal, computed} from '@angular/core';

interface BuildBookOverrides {
  metadata?: Partial<Book['metadata']>;
  shelves?: unknown[];
  readStatus?: ReadStatus;
}

function createAuthServiceStub(initialToken: string | null = 'token-123') {
  const token: WritableSignal<string | null> = signal(initialToken);
  return {
    token: token,
    isAuthenticated: computed(() => !!token()),
  } as unknown as AuthService;
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
  } as Book;
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
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: {
            availableLangs: ['en'],
            defaultLang: 'en',
          },
        }),
        BookService,
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

  it('eagerly fetches books and hydrates query-backed state, loading state, and unique metadata', async () => {
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

    // Since it's eager and token was present in setup(), request is already out.
    expect(service.isBooksLoading()).toBe(true);

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    expect(request.request.method).toBe('GET');
    request.flush(response);
    await flushBooksQuery();

    expect(service.books()).toEqual(response);
    expect(service.findBookById(2)).toEqual(response[1]);
    expect(service.findBookById(999)).toBeUndefined();
    expect(service.getBooksByIds([2, 999, 1])).toEqual(response);
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

  it('gates loading on the auth token and starts the eager fetch once a token is available', async () => {
    setup(null); // No token initially

    expect(service.books()).toEqual([]);
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBeNull();
    httpTestingController.expectNone(req => req.url.endsWith('/api/v1/books'));

    authService.token.set('token-123');
    flushSignalAndQueryEffects();

    expect(service.isBooksLoading()).toBe(true);
    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    request.flush([]);
    await flushBooksQuery();
    expect(service.isBooksLoading()).toBe(false);
  });

  it('surfaces query errors through booksError and clears the loading flag', async () => {
    setup();
    
    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books'));
    request.flush({message: 'boom'}, {status: 500, statusText: 'Server Error'});
    await flushBooksQuery();

    expect(service.books()).toEqual([]);
    expect(service.isBooksLoading()).toBe(false);
    expect(service.booksError()).toBe('Failed to load books');
  });

  it('removes a shelf from the cached books query without disturbing other shelf assignments', async () => {
    setup();

    const targetShelf = buildShelf(10, {name: 'Favorites'});
    const untouchedShelf = buildShelf(11, {name: 'Archive'});
    const initialBooks = [
      buildBook(1, {shelves: [targetShelf, untouchedShelf]}),
      buildBook(2, {shelves: [targetShelf]}),
    ];

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books')).flush(initialBooks);
    await flushBooksQuery();

    service.removeBooksFromShelf(10);
    await flushBooksQuery();

    expect(queryClientHarness.queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      expect.objectContaining({id: 1, shelves: [untouchedShelf]}),
      expect.objectContaining({id: 2, shelves: []}),
    ]);
  });

  it('removes the books query cache when the auth token is cleared', async () => {
    setup();

    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries');

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books')).flush([
      buildBook(1),
      buildBook(2),
    ]);
    await flushBooksQuery();

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY});
  });
});

function buildShelf(id: number, overrides: Record<string, unknown> = {}) {
  return {id, ...overrides};
}
