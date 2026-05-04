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

function makeBook(id: number, title: string, authors: string[] = []): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {
      bookId: id,
      title,
      authors,
    },
  } as Book;
}

describe('CommandPaletteService', () => {
  let service: CommandPaletteService;
  let books = signal<Book[]>([]);

  beforeEach(() => {
    vi.useFakeTimers();
  });

  beforeEach(() => {
    books = signal([
      makeBook(1, 'The Hobbit', ['J.R.R. Tolkien']),
      makeBook(2, 'The Fellowship of the Ring', ['J.R.R. Tolkien']),
      makeBook(3, 'Dune', ['Frank Herbert']),
    ]);

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        { provide: BookService, useValue: { books: books.asReadonly() } },
        { provide: ShelfService, useValue: { shelves: signal([]) } },
        { provide: MagicShelfService, useValue: { shelves: signal([]) } },
        { provide: LibraryService, useValue: { libraries: signal([]) } },
        { provide: UserService, useValue: { currentUser: signal({ permissions: {} }) } },
        { provide: UrlHelperService, useValue: { getThumbnailUrl: vi.fn(() => null) } },
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
});
