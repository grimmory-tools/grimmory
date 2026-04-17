import {Injector} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookMarkService} from '../../../../../shared/service/book-mark.service';
import {ReaderAnnotationHttpService} from '../../features/annotations/annotation.service';
import {ReaderBookmarkService} from '../../features/bookmarks/bookmark.service';
import {ReaderSelectionService} from '../../features/selection/selection.service';
import {ReaderProgressService} from '../../state/progress.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {Book} from '../../../../book/model/book.model';
import {ReaderSidebarService} from './sidebar.service';

describe('ReaderSidebarService', () => {

  const selectionAnnotations$ = new Subject<{id: number; cfi: string}[]>();
  const selectionService = {
    annotationsChanged$: selectionAnnotations$.asObservable(),
    setAnnotations: vi.fn(),
  };

  const urlHelper = {
    getThumbnailUrl: vi.fn(() => '/cover.jpg'),
  };

  const viewManager = {
    getChapters: vi.fn(),
    goTo: vi.fn(() => of(void 0)),
    addAnnotations: vi.fn(),
    deleteAnnotation: vi.fn(() => of(void 0)),
  };

  const progressService: { currentChapterHref: string | null; currentCfi: string | null } = {
    currentChapterHref: '/ops/chapter-2.xhtml#frag',
    currentCfi: 'epubcfi(/6/4)',
  };

  const bookmarkService = {
    createBookmarkAtCurrentPosition: vi.fn(),
  };

  const bookmarkHttpService = {
    getBookmarksForBook: vi.fn(),
    deleteBookmark: vi.fn(() => of(void 0)),
  };

  const annotationService = {
    getAnnotations: vi.fn(),
    toViewAnnotations: vi.fn(),
    deleteAnnotation: vi.fn(() => of(true)),
  };

  const book = {
    id: 7,
    libraryId: 2,
    libraryName: 'Library',
    fileName: 'book.epub',
    metadata: {
      bookId: 7,
      title: 'Reader Book',
      authors: ['Alice', 'Bob'],
      coverUpdatedOn: '2026-03-26T00:00:00Z',
    },
  } as Book;

  const chapters = [
    {
      label: 'Part 1',
      href: '/ops/part-1.xhtml',
      subitems: [
        {label: 'Chapter 1', href: '/ops/chapter-1.xhtml', subitems: []},
        {label: 'Chapter 2', href: '/ops/chapter-2.xhtml#frag', subitems: []},
      ],
    },
  ];

  const bookmarks = [
    {id: 10, bookId: 7, cfi: 'epubcfi(/6/4)', title: 'Current', createdAt: '2026-03-26T00:00:00Z'},
  ];

  const annotations = [
    {id: 22, bookId: 7, cfi: 'epubcfi(/6/6)', text: 'Quote', color: '#ff0', style: 'highlight' as const, createdAt: '2026-03-26T00:00:00Z'},
  ];

  let service: ReaderSidebarService;

  beforeEach(() => {
    viewManager.getChapters.mockReset();
    viewManager.getChapters.mockReturnValue(chapters);
    viewManager.goTo.mockReset();
    viewManager.goTo.mockReturnValue(of(void 0));
    viewManager.addAnnotations.mockReset();
    viewManager.deleteAnnotation.mockReset();
    viewManager.deleteAnnotation.mockReturnValue(of(void 0));

    bookmarkService.createBookmarkAtCurrentPosition.mockReset();
    bookmarkService.createBookmarkAtCurrentPosition.mockReturnValue(of(true));
    bookmarkHttpService.getBookmarksForBook.mockReset();
    bookmarkHttpService.getBookmarksForBook.mockReturnValue(of(bookmarks));
    bookmarkHttpService.deleteBookmark.mockReset();
    bookmarkHttpService.deleteBookmark.mockReturnValue(of(void 0));

    annotationService.getAnnotations.mockReset();
    annotationService.getAnnotations.mockReturnValue(of(annotations));
    annotationService.toViewAnnotations.mockReset();
    annotationService.toViewAnnotations.mockReturnValue([{value: annotations[0].cfi, color: annotations[0].color, style: annotations[0].style}]);
    annotationService.deleteAnnotation.mockReset();
    annotationService.deleteAnnotation.mockReturnValue(of(true));

    selectionService.setAnnotations.mockReset();
    urlHelper.getThumbnailUrl.mockClear();

    TestBed.configureTestingModule({
      providers: [
        ReaderSidebarService,
        {provide: UrlHelperService, useValue: urlHelper},
        {provide: ReaderViewManagerService, useValue: viewManager},
        {provide: ReaderProgressService, useValue: progressService},
        {provide: ReaderBookmarkService, useValue: bookmarkService},
        {provide: BookMarkService, useValue: bookmarkHttpService},
        {provide: ReaderAnnotationHttpService, useValue: annotationService},
        {provide: ReaderSelectionService, useValue: selectionService},
        {
          provide: Injector,
          useFactory: () => ({
            get: (token: unknown) => token === ReaderSelectionService ? selectionService : undefined,
          }),
        },
      ]
    });

    service = TestBed.inject(ReaderSidebarService);
    service.initialize(7, book);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('initializes book info, chapters, bookmarks, and annotations', () => {
    expect(urlHelper.getThumbnailUrl).toHaveBeenCalledWith(7, '2026-03-26T00:00:00Z');
    expect(service.bookInfo()).toEqual({
      id: 7,
      title: 'Reader Book',
      authors: 'Alice, Bob',
      coverUrl: '/cover.jpg',
    });
    expect(service.chapters()).toEqual(chapters);
    expect(service.bookmarks()).toEqual(bookmarks);
    expect(service.annotations()).toEqual(annotations);
    expect(selectionService.setAnnotations).toHaveBeenCalledWith(annotations);
    expect(viewManager.addAnnotations).toHaveBeenCalledWith([{value: 'epubcfi(/6/6)', color: '#ff0', style: 'highlight'}]);
  });

  it('opens tabs, auto-expands the current chapter path, and detects active chapters', () => {
    service.open('bookmarks');

    expect(service.isOpen()).toBe(true);
    expect(service.activeTab()).toBe('bookmarks');
    expect(service.isChapterExpanded('/ops/part-1.xhtml')).toBe(true);
    expect(service.isChapterActive('/ops/chapter-2.xhtml')).toBe(true);
    expect(service.isChapterActive('/ops/chapter-9.xhtml')).toBe(false);

    service.toggleChapterExpand('/ops/chapter-1.xhtml');
    expect(service.isChapterExpanded('/ops/chapter-1.xhtml')).toBe(true);
    service.toggleChapterExpand('/ops/chapter-1.xhtml');
    expect(service.isChapterExpanded('/ops/chapter-1.xhtml')).toBe(false);
  });

  it('toggles sidebar visibility and can update the chapter list', () => {
    const nextChapters = [{label: 'New Chapter', href: '/ops/new.xhtml', subitems: []}];
    viewManager.getChapters.mockReturnValue(nextChapters);

    service.toggle();
    expect(service.isOpen()).toBe(true);

    service.toggle();
    expect(service.isOpen()).toBe(false);

    service.setActiveTab('highlights');
    service.updateChapters();
    expect(service.activeTab()).toBe('highlights');
    expect(service.chapters()).toEqual(nextChapters);
  });

  it('navigates to chapters, bookmarks, and annotations and closes afterwards', () => {
    service.open();

    service.navigateToChapter('/ops/chapter-1.xhtml');
    service.navigateToBookmark('epubcfi(/6/8)');
    service.navigateToAnnotation('epubcfi(/6/10)');

    expect(viewManager.goTo).toHaveBeenNthCalledWith(1, '/ops/chapter-1.xhtml');
    expect(viewManager.goTo).toHaveBeenNthCalledWith(2, 'epubcfi(/6/8)');
    expect(viewManager.goTo).toHaveBeenNthCalledWith(3, 'epubcfi(/6/10)');
    expect(service.isOpen()).toBe(false);
  });

  it('creates and deletes bookmarks through the current cfi branches', () => {
    service.createBookmark();
    expect(bookmarkService.createBookmarkAtCurrentPosition).toHaveBeenCalledWith(7);
    expect(bookmarkHttpService.getBookmarksForBook).toHaveBeenCalledTimes(2);

    service.toggleBookmark();
    expect(bookmarkHttpService.deleteBookmark).toHaveBeenCalledWith(10);

    progressService.currentCfi = 'epubcfi(/6/12)';
    service.toggleBookmark();
    expect(bookmarkService.createBookmarkAtCurrentPosition).toHaveBeenCalledTimes(2);

    progressService.currentCfi = null;
    service.toggleBookmark();
    expect(bookmarkService.createBookmarkAtCurrentPosition).toHaveBeenCalledTimes(2);
  });

  it('removes annotations only when delete succeeds and syncs external changes', () => {
    service.deleteAnnotation(22);

    expect(annotationService.deleteAnnotation).toHaveBeenCalledWith(22);
    expect(viewManager.deleteAnnotation).toHaveBeenCalledWith('epubcfi(/6/6)');
    expect(service.annotations()).toEqual([]);
    expect(selectionService.setAnnotations).toHaveBeenCalledWith([]);

    selectionAnnotations$.next([{id: 31, cfi: 'epubcfi(/6/14)'}]);
    expect(service.annotations()).toEqual([{id: 31, cfi: 'epubcfi(/6/14)'}]);

    annotationService.deleteAnnotation.mockReturnValue(of(false));
    service.setAnnotations(annotations);
    service.deleteAnnotation(22);
    expect(viewManager.deleteAnnotation).toHaveBeenCalledTimes(1);

    service.deleteAnnotation(999);
    expect(annotationService.deleteAnnotation).toHaveBeenCalledTimes(2);
  });

  it('emits metadata requests and resets back to defaults', () => {
    const metadataEvents: void[] = [];
    service.showMetadata$.subscribe(() => metadataEvents.push(undefined));

    service.open();
    service.openMetadata();

    expect(metadataEvents).toHaveLength(1);
    expect(service.isOpen()).toBe(false);

    service.reset();
    expect(service.activeTab()).toBe('chapters');
    expect(service.bookInfo()).toEqual({id: null, title: '', authors: '', coverUrl: null});
    expect(service.chapters()).toEqual([]);
    expect(service.bookmarks()).toEqual([]);
    expect(service.annotations()).toEqual([]);
  });
});
