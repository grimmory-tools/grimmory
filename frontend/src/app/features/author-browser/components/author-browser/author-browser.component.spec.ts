import {signal, type WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness} from '../../../../core/testing/query-testing';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {RouteScrollPositionService} from '../../../../shared/service/route-scroll-position.service';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {BookService} from '../../../book/service/book.service';
import {UserService} from '../../../settings/user-management/user.service';
import {AuthorSummary} from '../../model/author.model';
import {AuthorSelectionService} from '../../service/author-selection.service';
import {AuthorService} from '../../service/author.service';
import {AuthorBrowserComponent} from './author-browser.component';

describe('AuthorBrowserComponent duplicate review', () => {
  const authors: AuthorSummary[] = [
    {id: 1, name: 'Jane Doe', bookCount: 2, hasPhoto: false},
    {id: 2, name: 'Doe, Jane', bookCount: 1, hasPhoto: false},
    {id: 3, name: 'John Smith', bookCount: 1, hasPhoto: false},
    {id: 4, name: 'Smith, John', bookCount: 2, hasPhoto: false},
  ];
  let merge$: Subject<void>;
  let refreshAuthors: ReturnType<typeof vi.fn>;
  let invalidateAuthors: ReturnType<typeof vi.fn>;
  let addMessage: ReturnType<typeof vi.fn>;
  let authorsState: WritableSignal<AuthorSummary[]>;
  let component: AuthorBrowserComponent;

  beforeEach(() => {
    merge$ = new Subject<void>();
    refreshAuthors = vi.fn();
    invalidateAuthors = vi.fn();
    addMessage = vi.fn();
    authorsState = signal(authors);
    const harness = createQueryClientHarness();
    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthorService, useValue: {allAuthors: authorsState, refreshAuthors, invalidateAuthors}},
        {provide: BookMetadataManageService, useValue: {mergeAuthors: vi.fn(() => merge$)}},
        {provide: BookService, useValue: {booksQueryOptions: () => ({queryKey: ['test-books'], queryFn: () => Promise.resolve([]), enabled: false})}},
        {provide: ConfirmationService, useValue: {}},
        {provide: MessageService, useValue: {add: addMessage}},
        {provide: PageTitleService, useValue: {}},
        {provide: RouteScrollPositionService, useValue: {}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
        {provide: Router, useValue: {}},
        {provide: ActivatedRoute, useValue: {}},
        {provide: LocalStorageService, useValue: {get: () => null}},
        {provide: LayoutService, useValue: {isDesktop: () => true}},
        {provide: UserService, useValue: {currentUser: signal(null)}},
        {provide: AuthorSelectionService, useValue: {
          selectedAuthors: signal(new Set<number>()),
          setCurrentAuthors: vi.fn(),
          deselectAll: vi.fn(),
          getSelectedIds: () => [1, 2],
        }},
      ],
    });
    component = TestBed.runInInjectionContext(() => new AuthorBrowserComponent());
    TestBed.tick();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('returns to duplicate results on cancel, but not for a manual selection', () => {
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.closeMergeAuthorsDialog();

    expect(component.duplicateDialogVisible()).toBe(true);

    component.duplicateDialogVisible.set(false);
    component.openMergeAuthorsDialog();
    component.closeMergeAuthorsDialog();

    expect(component.duplicateDialogVisible()).toBe(false);
  });

  it('refreshes duplicate groups from the server after a successful merge', async () => {
    let finishRefresh!: (value: AuthorSummary[]) => void;
    refreshAuthors.mockReturnValue(new Promise<AuthorSummary[]>(resolve => finishRefresh = resolve));
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.mergeTargetAuthorId.set(1);
    component.confirmMergeAuthors();
    merge$.next();

    expect(component.mergeAuthorsDialogVisible()).toBe(false);
    expect(component.duplicateDialogVisible()).toBe(true);
    expect(component.findingDuplicates()).toBe(true);

    const updatedAuthor = {...authors[2], bookCount: 5};
    finishRefresh([{...authors[0], bookCount: 3}, updatedAuthor, authors[3]]);
    await vi.waitFor(() => expect(component.findingDuplicates()).toBe(false));

    expect(component.duplicateGroups()).toEqual([[updatedAuthor, authors[3]]]);
    expect(refreshAuthors).toHaveBeenCalledOnce();
    expect(invalidateAuthors).not.toHaveBeenCalled();
  });

  it('keeps remaining local results and reports a failed refresh', async () => {
    let failRefresh!: (error: Error) => void;
    refreshAuthors.mockReturnValue(new Promise<AuthorSummary[]>((_, reject) => failRefresh = reject));
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.mergeTargetAuthorId.set(1);
    component.confirmMergeAuthors();
    merge$.next();
    authorsState.set([...authors]);
    TestBed.tick();
    failRefresh(new Error('network error'));

    await vi.waitFor(() => expect(component.findingDuplicates()).toBe(false));

    expect(component.duplicateDialogVisible()).toBe(true);
    expect(component.duplicateGroups()).toEqual([[authors[2], authors[3]]]);
    expect(invalidateAuthors).toHaveBeenCalledOnce();
    expect(addMessage).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      summary: 'authorBrowser.duplicates.refreshFailedSummary',
    }));
  });

  it('keeps the merge dialog open when the merge fails', () => {
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.mergeTargetAuthorId.set(1);
    component.confirmMergeAuthors();
    merge$.error(new Error('merge failed'));

    expect(component.mergeAuthorsDialogVisible()).toBe(true);
    expect(component.duplicateDialogVisible()).toBe(false);
    expect(component.mergingAuthors()).toBe(false);
    expect(refreshAuthors).not.toHaveBeenCalled();
  });
});

// NOTE(frontend-seam): Real coverage here needs seams around signal/effect synchronization,
// virtual-scroller behavior, router/route query-param state, and cross-service selection logic so
// author browsing can be tested without a full browser shell harness.
describe.skip('AuthorBrowserComponent', () => {
  it('needs state-sync seams to verify author enrichment, filtering, sorting, and active-filter counting', () => {
    // TODO(seam): Cover enrichAuthors, applyFilters, and applySort once the reactive service graph is isolated for deterministic assertions.
  });

  it('needs browser-shell seams to verify selection, scroll restoration, route state, and thumbnail cache-busting behavior', () => {
    // TODO(seam): Cover the virtual-scroller and router orchestration after extracting those concerns behind testable adapters.
  });
});
