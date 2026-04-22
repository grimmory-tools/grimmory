import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {BookDialogHelperService} from './book-dialog-helper.service';

describe('BookDialogHelperService', () => {
  let service: BookDialogHelperService;
  let dialogLauncherService: {openDialog: ReturnType<typeof vi.fn>};
  const messageService = {add: vi.fn()};
  const translocoService = {translate: vi.fn((key: string) => key)};

  beforeEach(() => {
    dialogLauncherService = {
      openDialog: vi.fn(() => ({id: 'dialog-ref'})),
    };
    messageService.add.mockClear();

    TestBed.configureTestingModule({
      providers: [
        BookDialogHelperService,
        {provide: DialogLauncherService, useValue: dialogLauncherService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
      ],
    });

    service = TestBed.inject(BookDialogHelperService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('opens the book details dialog with the expected payload', async () => {
    const dialogRef = await service.openBookDetailsDialog(42);

    expect(dialogLauncherService.openDialog).toHaveBeenCalledWith(
      expect.any(Function),
      expect.objectContaining({
        showHeader: false,
        data: {bookId: 42},
      })
    );
    expect(dialogRef).toEqual({id: 'dialog-ref'});
  });

  it('returns null instead of opening the shelf assigner when no inputs are provided', async () => {
    const dialogRef = await service.openShelfAssignerDialog(null, null);

    expect(dialogRef).toBeNull();
    expect(dialogLauncherService.openDialog).not.toHaveBeenCalled();
  });

  it('opens the bulk file attacher with the provided source books', async () => {
    const sourceBooks = [{id: 1}, {id: 2}] as never[];

    await service.openBulkBookFileAttacherDialog(sourceBooks);

    expect(dialogLauncherService.openDialog).toHaveBeenCalledWith(
      expect.any(Function),
      expect.objectContaining({
        showHeader: false,
        data: {sourceBooks},
      })
    );
  });
});
