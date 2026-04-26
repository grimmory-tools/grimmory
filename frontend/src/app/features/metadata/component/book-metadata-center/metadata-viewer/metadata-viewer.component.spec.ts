import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ConfirmationService, MessageService, MenuItem} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {Book, BookFile, BookMetadata, BookRecommendation, ReadStatus} from '../../../../book/model/book.model';
import {BookService} from '../../../../book/service/book.service';
import {BookFileService} from '../../../../book/service/book-file.service';
import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {EmailService} from '../../../../settings/email-v2/email.service';
import {BookDialogHelperService} from '../../../../book/components/book-browser/book-dialog-helper.service';
import {LibraryService} from '../../../../book/service/library.service';
import {BookMetadataManageService} from '../../../../book/service/book-metadata-manage.service';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {AuthorService} from '../../../../author-browser/service/author.service';
import {Router} from '@angular/router';
import {BookNavigationService} from '../../../../book/service/book-navigation.service';
import {BookMetadataHostService} from '../../../../../shared/service/book-metadata-host.service';
import {MetadataViewerComponent} from './metadata-viewer.component';
import {AudiobookService} from '../../../../readers/audiobook-player/audiobook.service';

interface CurrentUser {
  permissions?: {
    canManageLibrary?: boolean;
    canUpload?: boolean;
    canEmailBook?: boolean;
    canDeleteBook?: boolean;
    admin?: boolean;
  };
  userSettings?: {
    metadataCenterViewMode?: 'route' | 'dialog';
  };
}

interface ConfirmationLike {
  message?: string;
  header?: string;
  accept?: () => void;
}

