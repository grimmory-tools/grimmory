import {ChangeDetectorRef, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {TranslocoService} from '@jsverse/transloco';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {Book, DuplicateGroup} from '../../model/book.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {BookFileService} from '../../service/book-file.service';
import {BookService} from '../../service/book.service';
import {DuplicateMergerComponent} from './duplicate-merger.component';

const createBook = (id: number): Book => ({id, libraryId: 1, libraryName: 'Main Library'});

const createGroup = (books: Book[]): DuplicateGroup => ({
  suggestedTargetBookId: books[0].id,
  matchReason: 'TITLE_AUTHOR',
  books,
});

describe('DuplicateMergerComponent', () => {
  const changeDetectorRef = {markForCheck: vi.fn()};
  const dialogConfig = {data: {libraryId: 1}};
  const bookFileService = {findDuplicates: vi.fn()};
  const bookService = {deleteBooks: vi.fn()};
  const confirmationService = {confirm: vi.fn()};
  const messageService = {add: vi.fn()};
  const translocoService = {translate: vi.fn((key: string) => key)};
  const urlHelper = {getThumbnailUrl: vi.fn()};
  const appSettingsService = {appSettings: signal(null)};

  let component: DuplicateMergerComponent;

  beforeEach(() => {
    vi.restoreAllMocks();
    appSettingsService.appSettings.set(null);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: ChangeDetectorRef, useValue: changeDetectorRef},
        {provide: DynamicDialogConfig, useValue: dialogConfig},
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: BookFileService, useValue: bookFileService},
        {provide: BookService, useValue: bookService},
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
        {provide: UrlHelperService, useValue: urlHelper},
        {provide: AppSettingsService, useValue: appSettingsService},
      ]
    });

    component = TestBed.runInInjectionContext(() => new DuplicateMergerComponent());
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('removes a deleted book from its group, dismisses the group, and notifies change detection', () => {
    const deletedBookId = 2;

    bookFileService.findDuplicates.mockReturnValue(of([
      createGroup([createBook(1), createBook(2)]),
    ]));
    component.ngOnInit();
    component.scan();

    const group = component.groups[0];
    component.toggleDeleteSelection(group, deletedBookId);

    bookService.deleteBooks.mockReturnValue(of({deleted: [deletedBookId], failedFileDeletions: []}));
    confirmationService.confirm.mockImplementation(config => config.accept());

    component.deleteGroup(group);

    expect(group.books.map(book => book.id)).toEqual([1]);
    expect(group.dismissed).toBe(true);
    expect(changeDetectorRef.markForCheck).toHaveBeenCalledOnce();
  });
});
