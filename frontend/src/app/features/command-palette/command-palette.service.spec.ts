import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MessageService } from 'primeng/api';
import { getTranslocoModule } from '../../core/testing/transloco-testing';
import { BookDialogHelperService } from '../book/components/book-browser/book-dialog-helper.service';
import type { AppBookQuickSearchResult } from '../book/model/app-book.model';
import { AppBooksApiService } from '../book/service/app-books-api.service';
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
  overrides: Partial<AppBookQuickSearchResult> = {},
): AppBookQuickSearchResult {
  return {
    id,
    title,
    authors,
    seriesName: null,
    seriesNumber: null,
    publishedDate: null,
    primaryFileType: null,
    primaryFileDocumentParseStatus: null,
    primaryFileName: null,
    coverUpdatedOn: null,
    audiobookCoverUpdatedOn: null,
    ...overrides,
  };
}

describe('CommandPaletteService', () => {
  let service: CommandPaletteService;
  let books = signal<AppBookQuickSearchResult[]>([]);
  let urlHelper: {
    getThumbnailUrl: ReturnType<typeof vi.fn>;
    getAudiobookThumbnailUrl: ReturnType<typeof vi.fn>;
  };
  let quickSearchBooks: ReturnType<typeof vi.fn>;
  let semanticSearchBooks: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
  });

  beforeEach(() => {
    books = signal([
      makeBook(1, 'The Hobbit', ['J.R.R. Tolkien']),
      makeBook(2, 'The Fellowship of the Ring', ['J.R.R. Tolkien']),
      makeBook(3, 'Dune', ['Frank Herbert']),
    ]);
    urlHelper = {
      getThumbnailUrl: vi.fn(() => null),
      getAudiobookThumbnailUrl: vi.fn(() => null),
    };
    quickSearchBooks = vi.fn((query: string) => {
      const normalized = query.toLowerCase();
      return of(books()
        .filter(book => `${book.title} ${book.authors.join(' ')}`.toLowerCase().includes(normalized)));
    });
    semanticSearchBooks = vi.fn(() => of([] as AppBookQuickSearchResult[]));

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        { provide: AppBooksApiService, useValue: { quickSearchBooks, semanticSearchBooks } },
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
    TestBed.flushEffects();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('queries matching book groups through the specialized search API after the debounce window', async () => {
    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    const bookGroup = service.groups().find((group) => group.kind === 'book');

    expect(bookGroup).toBeDefined();
    expect(bookGroup?.items.map((item) => item.title)).toEqual([
      'The Hobbit',
      'The Fellowship of the Ring',
    ]);
    expect(quickSearchBooks).toHaveBeenCalledWith('tolkien', 50);
  });

  it('does not show book groups for searches shorter than three characters', async () => {
    service.query.set('du');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();
    expect(quickSearchBooks).not.toHaveBeenCalled();
  });

  it('keeps the searching state active until the book request completes', async () => {
    const response = new Subject<AppBookQuickSearchResult[]>();
    quickSearchBooks.mockReturnValue(response.asObservable());

    service.query.set('dune');
    TestBed.flushEffects();

    expect(service.isSearching()).toBe(true);

    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    expect(quickSearchBooks).toHaveBeenCalledWith('dune', 50);
    expect(service.isSearching()).toBe(true);

    response.next([]);
    response.complete();
    TestBed.flushEffects();

    expect(service.isSearching()).toBe(false);
  });

  it('shows semantic matches in their own group directly after the lexical book group', async () => {
    semanticSearchBooks.mockReturnValue(of([makeBook(3, 'Dune', ['Frank Herbert'])]));

    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    expect(semanticSearchBooks).toHaveBeenCalledWith('tolkien', 50);
    expect(service.groups().map((group) => group.kind).slice(0, 2)).toEqual(['book', 'semanticBook']);
    expect(service.groups().find((group) => group.kind === 'semanticBook')?.items.map((item) => item.title))
      .toEqual(['Dune']);
  });

  it('drops semantic matches that the lexical group already shows', async () => {
    semanticSearchBooks.mockReturnValue(of([
      makeBook(1, 'The Hobbit', ['J.R.R. Tolkien']),
      makeBook(3, 'Dune', ['Frank Herbert']),
    ]));

    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    expect(service.groups().find((group) => group.kind === 'semanticBook')?.items.map((item) => item.title))
      .toEqual(['Dune']);
  });

  it('does not hold the spinner while only the semantic request is still in flight', async () => {
    const semanticResponse = new Subject<AppBookQuickSearchResult[]>();
    semanticSearchBooks.mockReturnValue(semanticResponse.asObservable());

    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    expect(service.isSearching()).toBe(false);
    expect(service.groups().find((group) => group.kind === 'book')).toBeDefined();

    semanticResponse.next([]);
    semanticResponse.complete();
  });

  it('does not run a semantic search for queries shorter than three characters', async () => {
    service.query.set('du');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    expect(semanticSearchBooks).not.toHaveBeenCalled();
  });

  it('returns no groups when the query is empty', () => {
    service.query.set('');

    expect(service.groups()).toEqual([]);
    expect(service.visibleItems()).toEqual([]);
  });

  it('uses square audiobook metadata and audiobook thumbnails for audiobook results', async () => {
    urlHelper.getAudiobookThumbnailUrl.mockReturnValue('/audio-thumb.jpg');
    books.set([
      makeBook(4, 'Audio Sample', ['Narrator'], {
        primaryFileType: 'AUDIOBOOK',
        audiobookCoverUpdatedOn: 'audio-updated',
      }),
    ]);

    service.query.set('audio');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(350);
    TestBed.flushEffects();

    const book = service.groups().find((group) => group.kind === 'book')?.items[0];

    expect(book?.bookMeta?.isAudiobook).toBe(true);
    expect(book?.bookMeta?.thumbnailUrl).toBe('/audio-thumb.jpg');
    expect(urlHelper.getAudiobookThumbnailUrl).toHaveBeenCalledWith(4, 'audio-updated');
    expect(urlHelper.getThumbnailUrl).not.toHaveBeenCalled();
  });
});
