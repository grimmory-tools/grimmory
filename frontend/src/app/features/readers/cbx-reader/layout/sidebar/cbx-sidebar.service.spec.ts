import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {of, Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {CbxPageInfo, CbxReaderService} from '../../../../book/service/cbx-reader.service';
import {Book} from '../../../../book/model/book.model';
import {BookMark, BookMarkService} from '../../../../../shared/service/book-mark.service';
import {BookNoteV2, BookNoteV2Service} from '../../../../../shared/service/book-note-v2.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {CbxSidebarService} from './cbx-sidebar.service';

describe('CbxSidebarService', () => {
  let service: CbxSidebarService;
  let destroy$: Subject<void>;

  const pageInfo: CbxPageInfo[] = [
    {pageNumber: 1, displayName: 'Page 1'},
    {pageNumber: 2, displayName: 'Page 2'},
  ];

  const bookmarks: BookMark[] = [
    {id: 10, bookId: 7, cfi: '2', title: 'Page 2', createdAt: '2026-03-26T00:00:00Z'},
  ];

  const notes: BookNoteV2[] = [
    {id: 20, bookId: 7, cfi: '3', noteContent: 'Highlight', chapterTitle: 'Battle', createdAt: '2026-03-26T00:00:00Z'},
    {id: 21, bookId: 7, cfi: '4', noteContent: 'Quiet scene', chapterTitle: 'Interlude', createdAt: '2026-03-26T00:00:00Z'},
  ];

  const cbxReaderService = {
    getPageInfo: vi.fn(),
  };

  const bookMarkService = {
    getBookmarksForBook: vi.fn(),
    createBookmark: vi.fn(),
    deleteBookmark: vi.fn(),
  };

  const bookNoteV2Service = {
    getNotesForBook: vi.fn(),
    createNote: vi.fn(),
    updateNote: vi.fn(),
    deleteNote: vi.fn(),
  };

  const urlHelper = {
    getThumbnailUrl: vi.fn(),
  };

  const translocoService = {
    translate: vi.fn((key: string) => {
      if (key === 'readerCbx.sidebar.untitled') {
        return 'Untitled';
      }
      if (key === 'readerCbx.sidebar.page') {
        return 'Page';
      }
      return key;
    }),
  };

  const createBook = (overrides: Partial<Book> = {}): Book => ({
    id: 7,
    libraryId: 11,
    libraryName: 'Library',
    fileName: 'issue.cbz',
    metadata: {
      bookId: 7,
      title: 'Issue 7',
      authors: ['Alice', 'Bob'],
      coverUpdatedOn: '2026-03-26T00:00:00Z',
    },
    ...overrides,
  } as Book);

  beforeEach(() => {
    destroy$ = new Subject<void>();
    cbxReaderService.getPageInfo.mockReset();
    cbxReaderService.getPageInfo.mockReturnValue(of(pageInfo));
    bookMarkService.getBookmarksForBook.mockReset();
    bookMarkService.getBookmarksForBook.mockReturnValue(of(bookmarks));
    bookMarkService.createBookmark.mockReset();
    bookMarkService.createBookmark.mockReturnValue(of(bookmarks[0]));
    bookMarkService.deleteBookmark.mockReset();
    bookMarkService.deleteBookmark.mockReturnValue(of(void 0));
    bookNoteV2Service.getNotesForBook.mockReset();
    bookNoteV2Service.getNotesForBook.mockReturnValue(of(notes));
    bookNoteV2Service.createNote.mockReset();
    bookNoteV2Service.createNote.mockReturnValue(of(notes[0]));
    bookNoteV2Service.updateNote.mockReset();
    bookNoteV2Service.updateNote.mockReturnValue(of(notes[0]));
    bookNoteV2Service.deleteNote.mockReset();
    bookNoteV2Service.deleteNote.mockReturnValue(of(void 0));
    urlHelper.getThumbnailUrl.mockReset();
    urlHelper.getThumbnailUrl.mockReturnValue('/thumb.jpg');
    translocoService.translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        CbxSidebarService,
        {provide: UrlHelperService, useValue: urlHelper},
        {provide: CbxReaderService, useValue: cbxReaderService},
        {provide: BookMarkService, useValue: bookMarkService},
        {provide: BookNoteV2Service, useValue: bookNoteV2Service},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    service = TestBed.inject(CbxSidebarService);
  });

  afterEach(() => {
    destroy$.complete();
    TestBed.resetTestingModule();
  });

  it('initializes book info and loads pages, bookmarks, and notes', () => {
    service.initialize(7, createBook(), destroy$, 'CBX');

    expect(cbxReaderService.getPageInfo).toHaveBeenCalledWith(7, 'CBX');
    expect(bookMarkService.getBookmarksForBook).toHaveBeenCalledWith(7);
    expect(bookNoteV2Service.getNotesForBook).toHaveBeenCalledWith(7);
    expect(urlHelper.getThumbnailUrl).toHaveBeenCalledWith(7, '2026-03-26T00:00:00Z');
    expect(service.bookInfo()).toEqual({
      id: 7,
      title: 'Issue 7',
      authors: 'Alice, Bob',
      coverUrl: '/thumb.jpg'
    });
    expect(service.pages()).toEqual(pageInfo);
    expect(service.bookmarks()).toEqual(bookmarks);
    expect(service.notes()).toEqual(notes);
  });

  it('falls back to translated untitled book info when metadata is missing', () => {
    service.initialize(7, createBook({metadata: undefined, fileName: undefined}), destroy$);

    expect(service.bookInfo()).toEqual({
      id: 7,
      title: 'Untitled',
      authors: '',
      coverUrl: '/thumb.jpg'
    });
  });

  it('tracks tabs, open state, current page, and page navigation emissions', () => {
    const pageEvents: number[] = [];
    service.navigateToPage$.subscribe(value => pageEvents.push(value));

    service.setCurrentPage(5);
    service.setActiveTab('notes');
    service.open('bookmarks');
    service.navigateToPage(8);

    expect(service.currentPage()).toBe(5);
    expect(service.activeTab()).toBe('bookmarks');
    expect(service.isOpen()).toBe(false);
    expect(pageEvents).toEqual([8]);
  });

  it('supports bookmark lookups, creation, deletion, and toggle branches', () => {
    service.initialize(7, createBook(), destroy$);

    expect(service.isPageBookmarked(2)).toBe(true);
    expect(service.isPageBookmarked(9)).toBe(false);
    expect(service.getBookmarkForPage(2)?.id).toBe(10);

    service.toggleBookmark(2);
    expect(bookMarkService.deleteBookmark).toHaveBeenCalledWith(10);

    service.toggleBookmark(5);
    expect(bookMarkService.createBookmark).toHaveBeenCalledWith({
      bookId: 7,
      cfi: '5',
      title: 'Page 5'
    });
    expect(bookMarkService.getBookmarksForBook).toHaveBeenCalledTimes(3);
  });

  it('navigates to bookmarks only when the cfi parses to a page number', () => {
    const pageEvents: number[] = [];
    service.navigateToPage$.subscribe(value => pageEvents.push(value));
    service.open();

    service.navigateToBookmark('4');
    service.navigateToBookmark('not-a-page');

    expect(pageEvents).toEqual([4]);
    expect(service.isOpen()).toBe(false);
  });

  it('filters notes by content or chapter title and clears the query', () => {
    service.initialize(7, createBook(), destroy$);

    service.setNotesSearchQuery('battle');
    expect(service.filteredNotes()).toEqual([notes[0]]);

    service.setNotesSearchQuery('quiet');
    expect(service.filteredNotes()).toEqual([notes[1]]);

    service.setNotesSearchQuery('');
    expect(service.filteredNotes()).toEqual(notes);
  });

  it('creates, updates, edits, deletes, and navigates notes', () => {
    const pageEvents: number[] = [];
    const editEvents: BookNoteV2[] = [];
    service.navigateToPage$.subscribe(value => pageEvents.push(value));
    service.editNote$.subscribe(value => editEvents.push(value));
    service.initialize(7, createBook(), destroy$);

    service.createNote(6, 'Remember this', '#fff000');
    expect(bookNoteV2Service.createNote).toHaveBeenCalledWith({
      bookId: 7,
      cfi: '6',
      noteContent: 'Remember this',
      color: '#fff000',
      chapterTitle: 'Page 6'
    });

    service.updateNote(20, 'Updated', '#000000');
    expect(bookNoteV2Service.updateNote).toHaveBeenCalledWith(20, {
      noteContent: 'Updated',
      color: '#000000'
    });

    service.open();
    service.editNote(notes[0]);
    expect(editEvents).toEqual([notes[0]]);
    expect(service.isOpen()).toBe(false);

    service.navigateToNote('3');
    service.navigateToNote('invalid');
    expect(pageEvents).toEqual([3]);

    service.deleteNote(20);
    expect(bookNoteV2Service.deleteNote).toHaveBeenCalledWith(20);
    expect(service.notes()).toEqual([notes[1]]);
    expect(service.pageHasNotes(4)).toBe(true);
    expect(service.pageHasNotes(20)).toBe(false);
  });
});
