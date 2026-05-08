import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import type {MenuItem, MenuItemCommandEvent} from 'primeng/api';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {LibraryShelfMenuService} from './library-shelf-menu.service';
import {LibraryService} from './library.service';
import {ShelfService} from './shelf.service';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {UserService} from '../../settings/user-management/user.service';
import {LoadingService} from '../../../core/services/loading.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {BookDialogHelperService} from '../components/book-browser/book-dialog-helper.service';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import type {Library} from '../model/library.model';
import type {Shelf} from '../model/shelf.model';
import type {MagicShelf} from '../../magic-shelf/service/magic-shelf.service';

function buildLibrary(overrides: Partial<Library> = {}): Library {
  return {
    id: 7,
    name: 'Main Library',
    watch: true,
    paths: [],
    ...overrides,
  };
}

function buildShelf(overrides: Partial<Shelf> = {}): Shelf {
  return {
    id: 11,
    name: 'Favorites',
    userId: 3,
    publicShelf: false,
    ...overrides,
  };
}

function buildMagicShelf(overrides: Partial<MagicShelf> = {}): MagicShelf {
  return {
    id: 13,
    name: 'Magic Shelf',
    filterJson: '{}',
    isPublic: false,
    ...overrides,
  };
}

function findMenuItem(items: MenuItem[], label: string): MenuItem {
  const item = items
    .flatMap(candidate => candidate.items ?? [candidate])
    .find(candidate => candidate.label === label);
  expect(item).toBeDefined();
  return item!;
}

function runCommand(item: MenuItem): void {
  item.command?.({ item } as MenuItemCommandEvent);
}

