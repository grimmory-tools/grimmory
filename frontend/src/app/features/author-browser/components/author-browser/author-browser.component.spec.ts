import {signal, type WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {of, Subject, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness} from '../../../../core/testing/query-testing';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {RouteScrollPositionService} from '../../../../shared/service/route-scroll-position.service';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {ReadStatus, type Book} from '../../../book/model/book.model';
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
  let mergeAuthors: ReturnType<typeof vi.fn>;
  let refreshAuthors: ReturnType<typeof vi.fn>;
  let invalidateAuthors: ReturnType<typeof vi.fn>;
  let addMessage: ReturnType<typeof vi.fn>;
  let authorsState: WritableSignal<AuthorSummary[]>;
  let getBooks: ReturnType<typeof vi.fn>;
  let queryParams: {get: (key: string) => string | null};
  let component: AuthorBrowserComponent;

  beforeEach(() => {
    merge$ = new Subject<void>();
    refreshAuthors = vi.fn().mockResolvedValue(authors);
    mergeAuthors = vi.fn(() => merge$);
    invalidateAuthors = vi.fn();
    addMessage = vi.fn();
    authorsState = signal(authors);
    getBooks = vi.fn().mockResolvedValue([]);
    queryParams = {get: () => null};
    const harness = createQueryClientHarness();
    harness.queryClient.setDefaultOptions({queries: {retry: false}});
    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthorService, useValue: {allAuthors: authorsState, refreshAuthors, invalidateAuthors}},
        {provide: BookMetadataManageService, useValue: {mergeAuthors}},
        {provide: BookService, useValue: {booksQueryOptions: () => ({queryKey: ['test-books'], queryFn: getBooks})}},
        {provide: ConfirmationService, useValue: {}},
        {provide: MessageService, useValue: {add: addMessage}},
        {provide: PageTitleService, useValue: {setPageTitle: vi.fn()}},
        {provide: RouteScrollPositionService, useValue: {trackRoute: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
        {provide: Router, useValue: {navigate: vi.fn()}},
        {provide: ActivatedRoute, useValue: {snapshot: {queryParamMap: queryParams}}},
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

  it('shows and searches authors without requesting the complete book list', () => {
    expect(component.loading()).toBe(false);
    expect(getBooks).not.toHaveBeenCalled();

    component.onSearchChange('jane');
    expect(component.filteredAuthors().map(author => author.id)).toEqual([2, 1]);
    component.onSortChange('book-count');
    expect(getBooks).not.toHaveBeenCalled();
  });

  it('loads book data when an advanced sort is selected and applies it only after loading', async () => {
    let finish!: (books: Book[]) => void;
    getBooks.mockReturnValue(new Promise<Book[]>(resolve => finish = resolve));

    component.onSortChange('reading-progress');
    TestBed.tick();
    expect(getBooks).toHaveBeenCalledOnce();
    expect(component.loading()).toBe(true);

    finish([{
      id: 10,
      libraryId: 1,
      libraryName: 'Library',
      readStatus: ReadStatus.READ,
      metadata: {bookId: 10, authors: ['Jane Doe']},
    }]);
    await vi.waitFor(() => {
      TestBed.tick();
      expect(component.loading()).toBe(false);
    });

    expect(component.filteredAuthors()[0].id).toBe(1);
    expect(component.filteredAuthors()[0].readingProgress).toBe(100);
  });

  it('loads book data for a saved advanced sort', () => {
    queryParams.get = key => key === 'sort' ? 'series-count' : null;
    component.ngOnInit();
    TestBed.tick();

    expect(getBooks).toHaveBeenCalledOnce();
  });

  it('loads book data for the filter options and applies library and genre filters', async () => {
    getBooks.mockResolvedValue([{
      id: 10,
      libraryId: 1,
      libraryName: 'Library',
      metadata: {bookId: 10, authors: ['Jane Doe'], categories: ['Fantasy']},
    }]);
    component.requestBookDetails();
    await vi.waitFor(() => {
      TestBed.tick();
      expect(component.bookDetailsAvailable()).toBe(true);
    });

    component.onFilterChange('library', 'Library');
    component.onFilterChange('genre', 'Fantasy');
    expect(component.filteredAuthors().map(author => author.id)).toEqual([1]);
  });

  it('reports an advanced-data failure and can retry or return to basic authors', async () => {
    getBooks.mockRejectedValueOnce(new Error('network error')).mockResolvedValue([]);
    component.onSortChange('recently-added');
    await vi.waitFor(() => {
      TestBed.tick();
      expect(component.bookDetailsError()).toBe(true);
    });
    expect(component.loading()).toBe(false);
    expect(component.bookDetailsRequired()).toBe(true);

    component.retryBookDetails();
    await vi.waitFor(() => {
      TestBed.tick();
      expect(component.bookDetailsAvailable()).toBe(true);
    });

    component.returnToBasicAuthors();
    expect(component.sortBy()).toBe('name');
    expect(component.bookDetailsRequired()).toBe(false);
  });

  it('can return to basic authors without retrying a failed advanced sort', async () => {
    getBooks.mockRejectedValue(new Error('network error'));
    component.onSortChange('avg-rating');
    await vi.waitFor(() => {
      TestBed.tick();
      expect(component.bookDetailsError()).toBe(true);
    });

    component.returnToBasicAuthors();
    expect(component.loading()).toBe(false);
    expect(component.filteredAuthors()).toHaveLength(authors.length);
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

  it('only queues groups after an explicit target and allows reviewing the choice', () => {
    component.duplicateGroups.set([authors.slice(0, 2)]);
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.planMergeAuthors();
    expect(component.plannedMerges()).toHaveLength(0);

    component.mergeTargetAuthorId.set(1);
    component.planMergeAuthors();
    expect(component.plannedMerges()).toHaveLength(1);
    expect(component.isGroupPlanned(authors.slice(0, 2))).toBe(true);
    expect(mergeAuthors).not.toHaveBeenCalled();
    expect(component.duplicateDialogVisible()).toBe(true);

    component.reviewDuplicateGroup(authors.slice(0, 2));
    expect(component.mergeTargetAuthorId()).toBe(1);
    component.mergeTargetAuthorId.set(2);
    component.planMergeAuthors();
    expect(component.plannedMerges()[0].target.id).toBe(2);
    expect(component.plannedMerges()[0].sources.map(author => author.id)).toEqual([1]);
    component.removePlannedMerge(component.plannedMerges()[0].groupKey);
    expect(component.isGroupPlanned(authors.slice(0, 2))).toBe(false);
  });

  it('does not queue unchecked authors from a suggested group', () => {
    const group = authors.slice(0, 3);
    component.reviewDuplicateGroup(group);
    component.toggleMergeCandidate(3, false);
    component.mergeTargetAuthorId.set(1);
    component.planMergeAuthors();

    expect(component.plannedMerges()[0].sources.map(author => author.id)).toEqual([2]);
    component.reviewDuplicateGroup(group);
    expect(component.includedMergeIds()).toEqual(new Set([1, 2]));
  });

  it('confirms and processes only reviewed groups sequentially', async () => {
    component.duplicateGroups.set([authors.slice(0, 2), authors.slice(2)]);
    for (const [group, target] of [[authors.slice(0, 2), 1], [authors.slice(2), 4]] as const) {
      component.reviewDuplicateGroup(group);
      component.mergeTargetAuthorId.set(target);
      component.planMergeAuthors();
    }
    component.confirmPlannedMerges();
    expect(component.batchConfirmVisible()).toBe(true);
    expect(mergeAuthors).not.toHaveBeenCalled();
    mergeAuthors.mockReturnValue(of(undefined));
    refreshAuthors.mockResolvedValueOnce(authors).mockResolvedValueOnce([authors[0], authors[3]]);

    await component.mergePlannedAuthors();

    expect(mergeAuthors.mock.calls).toEqual([[1, [2]], [4, [3]]]);
    expect(component.plannedMerges()).toEqual([]);
    expect(component.duplicateGroups()).toEqual([]);
    expect(component.batchProgress()).toBeNull();
    expect(refreshAuthors).toHaveBeenCalledTimes(2);
  });

  it('stops after a failure and preserves only the unprocessed groups', async () => {
    const additional: AuthorSummary[] = [
      {id: 5, name: 'Robert Brown', bookCount: 1, hasPhoto: false},
      {id: 6, name: 'Brown, Robert', bookCount: 1, hasPhoto: false},
    ];
    const allAuthors = [...authors, ...additional];
    authorsState.set(allAuthors);
    TestBed.tick();
    component.duplicateGroups.set([authors.slice(0, 2), authors.slice(2), additional]);
    for (const [group, target] of [[authors.slice(0, 2), 1], [authors.slice(2), 4], [additional, 5]] as const) {
      component.reviewDuplicateGroup(group);
      component.mergeTargetAuthorId.set(target);
      component.planMergeAuthors();
    }
    refreshAuthors.mockResolvedValueOnce(allAuthors).mockResolvedValueOnce([authors[0], ...authors.slice(2), ...additional]);
    mergeAuthors.mockReturnValueOnce(of(undefined)).mockReturnValueOnce(throwError(() => new Error('failed')));
    component.confirmPlannedMerges();

    await component.mergePlannedAuthors();

    expect(mergeAuthors.mock.calls).toEqual([[1, [2]], [4, [3]]]);
    expect(component.plannedMerges().map(plan => plan.target.id)).toEqual([5]);
    expect(addMessage).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error', summary: 'authorBrowser.duplicates.batchFailureSummary',
    }));
    expect(refreshAuthors).toHaveBeenCalledTimes(2);
  });

  it('refuses to merge if the server data changed after review', async () => {
    component.duplicateGroups.set([authors.slice(0, 2)]);
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.mergeTargetAuthorId.set(1);
    component.planMergeAuthors();
    component.confirmPlannedMerges();
    refreshAuthors.mockResolvedValueOnce([{...authors[0], name: 'Another Person'}, ...authors.slice(1)]);

    await component.mergePlannedAuthors();

    expect(mergeAuthors).not.toHaveBeenCalled();
    expect(component.plannedMerges()).toHaveLength(1);
    expect(component.batchProgress()).toBeNull();
    expect(addMessage).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error', summary: 'authorBrowser.duplicates.staleSummary',
    }));
  });

  it('does not start merges if the preflight refresh fails', async () => {
    component.duplicateGroups.set([authors.slice(0, 2)]);
    component.reviewDuplicateGroup(authors.slice(0, 2));
    component.mergeTargetAuthorId.set(1);
    component.planMergeAuthors();
    component.confirmPlannedMerges();
    refreshAuthors.mockRejectedValueOnce(new Error('network error'));

    await component.mergePlannedAuthors();

    expect(mergeAuthors).not.toHaveBeenCalled();
    expect(component.plannedMerges()).toHaveLength(1);
    expect(component.batchProgress()).toBeNull();
    expect(addMessage).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error', detail: 'authorBrowser.duplicates.preflightFailedDetail',
    }));
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
