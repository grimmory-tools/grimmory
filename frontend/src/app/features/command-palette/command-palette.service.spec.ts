import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getTranslocoModule } from '../../core/testing/transloco-testing';
import { BookDialogHelperService } from '../book/components/book-browser/book-dialog-helper.service';
import { AppBookSummary, AppPageResponse } from '../book/model/app-book.model';
import { AppBooksApiService } from '../book/service/app-books-api.service';
import { LibraryService } from '../book/service/library.service';
import { ShelfService } from '../book/service/shelf.service';
import { MagicShelfService } from '../magic-shelf/service/magic-shelf.service';
import { UrlHelperService } from '../../shared/service/url-helper.service';
import { UserService } from '../settings/user-management/user.service';
import { IconService } from '../../shared/services/icon.service';
import { DialogLauncherService } from '../../shared/services/dialog-launcher.service';

import { CommandPaletteService } from './command-palette.service';

function makeBookSummary(id: number, title: string, authors: string[] = []): AppBookSummary {
  return {
    id,
    title,
    authors,
    thumbnailUrl: null,
    readStatus: null,
    personalRating: null,
    seriesName: null,
    seriesNumber: null,
    libraryId: 1,
    addedOn: null,
    lastReadTime: null,
    readProgress: null,
    primaryFileId: null,
    primaryFileType: 'EPUB',
    primaryFileName: null,
    coverUpdatedOn: null,
    audiobookCoverUpdatedOn: null,
    isPhysical: false,
    publisher: null,
    categories: null,
    tags: null,
    moods: null,
    language: null,
    narrator: null,
    isbn13: null,
    isbn10: null,
    publishedDate: null,
    pageCount: null,
    ageRating: null,
    contentRating: null,
    metadataMatchScore: null,
    fileSizeKb: null,
    amazonRating: null,
    amazonReviewCount: null,
    goodreadsRating: null,
    goodreadsReviewCount: null,
    hardcoverRating: null,
    hardcoverReviewCount: null,
    ranobedbRating: null,
    lubimyczytacRating: null,
    audibleRating: null,
    audibleReviewCount: null,
    allMetadataLocked: null,
  };
}

function makePageResponse<T>(content: T[]): AppPageResponse<T> {
  return {
    content,
    page: 0,
    size: content.length,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

describe('CommandPaletteService', () => {
  let service: CommandPaletteService;
  let searchBooks: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
  });

  beforeEach(() => {
    searchBooks = vi.fn((query: string) =>
      query === 'tolkien'
        ? of(makePageResponse([
            makeBookSummary(1, 'The Hobbit', ['J.R.R. Tolkien']),
            makeBookSummary(2, 'The Fellowship of the Ring', ['J.R.R. Tolkien']),
          ]))
        : of(makePageResponse([]))
    );

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        { provide: AppBooksApiService, useValue: { searchBooks } },
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

  it('queries matching remote groups from the backend after the debounce window', async () => {
    service.query.set('tolkien');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    const bookGroup = service.groups().find((group) => group.kind === 'book');

    expect(searchBooks).toHaveBeenCalledWith('tolkien', 25);
    expect(bookGroup).toBeDefined();
    expect(bookGroup?.items.map((item) => item.title)).toEqual([
      'The Hobbit',
      'The Fellowship of the Ring',
    ]);
  });

  it('does not query remote groups for one-character searches', async () => {
    service.query.set('d');
    TestBed.flushEffects();
    await vi.advanceTimersByTimeAsync(200);
    TestBed.flushEffects();

    expect(searchBooks).not.toHaveBeenCalled();
    expect(service.groups().find((group) => group.kind === 'book')).toBeUndefined();
  });

  it('returns no groups when the query is empty', () => {
    service.query.set('');

    expect(service.groups()).toEqual([]);
    expect(service.visibleItems()).toEqual([]);
  });
});
