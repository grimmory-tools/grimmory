import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {LoadingService} from '../../../core/services/loading.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {BookDialogHelperService} from '../components/book-browser/book-dialog-helper.service';
import {LibraryService} from './library.service';
import {LibraryShelfMenuService} from './library-shelf-menu.service';
import {ShelfService} from './shelf.service';

describe('LibraryShelfMenuService', () => {
  let service: LibraryShelfMenuService;

  const confirmationService = {confirm: vi.fn()};
  const messageService = {add: vi.fn()};
  const libraryService = {
    refreshLibrary: vi.fn(() => of(undefined)),
    deleteLibrary: vi.fn(() => of(undefined)),
  };
  const shelfService = {deleteShelf: vi.fn(() => of(undefined))};
  const magicShelfService = {deleteShelf: vi.fn(() => of(undefined))};
  const taskHelperService = {refreshMetadataTask: vi.fn(() => of(undefined))};
  const router = {url: '/', navigate: vi.fn(() => Promise.resolve(true))};
  const dialogLauncherService = {
    openLibraryEditDialog: vi.fn(() => Promise.resolve(null)),
    openShelfEditDialog: vi.fn(() => Promise.resolve(null)),
    openMagicShelfEditDialog: vi.fn(() => Promise.resolve(null)),
  };
  const loadingService = {show: vi.fn(() => 'loader-token'), hide: vi.fn()};
  const bookDialogHelperService = {
    openAddPhysicalBookDialog: vi.fn(() => Promise.resolve(null)),
    openBulkIsbnImportDialog: vi.fn(() => Promise.resolve(null)),
    openDuplicateMergerDialog: vi.fn(() => Promise.resolve(null)),
    openMetadataRefreshDialogWithContext: vi.fn(() => Promise.resolve(null)),
  };
  const translocoService = {translate: vi.fn((key: string) => key)};

  beforeEach(() => {
    router.url = '/';
    TestBed.configureTestingModule({
      providers: [
        LibraryShelfMenuService,
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: LibraryService, useValue: libraryService},
        {provide: ShelfService, useValue: shelfService},
        {provide: MagicShelfService, useValue: magicShelfService},
        {provide: TaskHelperService, useValue: taskHelperService},
        {provide: Router, useValue: router},
        {provide: DialogLauncherService, useValue: dialogLauncherService},
        {provide: LoadingService, useValue: loadingService},
        {provide: BookDialogHelperService, useValue: bookDialogHelperService},
        {provide: TranslocoService, useValue: translocoService},
      ],
    });
    service = TestBed.inject(LibraryShelfMenuService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('runs accepted library confirmation actions with the resolved name', () => {
    service.rescanLibrary({id: 7, name: 'Main Library'});
    expect(confirmationService.confirm.mock.calls[0][0].message)
      .toBe('book.shelfMenuService.confirm.rescanLibraryMessage');
    confirmationService.confirm.mock.calls[0][0].accept();
    expect(libraryService.refreshLibrary).toHaveBeenCalledWith(7);

    service.deleteLibrary({id: 7, name: 'Main Library'});
    confirmationService.confirm.mock.calls[1][0].accept();
    expect(libraryService.deleteLibrary).toHaveBeenCalledWith(7);
    expect(loadingService.hide).toHaveBeenCalledWith('loader-token');
    expect(router.navigate).not.toHaveBeenCalled();

    router.url = '/library/7/books?sort=title#results';
    service.deleteLibrary({id: 7, name: 'Main Library'});
    confirmationService.confirm.mock.calls[2][0].accept();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('runs shelf and magic-shelf actions', () => {
    service.editShelf(11);
    service.deleteShelf({id: 11, name: 'Favorites'});
    service.editMagicShelf(13);
    service.deleteMagicShelf({id: 13, name: 'Magic Shelf'});

    expect(dialogLauncherService.openShelfEditDialog).toHaveBeenCalledWith(11);
    expect(dialogLauncherService.openMagicShelfEditDialog).toHaveBeenCalledWith(13);
    expect(confirmationService.confirm).toHaveBeenCalledTimes(2);

    confirmationService.confirm.mock.calls[0][0].accept();
    confirmationService.confirm.mock.calls[1][0].accept();
    expect(shelfService.deleteShelf).toHaveBeenCalledWith(11);
    expect(magicShelfService.deleteShelf).toHaveBeenCalledWith(13);
    expect(router.navigate).not.toHaveBeenCalled();

    router.url = '/shelf/11/books?sort=title#results';
    service.deleteShelf({id: 11, name: 'Favorites'});
    confirmationService.confirm.mock.calls[2][0].accept();

    router.url = '/magic-shelf/13/books?sort=title#results';
    service.deleteMagicShelf({id: 13, name: 'Magic Shelf'});
    confirmationService.confirm.mock.calls[3][0].accept();
    expect(router.navigate).toHaveBeenNthCalledWith(1, ['/']);
    expect(router.navigate).toHaveBeenNthCalledWith(2, ['/']);
  });

  it('copies magic-shelf JSON', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    vi.stubGlobal('navigator', {clipboard: {writeText}});

    service.copyMagicShelfJson('{"rule":true}');
    await Promise.resolve();

    expect(writeText).toHaveBeenCalledWith('{"rule":true}');
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

});
