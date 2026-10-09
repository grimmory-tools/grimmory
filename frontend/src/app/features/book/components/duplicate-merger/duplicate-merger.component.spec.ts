import {TestBed} from '@angular/core/testing';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {TranslocoService} from '@jsverse/transloco';
import * as TanStack from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {DeleteBooksPartialError} from '../../data/book-command.models';
import {BookCommandService} from '../../data/book-command.service';
import {BookQueryService} from '../../data/book-query.service';
import {BookDetail} from '../../data/book-response.models';
import {DuplicateMergerComponent} from './duplicate-merger.component';

vi.mock('@tanstack/angular-query-experimental', {spy: true});

const book = (id: number): BookDetail => ({id, libraryId: 1, libraryName: 'Library'});
const group = (...bookIds: number[]) => ({
  suggestedTargetBookId: bookIds[0],
  selectedTargetBookId: bookIds[0],
  selectedForDeletion: new Set<number>(),
  matchReason: 'TITLE_AUTHOR',
  books: bookIds.map(book),
});

describe('DuplicateMergerComponent', () => {
  const confirmationService = {confirm: vi.fn()};
  const attach = vi.fn();
  const remove = vi.fn();
  let component: DuplicateMergerComponent;

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(TanStack.injectMutation).mockImplementation(vi.fn()
      .mockReturnValueOnce({mutateAsync: attach})
      .mockReturnValueOnce({mutateAsync: remove, isPending: () => false}));
    TestBed.configureTestingModule({
      providers: [
        {provide: TanStack.QueryClient, useValue: {}},
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 1}}},
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: BookQueryService, useValue: {}},
        {provide: BookCommandService, useValue: {}},
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
        {provide: UrlHelperService, useValue: {}},
        {provide: AppSettingsService, useValue: {appSettings: () => null}},
      ],
    });
    component = TestBed.runInInjectionContext(() => new DuplicateMergerComponent());
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('keeps failed deletions visible and selected after a partial deletion', async () => {
    component.groups.set([{...group(1, 2, 3), selectedForDeletion: new Set([2, 3])}]);
    remove.mockRejectedValue(new DeleteBooksPartialError(
      {removedBookIds: [2], fileCleanupFailedBookIds: []}, [3], new Error('Book 3 could not be deleted'),
    ));
    confirmationService.confirm.mockImplementation(config => config.accept());

    component.deleteGroup(component.groups()[0]);
    await vi.waitFor(() => expect(component.groups()[0].books.map(current => current.id)).toEqual([1, 3]));
    expect(component.groups()[0].selectedForDeletion).toEqual(new Set([3]));
  });

  it('stops scheduling merges when the dialog closes during an in-flight merge', async () => {
    component.groups.set([group(1, 2), group(3, 4)]);
    let finishFirstMerge!: () => void;
    attach.mockReturnValueOnce(new Promise<void>(resolve => {finishFirstMerge = resolve;}));

    const merging = component.mergeGroups(component.groups(), true);
    component.closeDialog();
    finishFirstMerge();
    await merging;

    expect(attach).toHaveBeenCalledTimes(1);
  });
});
