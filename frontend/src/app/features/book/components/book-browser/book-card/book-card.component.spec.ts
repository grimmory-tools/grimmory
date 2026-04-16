import {ComponentRef, NO_ERRORS_SCHEMA, signal, WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router} from '@angular/router';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ConfirmationService, MessageService} from 'primeng/api';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {EmailService} from '../../../../settings/email-v2/email.service';
import {User, UserService} from '../../../../settings/user-management/user.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {AdditionalFile, Book, ReadStatus} from '../../../model/book.model';
import {BookDialogHelperService} from '../book-dialog-helper.service';
import {BookCardComponent} from './book-card.component';
import {BookFileService} from '../../../service/book-file.service';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {BookNavigationService} from '../../../service/book-navigation.service';
import {BookService} from '../../../service/book.service';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';

function makeBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 41,
    libraryId: 2,
    libraryName: 'Library',
    metadata: {
      bookId: 41,
      title: 'Book Title',
      seriesName: 'Series Name',
      coverUpdatedOn: '2024-01-01',
      audiobookCoverUpdatedOn: '2024-01-02',
    },
    ...overrides,
  };
}

function makeUser(metadataCenterViewMode: 'route' | 'dialog'): User {
  return {
    id: 1,
    username: 'tester',
    name: 'Tester',
    email: 'tester@example.com',
    assignedLibraries: [],
    permissions: {
      admin: false,
      canUpload: false,
      canDownload: false,
      canEmailBook: false,
      canDeleteBook: false,
      canEditMetadata: false,
      canManageLibrary: false,
      canManageMetadataConfig: false,
      canSyncKoReader: false,
      canSyncKobo: false,
      canAccessOpds: false,
      canAccessBookdrop: false,
      canAccessLibraryStats: false,
      canAccessUserStats: false,
      canAccessTaskManager: false,
      canManageEmailConfig: false,
      canManageGlobalPreferences: false,
      canManageIcons: false,
      canManageFonts: false,
      demoUser: false,
      canBulkAutoFetchMetadata: false,
      canBulkCustomFetchMetadata: false,
      canBulkEditMetadata: false,
      canBulkRegenerateCover: false,
      canMoveOrganizeFiles: false,
      canBulkLockUnlockMetadata: false,
    },
    userSettings: {
      metadataCenterViewMode,
      perBookSetting: {} as never,
      pdfReaderSetting: {} as never,
      epubReaderSetting: {} as never,
      ebookReaderSetting: {} as never,
      cbxReaderSetting: {} as never,
      newPdfReaderSetting: {} as never,
      sidebarLibrarySorting: {} as never,
      sidebarShelfSorting: {} as never,
      sidebarMagicShelfSorting: {} as never,
      filterMode: 'and',
      enableSeriesView: false,
      entityViewPreferences: {global: {} as never, overrides: []},
      koReaderEnabled: false,
      autoSaveMetadata: false,
    },
  };
}

