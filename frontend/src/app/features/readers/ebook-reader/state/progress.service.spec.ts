import {TestBed} from '@angular/core/testing';
import {describe, expect, it, beforeEach, afterEach, vi} from 'vitest';

import {ReaderProgressService} from './progress.service';
import {ReaderStateService} from './reader-state.service';
import {ReaderViewManagerService, type FoliateRenderer} from '../core/view-manager.service';
import {ReaderAnnotationHttpService} from '../features/annotations/annotation.service';
import {ReaderBookmarkService} from '../features/bookmarks/bookmark.service';
import {BookPatchService} from '../../../book/service/book-patch.service';
import {ReadingSessionService} from '../../../../shared/service/reading-session.service';
import {TranslocoService} from '@jsverse/transloco';

describe('ReaderProgressService', () => {
  const renderer: FoliateRenderer = {
    heads: [],
    feet: [],
    getContents: vi.fn(() => []),
    setAttribute: vi.fn(),
    removeAttribute: vi.fn(),
  };

  const bookPatchService = {
    saveEpubProgress: vi.fn(),
  };

  const readingSessionService = {
    startSession: vi.fn(),
    updateProgress: vi.fn(),
    endSession: vi.fn(),
    isSessionActive: vi.fn(() => true),
  };

  const viewManagerService = {
    getRenderer: vi.fn<() => FoliateRenderer | null>(),
    updateHeadersAndFooters: vi.fn(),
  };

  const stateService = {
    state: vi.fn(() => ({
      flow: 'paginated',
      theme: {
        fg: '#111111',
        bg: '#222222',
        light: {fg: '#333333', bg: '#444444'},
      }
    })),
  };

  const annotationService = {
    updateCurrentChapter: vi.fn(),
  };

  const bookmarkService = {
    updateCurrentPosition: vi.fn(),
  };

  const translocoService = {
    translate: vi.fn(() => 'Time remaining in section: 1m 30s'),
  };

  let service: ReaderProgressService;

  beforeEach(() => {
    bookPatchService.saveEpubProgress.mockReset();
    readingSessionService.startSession.mockReset();
    readingSessionService.updateProgress.mockReset();
    readingSessionService.endSession.mockReset();
    readingSessionService.isSessionActive.mockReset();
    readingSessionService.isSessionActive.mockReturnValue(true);
    viewManagerService.getRenderer.mockReset();
    viewManagerService.getRenderer.mockReturnValue(renderer);
    viewManagerService.updateHeadersAndFooters.mockReset();
    stateService.state.mockReset();
    annotationService.updateCurrentChapter.mockReset();
    bookmarkService.updateCurrentPosition.mockReset();
    translocoService.translate.mockReset();
    translocoService.translate.mockReturnValue('Time remaining in section: 1m 30s');

    TestBed.configureTestingModule({
      providers: [
        ReaderProgressService,
        {provide: BookPatchService, useValue: bookPatchService},
        {provide: ReadingSessionService, useValue: readingSessionService},
        {provide: ReaderViewManagerService, useValue: viewManagerService},
        {provide: ReaderStateService, useValue: stateService},
        {provide: ReaderAnnotationHttpService, useValue: annotationService},
        {provide: ReaderBookmarkService, useValue: bookmarkService},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    service = TestBed.inject(ReaderProgressService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('records relocate progress and updates reader services', () => {
    const emissions: unknown[] = [];
    service.progress$.subscribe(value => emissions.push(value));

    service.initialize(17, 'EPUB', 23);
    service.handleRelocateEvent({
      cfi: 'epubcfi(/6/2)',
      fraction: 0.254,
      pageItem: {href: 'chapter.xhtml'},
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
      section: {current: 1, total: 10},
      time: {section: 90},
    });

    expect(readingSessionService.startSession).toHaveBeenCalledWith(17, 'EPUB', 'epubcfi(/6/2)', 25.4);
    expect(bookPatchService.saveEpubProgress).toHaveBeenCalledWith(17, 'epubcfi(/6/2)', 'chapter.xhtml', 25.4, 23);
    expect(readingSessionService.updateProgress).toHaveBeenCalledWith('epubcfi(/6/2)', 25.4);
    expect(annotationService.updateCurrentChapter).toHaveBeenCalledWith('Chapter 1');
    expect(bookmarkService.updateCurrentPosition).toHaveBeenCalledWith('epubcfi(/6/2)', 'Chapter 1');
    expect(viewManagerService.updateHeadersAndFooters).toHaveBeenCalledWith(
      'Chapter 1',
      {percentCompleted: 25.4, sectionTimeText: '1h 30m'},
      {fg: '#111111', bg: '#222222'},
      'Time remaining in section: 1m 30s'
    );
    expect(emissions).toHaveLength(1);
    expect(emissions[0]).toMatchObject({
      cfi: 'epubcfi(/6/2)',
      href: 'chapter.xhtml',
      chapterName: 'Chapter 1',
      chapterHref: 'chapter.xhtml',
      fraction: 0.254,
    });
  });

  it('ends an active session with rounded progress', () => {
    service.initialize(17, 'EPUB');
    service.handleRelocateEvent({
      cfi: 'epubcfi(/6/2)',
      fraction: 0.257,
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
    });

    service.endSession();

    expect(readingSessionService.endSession).toHaveBeenCalledWith('epubcfi(/6/2)', 25.7);
  });

  it('does not end a session when the reading session is inactive', () => {
    readingSessionService.isSessionActive.mockReturnValue(false);

    service.endSession();

    expect(readingSessionService.endSession).not.toHaveBeenCalled();
  });

  it('skips header and footer updates when the reader is not in paginated flow', () => {
    stateService.state.mockReturnValue({
      flow: 'scrolled',
      theme: {
        fg: '#111111',
        bg: '#222222',
        light: {fg: '#333333', bg: '#444444'},
      }
    });

    service.initialize(17, 'EPUB');
    service.handleRelocateEvent({
      cfi: 'epubcfi(/6/2)',
      fraction: 0.2,
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
      section: {current: 1, total: 10},
      time: {section: 0.5},
    });

    expect(viewManagerService.updateHeadersAndFooters).not.toHaveBeenCalled();
  });

  it('does not persist progress when the relocate payload has no cfi', () => {
    service.initialize(17, 'EPUB', 23);

    service.handleRelocateEvent({
      fraction: 0.2,
      pageItem: {href: 'chapter.xhtml'},
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
    });

    expect(readingSessionService.startSession).not.toHaveBeenCalled();
    expect(bookPatchService.saveEpubProgress).not.toHaveBeenCalled();
    expect(readingSessionService.updateProgress).not.toHaveBeenCalled();
    expect(bookmarkService.updateCurrentPosition).not.toHaveBeenCalled();
  });

  it('falls back to light theme colors when the active theme colors are blank', () => {
    stateService.state.mockReturnValue({
      flow: 'paginated',
      theme: {
        fg: '',
        bg: '',
        light: {fg: '#aaaaaa', bg: '#bbbbbb'},
      }
    });

    service.initialize(17, 'EPUB');
    service.handleRelocateEvent({
      cfi: 'epubcfi(/6/2)',
      fraction: 0.33,
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
      section: {current: 1, total: 10},
      time: {section: 0},
    });

    expect(viewManagerService.updateHeadersAndFooters).toHaveBeenCalledWith(
      'Chapter 1',
      {percentCompleted: 33, sectionTimeText: '0s'},
      {fg: '#aaaaaa', bg: '#bbbbbb'},
      'Time remaining in section: 1m 30s'
    );
  });

  it('returns early when there is no renderer to update', () => {
    viewManagerService.getRenderer.mockReturnValue(null);
    service.initialize(17, 'EPUB');

    service.handleRelocateEvent({
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
      section: {current: 1, total: 10},
      time: {section: 1},
    });

    expect(viewManagerService.updateHeadersAndFooters).not.toHaveBeenCalled();
  });

  it('resets progress state back to nullish defaults', () => {
    service.initialize(17, 'EPUB');
    service.handleRelocateEvent({
      cfi: 'epubcfi(/6/2)',
      fraction: 0.1,
      tocItem: {label: 'Chapter 1', href: 'chapter.xhtml'},
    });

    service.reset();

    expect(service.currentCfi).toBeNull();
    expect(service.currentChapterName).toBeNull();
    expect(service.currentChapterHref).toBeNull();
    expect(service.currentProgressData).toBeNull();
    expect(service.currentPageInfo).toBeUndefined();
  });
});