describe('LibraryShelfMenuService', () => {
  let service: LibraryShelfMenuService;
  const confirmationService = {
    confirm: vi.fn(),
  };
  const messageService = {
    add: vi.fn(),
  };
  const libraryService = {
    refreshLibrary: vi.fn(() => of(undefined)),
    deleteLibrary: vi.fn(() => of(undefined)),
  };
  const shelfService = {
    deleteShelf: vi.fn(() => of(undefined)),
  };
  const taskHelperService = {
    refreshMetadataTask: vi.fn(() => of(undefined)),
  };
  const userService = {
    getCurrentUser: vi.fn(() => ({id: 3, permissions: {admin: false}})),
  };
  const router = {
    navigate: vi.fn(),
  };
  const dialogLauncherService = {
    openLibraryEditDialog: vi.fn(),
    openShelfEditDialog: vi.fn(),
    openMagicShelfEditDialog: vi.fn(),
  };
  const magicShelfService = {
    deleteShelf: vi.fn(() => of(undefined)),
  };
  const loadingService = {
    show: vi.fn(() => 'loader-token'),
    hide: vi.fn(),
  };
  const bookDialogHelperService = {
    openAddPhysicalBookDialog: vi.fn(),
    openBulkIsbnImportDialog: vi.fn(),
    openDuplicateMergerDialog: vi.fn(),
    openMetadataRefreshDialogWithContext: vi.fn(),
  };
  const translocoService = {
    translate: vi.fn((key: string) => key),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        LibraryShelfMenuService,
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: LibraryService, useValue: libraryService},
        {provide: ShelfService, useValue: shelfService},
        {provide: TaskHelperService, useValue: taskHelperService},
        {provide: UserService, useValue: userService},
        {provide: Router, useValue: router},
        {provide: DialogLauncherService, useValue: dialogLauncherService},
        {provide: MagicShelfService, useValue: magicShelfService},
        {provide: LoadingService, useValue: loadingService},
        {provide: BookDialogHelperService, useValue: bookDialogHelperService},
        {provide: TranslocoService, useValue: translocoService},
      ],
    });

    service = TestBed.inject(LibraryShelfMenuService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();

    confirmationService.confirm.mockClear();
    messageService.add.mockClear();
    libraryService.refreshLibrary.mockClear();
    libraryService.deleteLibrary.mockClear();
    shelfService.deleteShelf.mockClear();
    taskHelperService.refreshMetadataTask.mockClear();
    userService.getCurrentUser.mockClear();
    router.navigate.mockClear();
    dialogLauncherService.openLibraryEditDialog.mockClear();
    dialogLauncherService.openShelfEditDialog.mockClear();
    dialogLauncherService.openMagicShelfEditDialog.mockClear();
    magicShelfService.deleteShelf.mockClear();
    loadingService.show.mockClear();
    loadingService.hide.mockClear();
    bookDialogHelperService.openAddPhysicalBookDialog.mockClear();
    bookDialogHelperService.openBulkIsbnImportDialog.mockClear();
    bookDialogHelperService.openDuplicateMergerDialog.mockClear();
    bookDialogHelperService.openMetadataRefreshDialogWithContext.mockClear();
    translocoService.translate.mockClear();
  });

  it('disables and guards library actions when the library id is missing', () => {
    const items = service.initializeLibraryMenuItems(buildLibrary({id: undefined}));

    const actionLabels = [
      'book.shelfMenuService.library.addPhysicalBook',
      'book.shelfMenuService.library.bulkIsbnImport',
      'book.shelfMenuService.library.editLibrary',
      'book.shelfMenuService.library.rescanLibrary',
      'book.shelfMenuService.library.customFetchMetadata',
      'book.shelfMenuService.library.autoFetchMetadata',
      'book.shelfMenuService.library.findDuplicates',
      'book.shelfMenuService.library.deleteLibrary',
    ];

    for (const label of actionLabels) {
      const item = findMenuItem(items, label);
      expect(item.disabled).toBe(true);
      runCommand(item);
    }

    expect(bookDialogHelperService.openAddPhysicalBookDialog).not.toHaveBeenCalled();
    expect(bookDialogHelperService.openBulkIsbnImportDialog).not.toHaveBeenCalled();
    expect(bookDialogHelperService.openDuplicateMergerDialog).not.toHaveBeenCalled();
    expect(dialogLauncherService.openLibraryEditDialog).not.toHaveBeenCalled();
    expect(bookDialogHelperService.openMetadataRefreshDialogWithContext).not.toHaveBeenCalled();
    expect(taskHelperService.refreshMetadataTask).not.toHaveBeenCalled();
    expect(confirmationService.confirm).not.toHaveBeenCalled();
  });

  it('passes the concrete library id into library actions when available', () => {
    const items = service.initializeLibraryMenuItems(buildLibrary({id: 42}));

    runCommand(findMenuItem(items, 'book.shelfMenuService.library.addPhysicalBook'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.bulkIsbnImport'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.editLibrary'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.customFetchMetadata'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.autoFetchMetadata'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.findDuplicates'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.rescanLibrary'));
    runCommand(findMenuItem(items, 'book.shelfMenuService.library.deleteLibrary'));

    expect(bookDialogHelperService.openAddPhysicalBookDialog).toHaveBeenCalledWith(42);
    expect(bookDialogHelperService.openBulkIsbnImportDialog).toHaveBeenCalledWith(42);
    expect(dialogLauncherService.openLibraryEditDialog).toHaveBeenCalledWith(42);
    expect(bookDialogHelperService.openMetadataRefreshDialogWithContext).toHaveBeenCalledWith({
      metadataRefreshType: MetadataRefreshType.LIBRARY,
      libraryId: 42
    });
    expect(taskHelperService.refreshMetadataTask).toHaveBeenCalledWith({
      refreshType: MetadataRefreshType.LIBRARY,
      libraryId: 42,
    });
    expect(bookDialogHelperService.openDuplicateMergerDialog).toHaveBeenCalledWith(42);
    expect(confirmationService.confirm).toHaveBeenCalledTimes(2);
  });

  it('guards shelf actions when the shelf id is missing even for the owner', () => {
    userService.getCurrentUser.mockReturnValue({id: 3, permissions: {admin: false}});
    const items = service.initializeShelfMenuItems(buildShelf({id: undefined, userId: 3}));

    const editItem = findMenuItem(items, 'book.shelfMenuService.shelf.editShelf');
    const deleteItem = findMenuItem(items, 'book.shelfMenuService.shelf.deleteShelf');

    expect(editItem.disabled).toBe(true);
    expect(deleteItem.disabled).toBe(true);

    runCommand(editItem);
    runCommand(deleteItem);

    expect(dialogLauncherService.openShelfEditDialog).not.toHaveBeenCalled();
    expect(confirmationService.confirm).not.toHaveBeenCalled();
  });

  it('guards magic shelf actions when the shelf id is missing', () => {
    const items = service.initializeMagicShelfMenuItems(buildMagicShelf({id: null}));

    const editItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.editMagicShelf');
    const deleteItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.deleteMagicShelf');

    expect(editItem.disabled).toBe(true);
    expect(deleteItem.disabled).toBe(true);

    runCommand(editItem);
    runCommand(deleteItem);

    expect(dialogLauncherService.openMagicShelfEditDialog).not.toHaveBeenCalled();
    expect(confirmationService.confirm).not.toHaveBeenCalled();
  });
});
