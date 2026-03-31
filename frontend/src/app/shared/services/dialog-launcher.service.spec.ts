import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {DialogService} from 'primeng/dynamicdialog';

import {DashboardSettingsComponent} from '../../features/dashboard/components/dashboard-settings/dashboard-settings.component';
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
      providers: [
        DialogLauncherService,
        {provide: DialogService, useValue: dialogService},
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

  it('opens the dashboard settings dialog with the expected style class', () => {
    service.openDashboardSettingsDialog();

    expect(dialogService.open).toHaveBeenCalledWith(
      DashboardSettingsComponent,
      expect.objectContaining({
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      })
    );
  });

  it('passes the library id into the library edit dialog', () => {
    service.openLibraryEditDialog(12);

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
