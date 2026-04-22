import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DialogService} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {DashboardSettingsComponent} from '../../features/dashboard/components/dashboard-settings/dashboard-settings.component';
import {LibraryCreatorComponent} from '../../features/library-creator/library-creator.component';
import {DialogLauncherService, DialogSize, DialogStyle} from './dialog-launcher.service';

describe('DialogLauncherService', () => {
  const dialogRef = {close: vi.fn()};
  const dialogService = {
    open: vi.fn(() => dialogRef),
  };
  const messageService = {add: vi.fn()};
  const translocoService = {translate: vi.fn((key: string) => key)};

  let service: DialogLauncherService;

  beforeEach(() => {
    vi.restoreAllMocks();
    dialogService.open.mockClear();
    messageService.add.mockClear();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        DialogLauncherService,
        {provide: DialogService, useValue: dialogService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    service = TestBed.inject(DialogLauncherService);
  });

  it('merges the default dialog options with caller overrides', () => {
    service.openDialog(LibraryCreatorComponent, {
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

  it('opens the dashboard settings dialog with the expected style class', async () => {
    await service.openDashboardSettingsDialog();

    expect(dialogService.open).toHaveBeenCalledWith(
      DashboardSettingsComponent,
      expect.objectContaining({
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
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

  it('deduplicates concurrent lazy opens with the same key', async () => {
    const [first, second] = await Promise.all([
      service.openDashboardSettingsDialog(),
      service.openDashboardSettingsDialog(),
    ]);

    expect(dialogService.open).toHaveBeenCalledTimes(1);
    expect(first).toBe(second);
  });

  it('opens independent dialogs for distinct keys', async () => {
    await Promise.all([
      service.openLibraryEditDialog(1),
      service.openLibraryEditDialog(2),
    ]);

    expect(dialogService.open).toHaveBeenCalledTimes(2);
  });
});
