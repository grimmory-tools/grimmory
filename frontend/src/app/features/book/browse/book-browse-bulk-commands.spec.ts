import {HttpTestingController} from '@angular/common/http/testing';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness, flushQueryAsync} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {API_CONFIG} from '../../../core/config/api-config';
import {BookDialogHelperService} from '../service/book-dialog-helper.service';
import {type BrowseSelection, type BrowseSelectionState} from '../../../shared/browse/selection';
import {createBookBrowseBulkCommands} from './book-browse-bulk-commands';

function selection(ids: readonly number[]): BrowseSelection {
  const selected = new Set(ids);
  return {
    state: signal<BrowseSelectionState>({mode: 'explicit', ids: selected}),
    count: signal(ids.length),
    active: signal(ids.length > 0),
    allMatchingSelected: signal(false),
    isSelected: id => selected.has(id),
    toggle: vi.fn(),
    selectAll: vi.fn(),
    clear: vi.fn(),
    pruneDeleted: vi.fn(),
  };
}

describe('createBookBrowseBulkCommands', () => {
  let http: HttpTestingController;
  let dialogHelper: Record<
    'openBulkMetadataEditDialog' | 'openMultibookMetadataEditorDialog' |
    'openLockUnlockMetadataDialog' | 'openFileMoverDialog' | 'openBulkBookFileAttacherDialog',
    ReturnType<typeof vi.fn>
  >;

  function commandsFor(selected: BrowseSelection) {
    return TestBed.runInInjectionContext(() =>
      createBookBrowseBulkCommands({
        selection: signal(selected),
        books: signal([]),
        fetchIds: signal(vi.fn()),
      }));
  }
  type Commands = ReturnType<typeof commandsFor>;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    dialogHelper = {
      openBulkMetadataEditDialog: vi.fn().mockResolvedValue(null),
      openMultibookMetadataEditorDialog: vi.fn().mockResolvedValue(null),
      openLockUnlockMetadataDialog: vi.fn().mockResolvedValue(null),
      openFileMoverDialog: vi.fn().mockResolvedValue(null),
      openBulkBookFileAttacherDialog: vi.fn().mockResolvedValue(null),
    };

    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        ...harness.providers,
        {provide: BookDialogHelperService, useValue: dialogHelper},
        {provide: ConfirmationService, useValue: {confirm: vi.fn()}},
        {provide: MessageService, useValue: {add: vi.fn()}},
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it.each([
    {name: 'bulk metadata editor', clears: true, dialog: () => dialogHelper.openBulkMetadataEditDialog,
      run: (commands: Commands) => commands.editAll()},
    {name: 'one-by-one metadata editor', clears: true, dialog: () => dialogHelper.openMultibookMetadataEditorDialog,
      run: (commands: Commands) => commands.editOneByOne()},
    {name: 'Lock/Unlock metadata dialog', clears: true, dialog: () => dialogHelper.openLockUnlockMetadataDialog,
      run: (commands: Commands) => commands.lockUnlockMetadata()},
    {name: 'file organizer', clears: false, dialog: () => dialogHelper.openFileMoverDialog,
      run: (commands: Commands) => commands.organizeFiles()},
  ])('opens the $name with resolved IDs', async ({clears, dialog, run}) => {
    const onClose = new Subject<void>();
    dialog().mockResolvedValue({onClose});
    const selected = selection([11, 12]);

    run(commandsFor(selected));

    await vi.waitFor(() => expect(dialog()).toHaveBeenCalledOnce());
    expect([...dialog().mock.calls[0][0]]).toEqual([11, 12]);
    onClose.next();
    expect(selected.clear).toHaveBeenCalledTimes(clears ? 1 : 0);
  });

  it('removes every supplied shelf in one membership mutation', async () => {
    commandsFor(selection([61, 62])).removeFromAllShelves([7, 8]);
    await flushQueryAsync(1);

    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/shelves`);
    expect(request.request.body).toEqual({
      bookIds: [61, 62],
      shelvesToAssign: [],
      shelvesToUnassign: [7, 8],
    });
    request.flush([]);
    await flushQueryAsync(1);
  });

});
