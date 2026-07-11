import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DialogService} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {getTranslocoModule} from '../../core/testing/transloco-testing';

import {LibraryCreatorComponent} from '../../features/library-creator/library-creator.component';
import {DialogLauncherService, DialogSize, DialogStyle} from './dialog-launcher.service';

describe('DialogLauncherService', () => {
  const dialogRef = {close: vi.fn()};
  const dialogService = {
    open: vi.fn(() => dialogRef),
  };

  let service: DialogLauncherService;

  beforeEach(() => {
    vi.restoreAllMocks();
    dialogService.open.mockClear();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        DialogLauncherService,
        {provide: DialogService, useValue: dialogService},
        {provide: MessageService, useValue: {add: vi.fn()}},
      ]
    });

    service = TestBed.inject(DialogLauncherService);
  });

  it('merges the default dialog options with caller overrides', async () => {
    await service.openDialog(LibraryCreatorComponent, {
      showHeader: false,
      data: {mode: 'create'},
    });

    expect(dialogService.open).toHaveBeenCalledWith(
      LibraryCreatorComponent,
      expect.objectContaining({
        baseZIndex: 10,
        closable: true,
        dismissableMask: true,
        draggable: false,
        modal: true,
        resizable: false,
        showHeader: false,
        maximizable: false,
        data: {mode: 'create'},
      })
    );
  });


  it('passes the library id into the library edit dialog', async () => {
    await service.openLibraryEditDialog(12);

    expect(dialogService.open).toHaveBeenCalledWith(
      LibraryCreatorComponent,
      expect.objectContaining({
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          mode: 'edit',
          libraryId: 12,
        },
      })
    );
  });
});