// Ensure matchMedia is mocked for PrimeNG components like TieredMenu
if (typeof window !== 'undefined' && !window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(query => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

// Ensure ResizeObserver is mocked for PrimeNG components like Tabs
if (typeof window !== 'undefined' && !window.ResizeObserver) {
  window.ResizeObserver = class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
  };
}

// Ensure IntersectionObserver is mocked
if (typeof window !== 'undefined' && !window.IntersectionObserver) {
  window.IntersectionObserver = class {
    root = null;
    rootMargin = '';
    thresholds = [0];
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
    takeRecords = vi.fn(() => []);
  };
}

describe('MetadataViewerComponent', () => {
  const currentUser = signal<CurrentUser | null>(null);
  const appSettings = signal<AppSettings | null>(null);
  const navigationState = signal(false);
  const canNavigatePrevious = signal(false);
  const canNavigateNext = signal(false);
  const currentPosition = signal<{ current: number; total: number } | null>(null);

  const readBook = vi.fn();
  const togglePhysicalFlag = vi.fn(() => of(void 0));
  const deleteBooks = vi.fn(() => of(void 0));
  const updateDateFinished = vi.fn(() => of(void 0));
  const updateBookReadStatus = vi.fn(() => of(void 0));
  const resetProgress = vi.fn(() => of(void 0));
  const updatePersonalRating = vi.fn(() => of(void 0));
  const resetPersonalRating = vi.fn(() => of(void 0));
  const getBooksInSeries = vi.fn(() => of([] as Book[]));
  const findBookById = vi.fn();

  const downloadFile = vi.fn();
  const downloadAdditionalFile = vi.fn();
  const downloadAllFiles = vi.fn();
  const deleteAdditionalFile = vi.fn(() => of(void 0));
  const deleteBookFile = vi.fn(() => of(void 0));
  const detachBookFile = vi.fn(() => of(void 0));

  const openAdditionalFileUploaderDialog = vi.fn();
  const openCustomSendDialog = vi.fn();
  const openBookFileAttacherDialog = vi.fn();
  const openFileMoverDialog = vi.fn();
  const openShelfAssignerDialog = vi.fn();

  const emailBookQuick = vi.fn(() => of(void 0));
  const refreshMetadataTask = vi.fn(() => of(void 0));
  const getAuthorByName = vi.fn(() => of({id: 41}));

  const findLibraryById = vi.fn(() => ({allowedFormats: ['PDF', 'EPUB']}));
  const switchBook = vi.fn();
  const updateCurrentBook = vi.fn();
  const previousBookId = vi.fn();
  const nextBookId = vi.fn();
  const routerNavigate = vi.fn(() => Promise.resolve(true));

  let lastConfirmation: ConfirmationLike | null = null;
  const confirm = vi.fn((confirmation: ConfirmationLike) => {
    lastConfirmation = confirmation;
  });
  const messageAdd = vi.fn();
  const translate = vi.fn((key: string, params?: Record<string, unknown>) => {
    if (!params) {
      return key;
    }
    return `${key}:${Object.entries(params).map(([paramKey, value]) => `${paramKey}=${value}`).join(',')}`;
  });
  const getCoverUrl = vi.fn((bookId: number, updatedOn?: string) => `cover:${bookId}:${updatedOn ?? 'none'}`);
  const getAudiobookCoverUrl = vi.fn((bookId: number, updatedOn?: string) => `audio:${bookId}:${updatedOn ?? 'none'}`);
  const getThumbnailUrl = vi.fn((bookId: number, updatedOn?: string) => `thumb:${bookId}:${updatedOn ?? 'none'}`);

  function createFile(
    id: number,
    overrides: Partial<BookFile> = {},
  ): BookFile {
    return {
      id,
      bookId: 21,
      fileName: `file-${id}.epub`,
      filePath: `/books/file-${id}.epub`,
      fileSizeKb: 100,
      ...overrides,
    };
  }

  function createMetadata(overrides: Partial<BookMetadata> = {}): BookMetadata {
    return {
      bookId: 21,
      title: 'Original Title',
      authors: ['Alice'],
      coverUpdatedOn: '2026-03-26',
      audiobookCoverUpdatedOn: '2026-03-26',
      ...overrides,
    };
  }

  function createBook(
    overrides: Partial<Book> = {},
    metadataOverrides: Partial<BookMetadata> = {},
  ): Book {
    return {
      id: 21,
      libraryId: 1,
      libraryName: 'Library',
      metadata: createMetadata(metadataOverrides),
      primaryFile: createFile(1, {bookId: 21, bookType: 'EPUB'}),
      alternativeFormats: [],
      supplementaryFiles: [],
      isPhysical: false,
      ...overrides,
    } as Book;
  }

  function createComponent() {
    const fixture = TestBed.createComponent(MetadataViewerComponent);
    return {
      fixture,
      component: fixture.componentInstance
    };
  }

  function runMenuCommand(command: MenuItem['command'] | undefined): void {
    (command as (() => void) | undefined)?.();
  }

  beforeEach(() => {
    currentUser.set(null);
    appSettings.set(null);
    navigationState.set(false);
    canNavigatePrevious.set(false);
    canNavigateNext.set(false);
    currentPosition.set(null);

    readBook.mockClear();
    togglePhysicalFlag.mockClear();
    deleteBooks.mockClear();
    updateDateFinished.mockClear();
    updateBookReadStatus.mockClear();
    resetProgress.mockClear();
    updatePersonalRating.mockClear();
    resetPersonalRating.mockClear();
    getBooksInSeries.mockClear();
    findBookById.mockClear();

    downloadFile.mockClear();
    downloadAdditionalFile.mockClear();
    downloadAllFiles.mockClear();
    deleteAdditionalFile.mockClear();
    deleteBookFile.mockClear();
    detachBookFile.mockClear();

    openAdditionalFileUploaderDialog.mockClear();
    openCustomSendDialog.mockClear();
    openBookFileAttacherDialog.mockClear();
    openFileMoverDialog.mockClear();
    openShelfAssignerDialog.mockClear();

    emailBookQuick.mockClear();
    refreshMetadataTask.mockClear();
    getAuthorByName.mockClear();

    findLibraryById.mockClear();
    switchBook.mockClear();
    updateCurrentBook.mockClear();
    previousBookId.mockClear();
    nextBookId.mockClear();
    routerNavigate.mockClear();

    confirm.mockClear();
    messageAdd.mockClear();
    translate.mockClear();
    getCoverUrl.mockClear();
    getAudiobookCoverUrl.mockClear();
    getThumbnailUrl.mockClear();
    lastConfirmation = null;

    TestBed.configureTestingModule({
      imports: [MetadataViewerComponent],
      providers: [
        {
          provide: TranslocoService,
          useValue: {
            translate,
            config: {reRenderOnLangChange: true},
            langChanges$: of('en'),
            events$: of({}),
            getActiveLang: () => 'en',
            _loadDependencies: () => of([])
          }
        },
        {provide: LibraryService, useValue: {findLibraryById}},
        {provide: BookMetadataManageService, useValue: {supportsDualCovers: signal(false)}},
        {provide: AudiobookService, useValue: {}},
        {
          provide: BookDialogHelperService,
          useValue: {
            openAdditionalFileUploaderDialog,
            openCustomSendDialog,
            openBookFileAttacherDialog,
            openFileMoverDialog,
            openShelfAssignerDialog,
          },
        },
        {provide: EmailService, useValue: {emailBookQuick}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {
          provide: BookService,
          useValue: {
            readBook,
            togglePhysicalFlag,
            deleteBooks,
            updateDateFinished,
            updateBookReadStatus,
            resetProgress,
            updatePersonalRating,
            resetPersonalRating,
            getBooksInSeries,
            findBookById,
          },
        },
        {provide: BookFileService, useValue: {downloadFile, downloadAdditionalFile, downloadAllFiles, deleteAdditionalFile, deleteBookFile, detachBookFile}},
        {provide: TaskHelperService, useValue: {refreshMetadataTask}},
        {provide: AuthorService, useValue: {getAuthorByName}},
        {provide: UrlHelperService, useValue: {getCoverUrl, getAudiobookCoverUrl, getThumbnailUrl}},
        {provide: UserService, useValue: {currentUser}},
        {provide: AppSettingsService, useValue: {appSettings}},
        {provide: ConfirmationService, useValue: {confirm}},
        {provide: Router, useValue: {navigate: routerNavigate}},
        {
          provide: BookNavigationService,
          useValue: {
            navigationState,
            canNavigatePrevious,
            canNavigateNext,
            currentPosition,
            previousBookId,
            nextBookId,
            updateCurrentBook,
          },
        },
        {provide: BookMetadataHostService, useValue: {switchBook}},
      ],
    });
  });

  it('filters series recommendations and builds the read, download, and other menus from the current book', () => {
    const {fixture, component} = createComponent();
    const seriesBooks = [
      {id: 8, metadata: {seriesNumber: 2}},
      {id: 4, metadata: {seriesNumber: 1}},
    ] as Book[];
    getBooksInSeries.mockReturnValueOnce(of(seriesBooks as Book[]));

    const recommendedBooks: BookRecommendation[] = [
      {book: {id: 4} as Book, similarityScore: 0.99},
      {book: {id: 17} as Book, similarityScore: 0.87},
    ];

    fixture.componentRef.setInput('recommendedBooks', recommendedBooks);
    const richBook = createBook(
      {
        id: 21,
        primaryFile: createFile(1, {bookId: 21, bookType: 'EPUB', fileName: 'main.epub', filePath: '/books/main.epub', fileSizeKb: 2048}),
        alternativeFormats: [
          createFile(2, {bookId: 21, bookType: 'EPUB', fileName: 'alt.epub', filePath: '/books/alt.epub', fileSizeKb: 1024}),
          createFile(3, {bookId: 21, bookType: 'PDF', fileName: 'art.pdf', filePath: '/books/art.pdf', fileSizeKb: 5120}),
        ],
        supplementaryFiles: [
          createFile(4, {bookId: 21, fileName: 'appendix.txt', filePath: '/books/appendix.txt', fileSizeKb: 12}),
        ],
      },
      {
        bookId: 21,
        seriesName: 'Series One',
        seriesNumber: 3,
      }
    );
    findBookById.mockReturnValue(richBook);

    currentUser.set({
      permissions: {
        canManageLibrary: true,
        canUpload: true,
        canEmailBook: true,
        canDeleteBook: true,
      },
      userSettings: {
        metadataCenterViewMode: 'route',
      },
    });
    appSettings.set({
      diskType: 'LOCAL',
      metadataProviderSettings: {
        amazon: {domain: 'co.uk'},
      },
    } as AppSettings);

    fixture.componentRef.setInput('book', richBook);
    fixture.detectChanges();

    expect(component.bookInSeries.map(book => book.id)).toEqual([4, 8]);
    expect(component.recommendedBooksFiltered.map(book => book.book.id)).toEqual([17]);

    const readItems = component.readMenuItems();
    expect(readItems.map(item => item.separator ? 'separator' : item.label)).toEqual([
      'metadata.viewer.menuStreamingReader',
      'separator',
      'EPUB',
      'PDF',
    ]);
    expect(readItems[0].command).toBeDefined();
    runMenuCommand(readItems[0].command);
    expect(readBook).toHaveBeenCalledWith(21, 'epub-streaming', undefined);
    runMenuCommand(readItems[2].items?.[0].command);
    expect(readBook).toHaveBeenLastCalledWith(21, undefined, 'EPUB');

    const downloadItems = component.downloadMenuItems();
    expect(downloadItems.some(item => item.separator)).toBe(true);
    expect(downloadItems[0].label).toContain('EPUB ·');
    expect(downloadItems[3].label).toContain('appendix.txt');
    runMenuCommand(downloadItems[0].command);
    expect(downloadAdditionalFile).toHaveBeenCalledWith(richBook, 2);
    runMenuCommand(downloadItems[3].command);
    expect(downloadAdditionalFile).toHaveBeenCalledWith(richBook, 4);

    const otherItems = component.otherItems();
    expect(otherItems.map(item => item.separator ? 'separator' : item.label)).toEqual([
      'metadata.viewer.menuShelf',
      'metadata.viewer.menuMarkPhysical',
      'metadata.viewer.menuUploadFile',
      'metadata.viewer.menuOrganizeFiles',
      'metadata.viewer.menuSendBook',
      'metadata.viewer.menuDeleteFileFormats',
      'metadata.viewer.menuDeleteSupplementaryFiles',
      'metadata.viewer.menuDeleteBookAllFiles',
    ]);

    runMenuCommand(otherItems[0].command);
    expect(openShelfAssignerDialog).toHaveBeenCalledWith(richBook, null);

    runMenuCommand(otherItems[1].command);
    expect(togglePhysicalFlag).toHaveBeenCalledWith(21, true);

    runMenuCommand(otherItems[2].command);
    expect(openAdditionalFileUploaderDialog).toHaveBeenCalledWith(richBook);

    runMenuCommand(otherItems[3].command);
    expect(openFileMoverDialog).toHaveBeenCalledWith(new Set([21]));

    const sendBookItem = otherItems[4];
    runMenuCommand(sendBookItem.items?.[0].command);
    expect(emailBookQuick).toHaveBeenCalledWith(21);
    runMenuCommand(sendBookItem.items?.[1].command);
    expect(openCustomSendDialog).toHaveBeenCalledWith(richBook);

    const deleteFormatItems = otherItems[5].items ?? [];
    runMenuCommand(deleteFormatItems[0].command);
    expect(confirm).toHaveBeenCalled();

    const deleteSupplementaryItems = otherItems[6].items ?? [];
    runMenuCommand(deleteSupplementaryItems[0].command);
    expect(confirm).toHaveBeenCalledTimes(2);
  });

  it('chooses confirmation copy for file deletion branches and runs the accept callbacks', () => {
    const {component} = createComponent();
    const book = createBook();

    component.deleteBookFile(book, 1, 'primary.epub', true, true);
    expect(lastConfirmation).toMatchObject({
      message: 'metadata.viewer.confirm.deleteOnlyFormatMessage:fileName=primary.epub',
      header: 'metadata.viewer.confirm.deleteOnlyFormatHeader',
    });

    component.deleteBookFile(book, 2, 'primary.epub', true, false);
    expect(lastConfirmation).toMatchObject({
      message: 'metadata.viewer.confirm.deletePrimaryFormatMessage:fileName=primary.epub',
      header: 'metadata.viewer.confirm.deletePrimaryFormatHeader',
    });

    component.deleteBookFile(book, 3, 'alt.pdf', false, false);
    expect(lastConfirmation).toMatchObject({
      message: 'metadata.viewer.confirm.deleteAltFormatMessage:fileName=alt.pdf',
      header: 'metadata.viewer.confirm.deleteAltFormatHeader',
    });
    lastConfirmation?.accept?.();
    expect(deleteBookFile).toHaveBeenCalledWith(21, 3, false);
    expect(messageAdd).toHaveBeenLastCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 'metadata.viewer.toast.deleteFormatSuccessSummary',
    }));

    deleteAdditionalFile.mockReturnValueOnce(throwError(() => new Error('boom')));
    component.deleteAdditionalFile(21, 9, 'appendix.txt');
    expect(lastConfirmation).toMatchObject({
      message: 'metadata.viewer.confirm.deleteSupplementaryMessage:fileName=appendix.txt',
      header: 'metadata.viewer.confirm.deleteSupplementaryHeader',
    });
    lastConfirmation?.accept?.();
    expect(deleteAdditionalFile).toHaveBeenCalledWith(21, 9);
    expect(messageAdd).toHaveBeenLastCalledWith(expect.objectContaining({
      severity: 'error',
      summary: 'metadata.viewer.toast.deleteSupplementaryErrorSummary',
    }));
  });

  it('navigates and formats helper branches for read states, file metadata, and location helpers', () => {
    const {component} = createComponent();
    const viewer = component as unknown as {
      metadataCenterViewMode: 'route' | 'dialog';
    };

    previousBookId.mockReturnValue(12);
    nextBookId.mockReturnValue(27);

    viewer.metadataCenterViewMode = 'route';
    component.navigatePrevious();
    expect(updateCurrentBook).toHaveBeenCalledWith(12);
    expect(routerNavigate).toHaveBeenCalledWith(['/book', 12], {queryParams: {tab: 'view'}});

    viewer.metadataCenterViewMode = 'dialog';
    component.navigateNext();
    expect(updateCurrentBook).toHaveBeenLastCalledWith(27);
    expect(switchBook).toHaveBeenCalledWith(27);

    const audiobookBook = createBook({primaryFile: createFile(1, {bookType: 'AUDIOBOOK'})});
    component.selectedReadStatus = ReadStatus.READING;
    expect(component.isInProgressStatus()).toBe(true);
    expect(component.getReadButtonLabel(audiobookBook)).toBe('metadata.viewer.continueBtn');
    expect(component.getReadButtonIcon(audiobookBook)).toBe('pi pi-play');

    component.selectedReadStatus = ReadStatus.UNREAD;
    expect(component.isInProgressStatus()).toBe(false);
    expect(component.getReadButtonLabel(audiobookBook)).toBe('metadata.viewer.playBtn');
    expect(component.getReadButtonLabel(createBook())).toBe('metadata.viewer.readBtn');
    expect(component.getReadButtonIcon(createBook())).toBe('pi pi-book');

    expect(component.formatFileSize(null)).toBe('-');
    expect(component.formatFileSize({fileSizeKb: 1536})).toBe('1.50 MB');
    expect(component.truncateFileName('plain-name-without-extension', 12)).toBe('plain-nam...');
    expect(component.truncateFileName('very-long-name.epub', 12)).toBe('very....epub');
    expect(component.getDisplayFormat(createFile(9, {extension: 'pdf'}))).toBe('PDF');
    expect(component.getDisplayFormat(createFile(10, {filePath: '/books/chapter.cbx'}))).toBe('CBX');
    expect(component.getUniqueAlternativeFormats(createBook({
      primaryFile: createFile(1, {bookType: 'EPUB', extension: 'epub'}),
      alternativeFormats: [
        createFile(2, {bookType: 'EPUB', extension: 'epub'}),
        createFile(3, {bookType: 'PDF', extension: 'pdf'}),
        createFile(4, {bookType: 'PDF', extension: 'pdf'}),
      ],
    }))).toEqual(['PDF']);
    expect(component.getFileIcon(null)).toBe('pi pi-file');
    expect(component.getFileIcon('pdf')).toBe('pi pi-file-pdf');
    expect(component.getFileIcon('audiobook')).toBe('pi pi-headphones');
    expect(component.getFileTypeBgColor('PDF')).toBe('var(--book-type-pdf-color, var(--p-gray-500))');
    expect(component.getStarColorScaled(null)).toBe('rgb(203, 213, 225)');
    expect(component.getStarColorScaled(5)).toBe('rgb(34, 197, 94)');
    expect(component.getRatingPercent(4.5)).toBe(90);
    expect(component.getRatingTooltip(createBook({metadata: createMetadata({amazonRating: 4.2, amazonReviewCount: 1234})}), 'amazon')).toBe('★ 4.2 | 1,234 reviews');
    expect(component.getStatusLabel(ReadStatus.READING)).toBe('metadata.viewer.readStatusReading'.toUpperCase());
    expect(component.getStatusLabel('missing')).toBe('UNSET');
    expect(component.getBookCoverUrl(createBook())).toBe('cover:21:2026-03-26');
    expect(component.getBookCoverUrl(createBook({primaryFile: createFile(1, {bookType: 'AUDIOBOOK'})}))).toBe('audio:21:2026-03-26');
    expect(component.hasAnyFiles(createBook())).toBe(true);
    expect(component.isPhysicalBook(createBook({primaryFile: undefined, alternativeFormats: []}))).toBe(true);
    expect(component.isMetadataFullyLocked(createMetadata({titleLocked: true, subtitleLocked: false}))).toBe(false);
    expect(component.isMetadataFullyLocked(createMetadata({titleLocked: true, subtitleLocked: true, allMetadataLocked: true}))).toBe(true);
    expect(component.hasComicMetadata(createBook({metadata: createMetadata({comicMetadata: {issueNumber: '7'}})}))).toBe(true);
    expect(component.hasAnyCreators({pencillers: ['A']})).toBe(true);
    expect(component.formatWebLink('https://example.com/books/very/long/path/segment/that/exceeds/limit')).toContain('example.com');
    expect(component.getChannelLabel(1)).toBe('metadata.viewer.channelMono');
    expect(component.getChannelLabel(2)).toBe('metadata.viewer.channelStereo');
    expect(component.getChannelLabel(6)).toBe('metadata.viewer.channelMultiple:count=6');
  });
});
