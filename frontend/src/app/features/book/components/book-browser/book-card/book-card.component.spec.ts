import {NO_ERRORS_SCHEMA, SimpleChange, SimpleChanges} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
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
    currentUser: ReturnType<typeof vi.fn>;
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
  let translocoService: {
    translate: ReturnType<typeof vi.fn>;
  };
  let queryClient: {
    fetchQuery: ReturnType<typeof vi.fn>;
  };
  let cdr: {
    markForCheck: ReturnType<typeof vi.fn>;
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
    userService = {
      currentUser: vi.fn(() => null),
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
    translocoService = {
      translate: vi.fn((key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key)),
    };
    queryClient = {
      fetchQuery: vi.fn(),
    };
    cdr = {
      markForCheck: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [BookCardComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
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
        {provide: TranslocoService, useValue: translocoService},
        {provide: QueryClient, useValue: queryClient},
        {provide: QueryClient, useValue: queryClient},
        {provide: BookCardComponent, useValue: BookCardComponent},
        {provide: 'ChangeDetectorRef', useValue: cdr},
      ],
    });

    component = TestBed.createComponent(BookCardComponent).componentInstance;
    (component as unknown as {cdr: typeof cdr}).cdr = cdr;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('derives progress, audiobook, and series display state from inputs and changes', () => {
    component.book = makeBook({
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
    });
    component.seriesViewEnabled = true;
    component.isSeriesCollapsed = true;
    component.forceEbookMode = false;

    component.ngOnInit();

    expect(component.hasProgress).toBe(true);
    expect(component.displayTitle).toBe('Series Saga');
    expect(component.titleTooltip).toContain('Series Saga');
    expect(component.coverImageUrl).toBe('audio-thumb:8:2024-04-02');
    expect(component.progressTooltip).toBe('42% (Grimmory) | 11% (KOReader) | 7% (Kobo)');
    expect(component.readButtonIcon).toBe('pi pi-forward');

    component.isSeriesCollapsed = false;
    component.ngOnChanges({
      isSeriesCollapsed: new SimpleChange(true, false, false),
    } as SimpleChanges);

    expect(component.displayTitle).toBe('Volume One');
    expect(component.titleTooltip).toContain('Volume One');
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

    component.forceEbookMode = true;
    component.readBook(audiobookWithAlternativeFormat);

    expect(bookService.readBook).toHaveBeenCalledWith(12, undefined, 'PDF');

    component.forceEbookMode = false;
    component.readBook(makeBook({id: 13}));

    expect(bookService.readBook).toHaveBeenCalledWith(13);
  });

  it('derives display format from missing primary files, extensions, paths, and forced audiobook ebook types', () => {
    component.book = makeBook({id: 20, primaryFile: undefined});
    component.forceEbookMode = false;
    expect(component.getDisplayFormat()).toBe('PHY');

    component.book = makeBook({
      id: 21,
      primaryFile: {
        id: 211,
        bookId: 21,
        bookType: 'PDF',
        extension: 'pdf',
        filePath: 'books/volume-one.pdf',
      },
    });
    expect(component.getDisplayFormat()).toBe('PDF');

    component.book = makeBook({
      id: 22,
      primaryFile: {
        id: 221,
        bookId: 22,
        bookType: 'EPUB',
        filePath: 'books/volume-one.mobi',
      },
    });
    expect(component.getDisplayFormat()).toBe('MOBI');

    component.book = makeBook({
      id: 23,
      primaryFile: {
        id: 231,
        bookId: 23,
        bookType: 'AUDIOBOOK',
        filePath: 'books/volume-one.m4b',
      },
      epubProgress: {cfi: 'epub', percentage: 73},
    });
    component.forceEbookMode = true;
    expect(component.getDisplayFormat()).toBe('EPUB');
  });

  it('guards card selection and preserves shift metadata for ctrl-initiated changes only', () => {
    component.book = makeBook({id: 30});
    component.index = 4;
    component.isCheckboxEnabled = false;
    component.isSelected = false;
    component.onBookSelect = vi.fn();

    const emitSpy = vi.spyOn(component.checkboxClick, 'emit');

    component.toggleCardSelection(true);
    expect(emitSpy).not.toHaveBeenCalled();
    expect(component.onBookSelect).not.toHaveBeenCalled();

    component.isCheckboxEnabled = true;
    component.captureMouseEvent(new MouseEvent('mousedown', {shiftKey: true}));

    component.onCardClick(new MouseEvent('click', {ctrlKey: false}));
    expect(emitSpy).not.toHaveBeenCalled();

    component.onCardClick(new MouseEvent('click', {ctrlKey: true}));

    expect(component.isSelected).toBe(true);
    expect(emitSpy).toHaveBeenCalledWith({
      index: 4,
      book: component.book,
      selected: true,
      shiftKey: true,
    });
    expect(component.onBookSelect).toHaveBeenCalledWith(component.book, true);
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

    userService.currentUser.mockReturnValue(makeUser('route'));
    component.book = book;
    component.isSeriesCollapsed = true;
    component.ngOnInit();

    component.openSeriesInfo();
    expect(router.navigate).toHaveBeenCalledWith(['/series', 'The Series']);

    bookNavigationService.availableBookIds.mockReturnValue([2, 44, 77]);
    component.isSeriesCollapsed = false;
    component.openSeriesInfo();

    expect(bookNavigationService.setNavigationContext).toHaveBeenCalledWith([2, 44, 77], 44);
    expect(router.navigate).toHaveBeenCalledWith(['/book', 44], {
      queryParams: {tab: 'view'},
    });

    userService.currentUser.mockReturnValue(makeUser('dialog'));
    const dialogComponent = component as unknown as {metadataCenterViewMode: 'route' | 'dialog'};
    dialogComponent.metadataCenterViewMode = 'dialog';
    router.navigate.mockClear();
    bookNavigationService.setNavigationContext.mockClear();

    component.openBookInfo(book);

    expect(bookDialogHelperService.openBookDetailsDialog).toHaveBeenCalledWith(44);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(bookNavigationService.setNavigationContext).toHaveBeenCalledWith([2, 44, 77], 44);
  });
});
