import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getTranslocoModule } from '../../core/testing/transloco-testing';
import { BookDialogHelperService } from '../book/components/book-browser/book-dialog-helper.service';
import { Book } from '../book/model/book.model';
import { BookService } from '../book/service/book.service';
import { LibraryService } from '../book/service/library.service';
import { ShelfService } from '../book/service/shelf.service';
import { MagicShelfService } from '../magic-shelf/service/magic-shelf.service';
import { UrlHelperService } from '../../shared/service/url-helper.service';
import { UserService } from '../settings/user-management/user.service';
import { IconService } from '../../shared/services/icon.service';
import { DialogLauncherService } from '../../shared/services/dialog-launcher.service';

import { CommandPaletteService } from './command-palette.service';

function makeBook(id: number, title: string, authors: string[] = [], overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    ...overrides,
    metadata: {
      bookId: id,
      title,
      authors,
      ...overrides.metadata,
    },
  } as Book;
}

describe('CommandPaletteService', () => {
  let service: CommandPaletteService;
  let books = signal<Book[]>([]);
  let urlHelper: {
    getThumbnailUrl: ReturnType<typeof vi.fn>;
    getAudiobookThumbnailUrl: ReturnType<typeof vi.fn>;
  };

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

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        { provide: BookService, useValue: { books: books.asReadonly() } },
        { provide: ShelfService, useValue: { shelves: signal([]) } },
        { provide: MagicShelfService, useValue: { shelves: signal([]) } },
        { provide: LibraryService, useValue: { libraries: signal([]) } },
        { provide: UserService, useValue: { currentUser: signal({ permissions: {} }) } },
        { provide: UrlHelperService, useValue: urlHelper },
        { provide: IconService, useValue: { getSvgIconContent: vi.fn(() => of('')) } },
        {
          provide: DialogLauncherService,
          useValue: {
            openLibraryCreateDialog: vi.fn(),
            openMagicShelfCreateDialog: vi.fn(),
            openFileUploadDialog: vi.fn(),
          },
        },
        {
          provide: BookDialogHelperService,
          useValue: {
            openShelfCreatorDialog: vi.fn(),
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

  it('queries matching book groups locally after the debounce window', async () => {
    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    const bookGroup = service.groups().find((group) => group.kind === 'book');

    expect(bookGroup).toBeDefined();
    expect(bookGroup?.items.map((item) => item.title)).toEqual([
      'The Hobbit',
      'The Fellowship of the Ring',
    ]);
  });

  it('does not show book groups for one-character searches', async () => {
    service.query.set('d');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();
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
        primaryFile: { id: 4, bookId: 4, bookType: 'AUDIOBOOK' },
        metadata: {
          bookId: 4,
          title: 'Audio Sample',
          authors: ['Narrator'],
          audiobookCoverUpdatedOn: 'audio-updated',
        },
      }),
    ]);

    service.query.set('audio');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    const book = service.groups().find((group) => group.kind === 'book')?.items[0];

    expect(book?.bookMeta?.isAudiobook).toBe(true);
    expect(book?.bookMeta?.thumbnailUrl).toBe('/audio-thumb.jpg');
    expect(urlHelper.getAudiobookThumbnailUrl).toHaveBeenCalledWith(4, 'audio-updated');
    expect(urlHelper.getThumbnailUrl).not.toHaveBeenCalled();
  });
});