describe('BookCardComponent', () => {
  let component: BookCardComponent;
  let fixture: ComponentFixture<BookCardComponent>;
  let ref: ComponentRef<BookCardComponent>;
  let bookService: {
    readBook: ReturnType<typeof vi.fn>;
  };
  let bookFileService: {
    downloadFile: ReturnType<typeof vi.fn>;
    downloadAdditionalFile: ReturnType<typeof vi.fn>;
    deleteAdditionalFile: ReturnType<typeof vi.fn>;
  };
  let bookMetadataManageService: {
    regenerateCover: ReturnType<typeof vi.fn>;
    generateCustomCover: ReturnType<typeof vi.fn>;
  };
  let taskHelperService: {
    refreshMetadataTask: ReturnType<typeof vi.fn>;
  };
  let userService: {
    currentUser: WritableSignal<User | null>;
    getCurrentUser: ReturnType<typeof vi.fn>;
  };
  let emailService: {
    emailBookQuick: ReturnType<typeof vi.fn>;
  };
  let messageService: {
    add: ReturnType<typeof vi.fn>;
  };
  let router: {
    navigate: ReturnType<typeof vi.fn>;
  };
  let urlHelper: {
    getThumbnailUrl: ReturnType<typeof vi.fn>;
    getAudiobookThumbnailUrl: ReturnType<typeof vi.fn>;
    getBookPrimaryReadingUrl: ReturnType<typeof vi.fn>;
  };
  let confirmationService: {
    confirm: ReturnType<typeof vi.fn>;
  };
  let bookDialogHelperService: {
    openShelfAssignerDialog: ReturnType<typeof vi.fn>;
    openBookDetailsDialog: ReturnType<typeof vi.fn>;
    openCustomSendDialog: ReturnType<typeof vi.fn>;
    openMetadataRefreshDialog: ReturnType<typeof vi.fn>;
    openFileMoverDialog: ReturnType<typeof vi.fn>;
  };
  let bookNavigationService: {
    availableBookIds: ReturnType<typeof vi.fn>;
    setNavigationContext: ReturnType<typeof vi.fn>;
  };
  let appSettingsService: {
    appSettings: ReturnType<typeof vi.fn>;
  };
  let queryClient: {
    fetchQuery: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    bookService = {
      readBook: vi.fn(),
    };
    bookFileService = {
      downloadFile: vi.fn(),
      downloadAdditionalFile: vi.fn(),
      deleteAdditionalFile: vi.fn(),
    };
    bookMetadataManageService = {
      regenerateCover: vi.fn(),
      generateCustomCover: vi.fn(),
    };
    taskHelperService = {
      refreshMetadataTask: vi.fn(),
    };
    const currentUser = signal<User | null>(null);
    userService = {
      currentUser,
      getCurrentUser: vi.fn(() => currentUser()),
    };
    emailService = {
      emailBookQuick: vi.fn(),
    };
    messageService = {
      add: vi.fn(),
    };
    router = {
      navigate: vi.fn(),
    };
    urlHelper = {
      getThumbnailUrl: vi.fn((bookId: number, coverUpdatedOn?: string) => `thumb:${bookId}:${coverUpdatedOn ?? 'none'}`),
      getAudiobookThumbnailUrl: vi.fn((bookId: number, audiobookCoverUpdatedOn?: string) => `audio-thumb:${bookId}:${audiobookCoverUpdatedOn ?? 'none'}`),
      getBookPrimaryReadingUrl: vi.fn((book: Book) => `/read/${book.id}`),
    };
    confirmationService = {
      confirm: vi.fn(),
    };
    bookDialogHelperService = {
      openShelfAssignerDialog: vi.fn(),
      openBookDetailsDialog: vi.fn(),
      openCustomSendDialog: vi.fn(),
      openMetadataRefreshDialog: vi.fn(),
      openFileMoverDialog: vi.fn(),
    };
    bookNavigationService = {
      availableBookIds: vi.fn(() => []),
      setNavigationContext: vi.fn(),
    };
    appSettingsService = {
      appSettings: vi.fn(() => ({diskType: 'LOCAL'})),
    };
    queryClient = {
      fetchQuery: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [BookCardComponent, getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {provide: ActivatedRoute, useValue: {}},
        {provide: BookService, useValue: bookService},
        {provide: BookFileService, useValue: bookFileService},
        {provide: BookMetadataManageService, useValue: bookMetadataManageService},
        {provide: TaskHelperService, useValue: taskHelperService},
        {provide: UserService, useValue: userService},
        {provide: EmailService, useValue: emailService},
        {provide: MessageService, useValue: messageService},
        {provide: Router, useValue: router},
        {provide: UrlHelperService, useValue: urlHelper},
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: BookDialogHelperService, useValue: bookDialogHelperService},
        {provide: BookNavigationService, useValue: bookNavigationService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: QueryClient, useValue: queryClient},
      ],
    });

    // Secondary mock for matchMedia to ensure it's available in CI environment
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
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

    fixture = TestBed.createComponent(BookCardComponent);
    ref = fixture.componentRef;
    component = fixture.componentInstance;

    // Set required inputs before first change detection
    ref.setInput('book', makeBook());
    ref.setInput('index', 0);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('derives progress, audiobook, and series display state from inputs', () => {
    ref.setInput('book', makeBook({
      id: 8,
      metadata: {
        bookId: 8,
        title: 'Volume One',
        seriesName: 'Series Saga',
        coverUpdatedOn: '2024-04-01',
        audiobookCoverUpdatedOn: '2024-04-02',
      },
      seriesCount: 2,
      primaryFile: {
        id: 81,
        bookId: 8,
        bookType: 'AUDIOBOOK',
        extension: 'm4b',
        filePath: 'books/volume-one.m4b',
      },
      pdfProgress: {page: 11, percentage: 42},
      koreaderProgress: {percentage: 11},
      koboProgress: {percentage: 7},
      readStatus: ReadStatus.READING,
    }));
    ref.setInput('seriesViewEnabled', true);
    ref.setInput('isSeriesCollapsed', true);
    ref.setInput('forceEbookMode', false);
    fixture.detectChanges();

    expect(component.hasProgress()).toBe(true);
    expect(component.displayTitle()).toBe('Series Saga');
    expect(component.titleTooltip()).toContain('Series Saga');
    expect(component.coverImageUrl()).toBe('audio-thumb:8:2024-04-02');
    expect(component.progressTooltip()).toBe('42% (Grimmory) | 11% (KOReader) | 7% (Kobo)');
    expect(component.readButtonIcon()).toBe('pi pi-forward');

    ref.setInput('isSeriesCollapsed', false);
    fixture.detectChanges();

    expect(component.displayTitle()).toBe('Volume One');
    expect(component.titleTooltip()).toContain('Volume One');
  });

  it('prefers the audiobook thumbnail for square covers when the book has an audiobook format', () => {
    ref.setInput('book', makeBook({
      id: 9,
      primaryFile: {
        id: 91,
        bookId: 9,
        bookType: 'EPUB',
        extension: 'epub',
        filePath: 'books/volume-nine.epub',
      },
      alternativeFormats: [
        {
          id: 92,
          bookId: 9,
          bookType: 'AUDIOBOOK',
          extension: 'm4b',
          filePath: 'books/volume-nine.m4b',
        } as AdditionalFile,
      ],
    }));
    ref.setInput('useSquareCovers', true);
    fixture.detectChanges();

    expect(component.coverImageUrl()).toBe('audio-thumb:9:2024-01-02');

    ref.setInput('useSquareCovers', false);
    fixture.detectChanges();

    expect(component.coverImageUrl()).toBe('thumb:9:2024-01-01');
  });

  it('uses forced ebook mode for audiobook reads and falls back to the normal read flow otherwise', () => {
    const audiobookWithAlternativeFormat = makeBook({
      id: 12,
      primaryFile: {
        id: 121,
        bookId: 12,
        bookType: 'AUDIOBOOK',
        extension: 'm4b',
        filePath: 'books/audiobook.m4b',
      },
      alternativeFormats: [
        {
          id: 122,
          bookId: 12,
          bookType: 'PDF',
          fileName: 'A PDF',
          filePath: 'books/audiobook.pdf',
          fileSizeKb: 2048,
        } as AdditionalFile,
      ],
    });

    ref.setInput('forceEbookMode', true);
    fixture.detectChanges();
    component.readBook(audiobookWithAlternativeFormat);

    expect(bookService.readBook).toHaveBeenCalledWith(12, undefined, 'PDF');

    ref.setInput('forceEbookMode', false);
    fixture.detectChanges();
    component.readBook(makeBook({id: 13}));

    expect(bookService.readBook).toHaveBeenCalledWith(13);
  });

  it('derives display format from missing primary files, extensions, paths, and forced audiobook ebook types', () => {
    ref.setInput('book', makeBook({id: 20, primaryFile: undefined}));
    ref.setInput('forceEbookMode', false);
    fixture.detectChanges();
    expect(component.displayFormat()).toBe('PHY');

    ref.setInput('book', makeBook({
      id: 21,
      primaryFile: {
        id: 211,
        bookId: 21,
        bookType: 'PDF',
        extension: 'pdf',
        filePath: 'books/volume-one.pdf',
      },
    }));
    fixture.detectChanges();
    expect(component.displayFormat()).toBe('PDF');

    ref.setInput('book', makeBook({
      id: 22,
      primaryFile: {
        id: 221,
        bookId: 22,
        bookType: 'EPUB',
        filePath: 'books/volume-one.mobi',
      },
    }));
    fixture.detectChanges();
    expect(component.displayFormat()).toBe('MOBI');

    ref.setInput('book', makeBook({
      id: 23,
      primaryFile: {
        id: 231,
        bookId: 23,
        bookType: 'AUDIOBOOK',
        filePath: 'books/volume-one.m4b',
      },
      epubProgress: {cfi: 'epub', percentage: 73},
    }));
    ref.setInput('forceEbookMode', true);
    fixture.detectChanges();
    expect(component.displayFormat()).toBe('EPUB');
  });

  it('guards card selection and emits checkbox events for ctrl-initiated changes only', () => {
    ref.setInput('book', makeBook({id: 30}));
    ref.setInput('index', 4);
    ref.setInput('isCheckboxEnabled', false);
    ref.setInput('isSelected', false);
    const selectFn = vi.fn();
    ref.setInput('onBookSelect', selectFn);
    fixture.detectChanges();

    const emitted: unknown[] = [];
    component.checkboxClick.subscribe(e => emitted.push(e));

    component.toggleCardSelection(true);
    expect(emitted).toHaveLength(0);
    expect(selectFn).not.toHaveBeenCalled();

    ref.setInput('isCheckboxEnabled', true);
    fixture.detectChanges();
    component.captureMouseEvent(new MouseEvent('mousedown', {shiftKey: true}));

    component.onCardClick(new MouseEvent('click', {ctrlKey: false}));
    expect(emitted).toHaveLength(0);

    component.onCardClick(new MouseEvent('click', {ctrlKey: true}));

    expect(emitted).toHaveLength(1);
    expect(emitted[0]).toEqual({
      index: 4,
      book: component.book(),
      selected: true,
      shiftKey: true,
    });
    expect(selectFn).toHaveBeenCalledWith(component.book(), true);
  });

  it('routes series info and book details through the correct destination', () => {
    const book = makeBook({
      id: 44,
      metadata: {
        bookId: 44,
        title: 'Volume Four',
        seriesName: 'The Series',
        coverUpdatedOn: '2024-05-01',
        audiobookCoverUpdatedOn: '2024-05-02',
      },
    });

    userService.currentUser.set(makeUser('route'));
    ref.setInput('book', book);
    ref.setInput('isSeriesCollapsed', true);
    fixture.detectChanges();

    component.openSeriesInfo();
    expect(router.navigate).toHaveBeenCalledWith(['/series', 'The Series']);

    bookNavigationService.availableBookIds.mockReturnValue([2, 44, 77]);
    ref.setInput('isSeriesCollapsed', false);
    fixture.detectChanges();
    component.openSeriesInfo();

    expect(bookNavigationService.setNavigationContext).toHaveBeenCalledWith([2, 44, 77], 44);
    expect(router.navigate).toHaveBeenCalledWith(['/book', 44], {
      queryParams: {tab: 'view'},
    });

    userService.currentUser.set(makeUser('dialog'));
    router.navigate.mockClear();
    bookNavigationService.setNavigationContext.mockClear();

    component.openBookInfo(book);

    expect(bookDialogHelperService.openBookDetailsDialog).toHaveBeenCalledWith(44);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(bookNavigationService.setNavigationContext).toHaveBeenCalledWith([2, 44, 77], 44);
  });
});
