import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness, flushQueryAsync} from '../../../core/testing/query-testing';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {type BrowseSelection, type BrowseSelectionState} from '../../../shared/browse/selection';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {UserService} from '../../settings/user-management/user.service';
import {type BookSummary} from '../data/book-response.models';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {BookDialogHelperService} from '../service/book-dialog-helper.service';
import {BookBrowseBulkBarComponent} from './book-browse-bulk-bar.component';

function selectionOf(ids: readonly number[]): BrowseSelection {
  const selected = new Set(ids);
  return {
    state: signal<BrowseSelectionState>({mode: 'explicit', ids: selected}),
    count: signal(ids.length),
    active: signal(true),
    allMatchingSelected: signal(false),
    isSelected: id => selected.has(id),
    toggle: vi.fn(),
    selectAll: vi.fn(),
    clear: vi.fn(),
    pruneDeleted: vi.fn(),
  };
}

function book(id: number, libraryId = 1): BookSummary {
  return {id, libraryId, libraryName: `Library ${libraryId}`};
}

function user(permissions: Record<string, boolean>, id = 1) {
  return {id, permissions};
}

describe('BookBrowseBulkBarComponent', () => {
  let fixture: ComponentFixture<BookBrowseBulkBarComponent>;
  let currentUser: WritableSignal<ReturnType<typeof user> | null>;
  let appSettings: WritableSignal<{diskType: string}>;
  let shelfDefinitions: {id: number; userId: number; name: string}[];

  function mount(books: BookSummary[], selectedIds: number[]): BookBrowseBulkBarComponent {
    fixture = TestBed.createComponent(BookBrowseBulkBarComponent);
    fixture.componentRef.setInput('selection', selectionOf(selectedIds));
    fixture.componentRef.setInput('books', books);
    fixture.componentRef.setInput('total', books.length);
    fixture.componentRef.setInput('fetchIds', () => Promise.resolve([]));
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    const harness = createQueryClientHarness();
    currentUser = signal(null);
    appSettings = signal({diskType: 'LOCAL'});
    shelfDefinitions = [];

    TestBed.configureTestingModule({
      imports: [BookBrowseBulkBarComponent, getTranslocoModule()],
      providers: [
        ...harness.providers,
        {provide: UserService, useValue: {currentUser}},
        {provide: AppSettingsService, useValue: {appSettings}},
        {
          provide: ShelfDefinitionQueryService,
          useValue: {
            definitions: () => ({
              queryKey: ['shelves', 'query', 'definitions'] as const,
              queryFn: () => Promise.resolve(shelfDefinitions),
            }),
          },
        },
        {provide: BookDialogHelperService, useValue: {}},
        {provide: ConfirmationService, useValue: {confirm: vi.fn()}},
        {provide: MessageService, useValue: {add: vi.fn()}},
      ],
    }).overrideComponent(BookBrowseBulkBarComponent, {
      set: {template: '', imports: []},
    });
  });

  it('allows only the metadata actions the user is permitted', () => {
    currentUser.set(user({canBulkLockUnlockMetadata: true}));
    const bar = mount([book(1)], [1]);

    expect(bar['bulkMetadataAvailable']()).toBe(true);
    expect(bar['bulkPermissions']()).toMatchObject({
      canLockUnlockMetadata: true,
      canAutoFetchMetadata: false,
      canCustomFetchMetadata: false,
      canRegenerateCover: false,
    });

    currentUser.set(user({
      canBulkAutoFetchMetadata: true,
      canBulkCustomFetchMetadata: true,
      canBulkRegenerateCover: true,
    }));

    expect(bar['bulkPermissions']()).toMatchObject({
      canLockUnlockMetadata: false,
      canAutoFetchMetadata: true,
      canCustomFetchMetadata: true,
      canRegenerateCover: true,
    });

    currentUser.set(user({}));
    expect(bar['bulkMetadataAvailable']()).toBe(false);
  });

  it('requires permission and one library to attach files, and local disk to organize files', () => {
    currentUser.set(user({canManageLibrary: true}));
    const bar = mount([book(1, 1), book(2, 2)], [1, 2]);
    expect(bar['bulkPermissions']().canAttachFiles).toBe(true);
    expect(bar['bulkAttachEligible']()).toBe(false);

    fixture.componentRef.setInput('books', [book(1), book(2)]);
    expect(bar['bulkAttachEligible']()).toBe(true);

    fixture.componentRef.setInput('books', [book(1)]);
    expect(bar['bulkAttachEligible']()).toBe(false);

    currentUser.set(user({canMoveOrganizeFiles: true}));
    expect(bar['bulkPermissions']().canAttachFiles).toBe(false);
    appSettings.set({diskType: 'S3'});
    expect(bar['bulkPermissions']().canOrganizeFiles).toBe(false);

    appSettings.set({diskType: 'LOCAL'});
    expect(bar['bulkPermissions']().canOrganizeFiles).toBe(true);

    currentUser.set(user({}));
    expect(bar['bulkPermissions']().canOrganizeFiles).toBe(false);
  });

  it('offers only the current user shelves for bulk assignment', async () => {
    currentUser.set(user({}, 7));
    shelfDefinitions = [
      {id: 5, userId: 7, name: 'Mine'},
      {id: 6, userId: 9, name: 'Shared by someone else'},
    ];
    const bar = mount([book(1)], [1]);
    await flushQueryAsync();

    expect(bar['bulkShelves']().map(shelf => shelf.id)).toEqual([5]);

    currentUser.set(user({}, 9));
    expect(bar['bulkShelves']().map(shelf => shelf.id)).toEqual([6]);
  });
});
