import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MessageService } from '@openng/optimus-ui/api';
import { createQueryClientHarness } from '../../core/testing/query-testing';
import { getTranslocoModule } from '../../core/testing/transloco-testing';
import { BookDialogHelperService } from '../book/service/book-dialog-helper.service';
import { BookPage } from '../book/data/book-query.models';
import { BookQueryService } from '../book/data/book-query.service';
import { BookSummary } from '../book/data/book-response.models';
import { LibraryService } from '../book/service/library.service';
import { ShelfService } from '../book/service/shelf.service';
import { MagicShelfService } from '../magic-shelf/service/magic-shelf.service';
import { UrlHelperService } from '../../shared/service/url-helper.service';
import { UserService } from '../settings/user-management/user.service';
import { CustomSvgService } from '../../shared/services/custom-svg.service';
import { DialogLauncherService } from '../../shared/services/dialog-launcher.service';

import { CommandPaletteService } from './command-palette.service';

function makeBook(
  id: number,
  title: string,
  authors: string[] = [],
  overrides: Partial<BookSummary> = {},
): BookSummary {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    ...overrides,
    metadata: {
      bookId: id,
      authors,
      allMetadataLocked: false,
      title,
      ...overrides.metadata,
    },
  };
}

describe('CommandPaletteService', () => {
  let service: CommandPaletteService;
  let http: HttpTestingController;
  let urlHelper: {
    getThumbnailUrl: ReturnType<typeof vi.fn>;
    getAudiobookThumbnailUrl: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.useFakeTimers();
  });

  beforeEach(() => {
    const queryHarness = createQueryClientHarness();
    urlHelper = {
      getThumbnailUrl: vi.fn(() => null),
      getAudiobookThumbnailUrl: vi.fn(() => null),
    };

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        ...queryHarness.providers,
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        BookQueryService,
        { provide: ShelfService, useValue: { shelves: signal([]) } },
        { provide: MagicShelfService, useValue: { shelves: signal([]) } },
        { provide: LibraryService, useValue: { libraries: signal([]) } },
        { provide: UserService, useValue: { currentUser: signal({ permissions: {} }) } },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: UrlHelperService, useValue: urlHelper },
        { provide: CustomSvgService, useValue: { getSvgIconContent: vi.fn(() => of('')) } },
        {
          provide: DialogLauncherService,
          useValue: {
            openLibraryCreateDialog: vi.fn(() => Promise.resolve(null)),
            openMagicShelfCreateDialog: vi.fn(() => Promise.resolve(null)),
            openFileUploadDialog: vi.fn(() => Promise.resolve(null)),
          },
        },
        {
          provide: BookDialogHelperService,
          useValue: {
            openShelfCreatorDialog: vi.fn(() => Promise.resolve(null)),
          },
        },
      ],
    });

    service = TestBed.inject(CommandPaletteService);
    http = TestBed.inject(HttpTestingController);
    TestBed.flushEffects();
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function makeBookPage(books: BookSummary[]): BookPage {
    return {
      content: books,
      page: {
        number: 0,
        size: 50,
        totalElements: books.length,
        totalPages: books.length > 0 ? 1 : 0,
        cursor: 'opaque-cursor',
      },
      links: [],
    };
  }

  async function searchBooks(query: string, books: BookSummary[]): Promise<void> {
    service.open();
    service.query.set(query);
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    const request = http.expectOne(request => request.url.endsWith('/api/v1/books/page'));
    expect(request.request.params.get('facet_logic')).toBe('or');
    expect(request.request.params.get('query')).toBe(query);
    expect(request.request.params.get('sort')).toBe('title');
    expect(request.request.params.get('size')).toBe('50');
    request.flush(makeBookPage(books));
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.flushEffects();
  }

  it('queries the page endpoint with the normalized term after the debounce window', async () => {
    service.open();
    service.query.set('it!');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(199);
    TestBed.flushEffects();

    http.expectNone(request => request.url.endsWith('/api/v1/books/page'));

    await vi.advanceTimersByTimeAsync(1);
    TestBed.flushEffects();
    const request = http.expectOne(request => request.url.endsWith('/api/v1/books/page'));
    expect(request.request.params.get('facet_logic')).toBe('or');
    expect(request.request.params.get('query')).toBe('it');
    expect(request.request.params.get('sort')).toBe('title');
    expect(request.request.params.get('size')).toBe('50');
    request.flush(makeBookPage([makeBook(1, 'It', ['Stephen King'])]));
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.flushEffects();

    const bookGroup = service.groups().find((group) => group.kind === 'book');

    expect(bookGroup).toBeDefined();
    expect(bookGroup?.items.map((item) => item.title)).toEqual(['It']);
  });

  it('reports a failed book search instead of claiming no results', async () => {
    service.open();
    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    const request = http.expectOne(req => req.url.endsWith('/api/v1/books/page'));
    request.flush('Bad request', {status: 400, statusText: 'Bad Request'});
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.flushEffects();

    expect(service.bookSearchFailed()).toBe(true);
    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();
  });

  it('does not search when the normalized term is below two characters', async () => {
    service.open();
    service.query.set('d!');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    http.expectNone(request => request.url.endsWith('/api/v1/books/page'));
    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();
  });
  it('does not search for eligible text while the palette is closed', async () => {
    service.query.set('dune');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    http.expectNone(request => request.url.endsWith('/api/v1/books/page'));
    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();
  });

  it('cancels an in-flight book search when debounced text changes', async () => {
    service.open();
    service.query.set('dune');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();
    const duneRequest = http.expectOne(request => request.urlWithParams.includes('query=dune'));

    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    expect(duneRequest.cancelled).toBe(true);
    const tolkienRequest = http.expectOne(request => request.urlWithParams.includes('query=tolkien'));
    tolkienRequest.flush(makeBookPage([]));
    TestBed.flushEffects();
  });

  it('cancels an in-flight book search on close and does not refire it on quick reopen', async () => {
    service.open();
    service.query.set('dune');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();
    const duneRequest = http.expectOne(request => request.urlWithParams.includes('query=dune'));

    service.hide();
    TestBed.flushEffects();

    expect(duneRequest.cancelled).toBe(true);

    service.open();
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    http.expectNone(request => request.url.endsWith('/api/v1/books/page'));
  });

  it('hides results from the previous search while the next search is debouncing', async () => {
    await searchBooks('dune', [makeBook(3, 'Dune', ['Frank Herbert'])]);

    expect(service.groups().find((group) => group.kind === 'book')!.items[0].title).toBe('Dune');

    service.query.set('tolkien');
    TestBed.flushEffects();

    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();

    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();
    const request = http.expectOne(request => request.urlWithParams.includes('query=tolkien'));
    request.flush(makeBookPage([]));
    TestBed.flushEffects();
  });

  it('hides a failed search error while the next term is debouncing', async () => {
    service.open();
    service.query.set('dune');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();
    const duneRequest = http.expectOne(request => request.urlWithParams.includes('query=dune'));
    duneRequest.flush('Bad request', {status: 400, statusText: 'Bad Request'});
    await TestBed.inject(ApplicationRef).whenStable();
    TestBed.flushEffects();

    expect(service.bookSearchFailed()).toBe(true);

    service.query.set('tolkien');
    TestBed.flushEffects();

    expect(service.bookSearchFailed()).toBe(false);
  });
});
