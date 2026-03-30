import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {BookDialogHelperService} from './book-dialog-helper.service';

describe('BookDialogHelperService', () => {
  let service: BookDialogHelperService;
  let dialogLauncherService: {openDialog: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    dialogLauncherService = {
      openDialog: vi.fn(() => ({id: 'dialog-ref'})),
    };

    TestBed.configureTestingModule({
      providers: [
        BookDialogHelperService,
        {provide: DialogLauncherService, useValue: dialogLauncherService},
      ],
    });

    service = TestBed.inject(BookDialogHelperService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('opens the book details dialog with the expected payload', () => {
    const dialogRef = service.openBookDetailsDialog(42);

    expect(dialogLauncherService.openDialog).toHaveBeenCalledWith(
      expect.any(Function),
      expect.objectContaining({
        showHeader: false,
        data: {bookId: 42},
      })
    );
    expect(dialogRef).toEqual({id: 'dialog-ref'});
  });

  it('returns null instead of opening the shelf assigner when no inputs are provided', () => {
    const dialogRef = service.openShelfAssignerDialog(null, null);

    expect(dialogRef).toBeNull();
    expect(dialogLauncherService.openDialog).not.toHaveBeenCalled();
  });

  it('opens the bulk file attacher with the provided source books', () => {
    const sourceBooks = [{id: 1}, {id: 2}] as never[];

    service.openBulkBookFileAttacherDialog(sourceBooks);

    expect(dialogLauncherService.openDialog).toHaveBeenCalledWith(
      expect.any(Function),
      expect.objectContaining({
        showHeader: false,
        data: {sourceBooks},
      })
    );
  });
});
