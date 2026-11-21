import {Component, computed, DestroyRef, effect, ElementRef, HostListener, inject, OnInit, signal, untracked, viewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Select} from '@openng/optimus-ui/select';
import {Dialog} from '@openng/optimus-ui/dialog';
import {Popover} from '@openng/optimus-ui/popover';
import {Button} from '@openng/optimus-ui/button';
import {Divider} from '@openng/optimus-ui/divider';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {injectQuery} from '@tanstack/angular-query';
import {RouteScrollPositionService} from '../../../../shared/service/route-scroll-position.service';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {AuthorService} from '../../service/author.service';
import {AuthorSummary, AuthorSearchResult, AuthorDetails, EnrichedAuthor, AuthorFilters, DEFAULT_AUTHOR_FILTERS} from '../../model/author.model';
import {AuthorCardComponent} from '../author-card/author-card.component';
import {AuthorMatchValidationDialogComponent} from '../author-match-validation-dialog/author-match-validation-dialog.component';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {AuthorSelectionService, AuthorCheckboxClickEvent} from '../../service/author-selection.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {ActivatedRoute, Router} from '@angular/router';
import {UserService} from '../../../settings/user-management/user.service';
import {BookService} from '../../../book/service/book.service';
import {Book, ReadStatus} from '../../../book/model/book.model';
import {createVirtualGrid} from '../../../../shared/util/virtual-grid.util';
import {GridDensityButtonsComponent, type GridDensityDirection} from '../../../../shared/components/grid-density-buttons/grid-density-buttons.component';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {ScalePreference} from '../../../../shared/util/scale-preference.util';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {createGridDensity} from '../../../../shared/util/grid-density.util';
import {findAuthorDuplicates} from '../../author-duplicates';

type SortDirection = 'asc' | 'desc';

interface SortOption {
  label: string;
  value: string;
}

interface FilterOption {
  label: string;
  value: string;
}

const DEFAULT_SORT_DIRECTIONS: Record<string, SortDirection> = {
  'name': 'asc',
  'book-count': 'desc',
  'matched': 'desc',
  'recently-added': 'desc',
  'recently-read': 'desc',
  'reading-progress': 'desc',
  'avg-rating': 'desc',
  'photo': 'desc',
  'series-count': 'desc'
};

const BOOK_DEPENDENT_SORTS = new Set([
  'recently-added', 'recently-read', 'reading-progress', 'avg-rating', 'series-count'
]);

@Component({
  selector: 'app-author-browser',
  standalone: true,
  templateUrl: './author-browser.component.html',
  styleUrls: ['./author-browser.component.scss'],
  providers: [ConfirmationService],
  imports: [
    FormsModule,
    ProgressSpinner,
    InputText,
    Select,
    Dialog,
    Popover,
    Button,
    Divider,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe,
    GridDensityButtonsComponent,
    AuthorCardComponent,
    AuthorMatchValidationDialogComponent,
  ]
})
export class AuthorBrowserComponent implements OnInit {

  private static readonly BASE_WIDTH = 165;
  private static readonly BASE_HEIGHT = 290;
  private static readonly MOBILE_BASE_WIDTH = 140;
  private static readonly MOBILE_BASE_HEIGHT = 250;
  private static readonly DEFAULT_MOBILE_GRID_COLUMNS = 3;
  private static readonly MIN_MOBILE_GRID_COLUMNS = 2;
  private static readonly MAX_MOBILE_GRID_COLUMNS = 4;
  private static readonly SCALE_STORAGE_KEY = 'authorScalePreference';
  private static readonly MOBILE_COLUMNS_STORAGE_KEY = 'authorMobileColumnsPreference';
  private static readonly MIN_SCALE = 0.7;
  private static readonly MAX_SCALE = 1.3;

  private readonly authorService = inject(AuthorService);
  private readonly bookMetadataManageService = inject(BookMetadataManageService);
  private readonly bookService = inject(BookService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly scrollService = inject(RouteScrollPositionService);
  private readonly t = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly localStorageService = inject(LocalStorageService);
  private readonly layoutService = inject(LayoutService);
  protected userService = inject(UserService);
  protected selectionService = inject(AuthorSelectionService);

  constructor() {
    effect(() => {
      const authors = this.authorService.allAuthors();
      if (authors !== null) {
        this.allAuthorsState.set(authors);
        if (untracked(() => this.duplicateDialogVisible() && !this.findingDuplicates())) {
          this.duplicateGroups.set(findAuthorDuplicates(authors));
        }
      }
    });

    effect(() => {
      this.selectionService.setCurrentAuthors(this.filteredAuthors());
    });
  }

  private static readonly GRID_GAP = 20;
  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');
  private readonly initialScrollOffset = () => this.scrollService.getPosition(this.scrollService.keyFor(this.activatedRoute)) ?? 0;
  private readonly scalePreference = new ScalePreference(this.localStorageService, {
    storageKey: AuthorBrowserComponent.SCALE_STORAGE_KEY,
    minScale: AuthorBrowserComponent.MIN_SCALE,
    maxScale: AuthorBrowserComponent.MAX_SCALE,
  });
  private readonly scaleFactor = this.scalePreference.scaleFactor;

  readonly screenWidth = signal(typeof window !== 'undefined' ? window.innerWidth : 1024);
  thumbnailCacheBusters = new Map<number, number>();
  private readonly selectedAuthors = this.selectionService.selectedAuthors;
  private readonly allAuthorsState = signal<AuthorSummary[] | null>(null);
  readonly deletingAuthorsWithoutBooks = signal(false);
  readonly pendingMatch = signal<{author: AuthorSummary; candidates: AuthorSearchResult[]} | null>(null);
  readonly applyingPendingMatch = signal(false);
  readonly autoMatchInProgress = signal(false);
  readonly autoMatchProgress = signal({processed: 0, total: 0});
  readonly mergeCandidates = signal<AuthorSummary[]>([]);
  readonly mergeAuthorsDialogVisible = signal(false);
  readonly mergeTargetAuthorId = signal<number | null>(null);
  readonly mergingAuthors = signal(false);
  readonly duplicateGroups = signal<AuthorSummary[][]>([]);
  readonly duplicateDialogVisible = signal(false);
  readonly findingDuplicates = signal(false);
  readonly duplicateLimit = signal(50);
  readonly includedMergeIds = signal<Set<number>>(new Set());
  readonly includedMergeCandidates = computed(() =>
    this.mergeCandidates()
      .filter(author => this.includedMergeIds().has(author.id))
      .map(author => ({...author, label: `${author.name} (#${author.id})`}))
  );
  private autoMatchQueue: AuthorSummary[] = [];
  private autoMatchStats = {matched: 0, ambiguous: 0, noResults: 0, failed: 0};
  private returnToDuplicates = false;

  private readonly bookDetailsRequested = signal(false);
  private readonly booksQuery = injectQuery(() => ({
    ...this.bookService.booksQueryOptions(true),
    enabled: this.bookDetailsRequested(),
  }));
  readonly bookDetailsAvailable = computed(() => this.booksQuery.data() !== undefined);
  readonly bookDetailsError = computed(() => this.booksQuery.isError() && !this.booksQuery.isFetching());
  readonly bookDetailsRequired = computed(() =>
    BOOK_DEPENDENT_SORTS.has(this.sortBy())
    || this.filters().readStatus !== 'all'
    || this.filters().library !== 'all'
    || this.filters().genre !== 'all'
  );
  readonly loading = computed(() =>
    this.allAuthorsState() === null
    || (this.bookDetailsRequired() && !this.bookDetailsAvailable() && !this.bookDetailsError())
  );
  protected currentUser = this.userService.currentUser;
  readonly selectedCount = computed(() => this.selectedAuthors().size);
  readonly searchTerm = signal('');
  readonly sortBy = signal('name');
  readonly sortDirection = signal<SortDirection>('asc');
  readonly filters = signal<AuthorFilters>({...DEFAULT_AUTHOR_FILTERS});
  private readonly enrichedAuthors = computed(() => {
    const authors = this.allAuthorsState();
    if (!authors) {
      return [];
    }

    return this.enrichAuthors(authors, this.booksQuery.data() ?? []);
  });
  readonly libraryOptions = computed<FilterOption[]>(() => {
    const allLabel = this.t.translate('authorBrowser.filters.all');
    const librarySet = new Set<string>();

    for (const author of this.enrichedAuthors()) {
      for (const library of author.libraryNames) {
        librarySet.add(library);
      }
    }

    return [
      {label: allLabel, value: 'all'},
      ...[...librarySet].sort().map(library => ({label: library, value: library}))
    ];
  });
  readonly genreOptions = computed<FilterOption[]>(() => {
    const allLabel = this.t.translate('authorBrowser.filters.all');
    const genreSet = new Set<string>();

    for (const author of this.enrichedAuthors()) {
      for (const category of author.categories) {
        genreSet.add(category);
      }
    }

    return [
      {label: allLabel, value: 'all'},
      ...[...genreSet].sort().map(category => ({label: category, value: category}))
    ];
  });
  readonly activeFilterCount = computed(() => {
    const filters = this.filters();
    let count = 0;
    if (filters.matchStatus !== 'all') count++;
    if (filters.photoStatus !== 'all') count++;
    if (filters.readStatus !== 'all') count++;
    if (filters.bookCount !== 'all') count++;
    if (filters.library !== 'all') count++;
    if (filters.genre !== 'all') count++;
    return count;
  });
  readonly filteredAuthors = computed<EnrichedAuthor[]>(() => {
    let result = this.enrichedAuthors();
    const search = this.searchTerm().trim().toLowerCase();

    if (search) {
      result = result.filter(author => author.name.toLowerCase().includes(search));
    }

    result = this.applyFilters(result, this.filters());
    return this.applySort(result, this.sortBy(), this.sortDirection());
  });
  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth.set(window.innerWidth);
  }

  readonly isMobile = computed(() => !this.layoutService.isDesktop());
  private readonly baseCardWidth = computed(() => this.isMobile()
    ? AuthorBrowserComponent.MOBILE_BASE_WIDTH
    : AuthorBrowserComponent.BASE_WIDTH
  );
  private readonly gridDensity = createGridDensity(this.localStorageService, {
    useFixedColumns: this.isMobile,
    screenWidth: this.screenWidth,
    storageKey: AuthorBrowserComponent.MOBILE_COLUMNS_STORAGE_KEY,
    defaultColumns: AuthorBrowserComponent.DEFAULT_MOBILE_GRID_COLUMNS,
    minColumns: AuthorBrowserComponent.MIN_MOBILE_GRID_COLUMNS,
    maxColumns: AuthorBrowserComponent.MAX_MOBILE_GRID_COLUMNS,
    scale: this.scaleFactor,
    minScale: AuthorBrowserComponent.MIN_SCALE,
    maxScale: AuthorBrowserComponent.MAX_SCALE,
    gap: AuthorBrowserComponent.GRID_GAP,
    baseWidth: this.baseCardWidth,
    setScale: scale => this.scalePreference.setScale(scale),
  });
  readonly gridDensitySmallerDisabled = this.gridDensity.smallerDisabled;
  readonly gridDensityLargerDisabled = this.gridDensity.largerDisabled;
  private readonly cardAspectRatio = computed(() => {
    const baseHeight = this.isMobile()
      ? AuthorBrowserComponent.MOBILE_BASE_HEIGHT
      : AuthorBrowserComponent.BASE_HEIGHT;
    return baseHeight / this.baseCardWidth();
  });
  private readonly minCardWidth = computed(() => this.isMobile()
    ? 1
    : Math.round(this.baseCardWidth() * this.scaleFactor())
  );
  readonly virtualGrid = createVirtualGrid({
    items: this.filteredAuthors,
    scrollElement: this.scrollElement,
    minItemWidth: this.minCardWidth,
    gap: this.gridDensity.gap,
    columns: this.gridDensity.columns,
    initialOffset: this.initialScrollOffset,
    fillItemWidth: true,
    estimateItemHeight: itemWidth => Math.round(itemWidth * this.cardAspectRatio()),
  });

  sortOptions: SortOption[] = [];

  private readonly validSortValues = [
    'name', 'book-count', 'matched', 'recently-added', 'recently-read',
    'reading-progress', 'avg-rating', 'photo', 'series-count'
  ];

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('authorBrowser.pageTitle'));

    this.sortOptions = [
      {label: this.t.translate('authorBrowser.sort.name'), value: 'name'},
      {label: this.t.translate('authorBrowser.sort.bookCount'), value: 'book-count'},
      {label: this.t.translate('authorBrowser.sort.matched'), value: 'matched'},
      {label: this.t.translate('authorBrowser.sort.recentlyAdded'), value: 'recently-added'},
      {label: this.t.translate('authorBrowser.sort.recentlyRead'), value: 'recently-read'},
      {label: this.t.translate('authorBrowser.sort.readingProgress'), value: 'reading-progress'},
      {label: this.t.translate('authorBrowser.sort.avgRating'), value: 'avg-rating'},
      {label: this.t.translate('authorBrowser.sort.photo'), value: 'photo'},
      {label: this.t.translate('authorBrowser.sort.seriesCount'), value: 'series-count'}
    ];

    const sortParam = this.activatedRoute.snapshot.queryParamMap.get('sort');
    const dirParam = this.activatedRoute.snapshot.queryParamMap.get('dir') as SortDirection | null;
    if (sortParam && this.validSortValues.includes(sortParam)) {
      this.sortBy.set(sortParam);
      this.sortDirection.set(dirParam === 'asc' || dirParam === 'desc' ? dirParam : DEFAULT_SORT_DIRECTIONS[sortParam]);
      if (BOOK_DEPENDENT_SORTS.has(sortParam)) {
        this.requestBookDetails();
      }
    }

    this.scrollService.trackRoute({
      scrollElement: this.scrollElement,
      route: this.activatedRoute,
      destroyRef: this.destroyRef,
      dismissOverlaysBeforeSave: true,
    });
    this.destroyRef.onDestroy(() => this.selectionService.deselectAll());
  }

  adjustGridDensity(direction: GridDensityDirection): void {
    this.gridDensity.adjust(direction, this.virtualGrid);
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
  }

  onSortChange(value: string): void {
    if (BOOK_DEPENDENT_SORTS.has(value)) {
      this.requestBookDetails();
    }
    this.sortBy.set(value);
    const nextDirection = DEFAULT_SORT_DIRECTIONS[value] || 'asc';
    this.sortDirection.set(nextDirection);
    this.updateSortQueryParams(value, nextDirection);
  }

  toggleSortDirection(): void {
    const next: SortDirection = this.sortDirection() === 'asc' ? 'desc' : 'asc';
    this.sortDirection.set(next);
    this.updateSortQueryParams(this.sortBy(), next);
  }

  onFilterChange(key: keyof AuthorFilters, value: string): void {
    if (value !== 'all' && (key === 'readStatus' || key === 'library' || key === 'genre')) {
      this.requestBookDetails();
    }
    this.filters.update(current => ({...current, [key]: value}));
  }

  requestBookDetails(): void {
    this.bookDetailsRequested.set(true);
  }

  retryBookDetails(): void {
    void this.booksQuery.refetch();
  }

  returnToBasicAuthors(): void {
    this.resetFilters();
    this.onSortChange('name');
  }

  resetFilters(): void {
    this.filters.set({...DEFAULT_AUTHOR_FILTERS});
  }

  get canEditMetadata(): boolean {
    const user = this.userService.currentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canEditMetadata;
  }

  get canBulkEditMetadata(): boolean {
    const user = this.userService.currentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canBulkEditMetadata;
  }

  get canDeleteBook(): boolean {
    const user = this.userService.currentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canDeleteBook;
  }

  isAuthorSelected(authorId: number): boolean {
    return this.selectedAuthors().has(authorId);
  }

  onCheckboxClicked(event: AuthorCheckboxClickEvent): void {
    this.selectionService.handleCheckboxClick(event);
  }

  selectAllAuthors(): void {
    this.selectionService.selectAll();
  }

  deselectAllAuthors(): void {
    this.selectionService.deselectAll();
  }

  navigateToAuthor(author: AuthorSummary): void {
    this.router.navigate(['/author', author.id]);
  }

  navigateToAuthorEdit(author: AuthorSummary): void {
    this.router.navigate(['/author', author.id], {queryParams: {tab: 'edit'}});
  }

  deleteAuthor(author: AuthorSummary): void {
    this.authorService.deleteAuthors([author.id]).subscribe({
      next: () => {
        this.removeAuthorsFromList([author.id]);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.deleteSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteSuccessDetail')
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.deleteFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteFailedDetail')
        });
      }
    });
  }

  onAuthorQuickMatched(updated: AuthorSummary): void {
    this.thumbnailCacheBusters.set(updated.id, Date.now());
    this.allAuthorsState.update(current =>
      (current ?? []).map(author => author.id === updated.id ? updated : author)
    );
  }

  openMergeAuthorsDialog(): void {
    const ids = this.selectionService.getSelectedIds();
    if (ids.length < 2 || this.mergingAuthors()) {
      return;
    }

    const authorsById = new Map((this.allAuthorsState() ?? []).map(author => [author.id, author]));
    const selectedAuthors = ids
      .map(id => authorsById.get(id))
      .filter((author): author is AuthorSummary => author !== undefined);
    if (selectedAuthors.length < 2) {
      return;
    }

    this.returnToDuplicates = false;
    this.openMergeCandidates(selectedAuthors);
  }

  openDuplicateAuthors(): void {
    if (this.findingDuplicates()) return;
    this.findingDuplicates.set(true);
    setTimeout(() => {
      if (this.destroyRef.destroyed) return;
      try {
        this.duplicateGroups.set(findAuthorDuplicates(this.allAuthorsState() ?? []));
        this.duplicateLimit.set(50);
        this.duplicateDialogVisible.set(true);
      } finally {
        this.findingDuplicates.set(false);
      }
    });
  }

  reviewDuplicateGroup(authors: AuthorSummary[]): void {
    this.duplicateDialogVisible.set(false);
    this.returnToDuplicates = true;
    this.openMergeCandidates(authors);
  }

  showMoreDuplicates(): void {
    this.duplicateLimit.update(limit => limit + 50);
  }

  private openMergeCandidates(authors: AuthorSummary[]): void {
    this.mergeCandidates.set(authors);
    this.includedMergeIds.set(new Set(authors.map(author => author.id)));
    this.mergeTargetAuthorId.set(null);
    this.mergeAuthorsDialogVisible.set(true);
  }

  toggleMergeCandidate(authorId: number, included: boolean): void {
    this.includedMergeIds.update(current => {
      const next = new Set(current);
      if (included) next.add(authorId);
      else next.delete(authorId);
      return next;
    });
    if (!included && this.mergeTargetAuthorId() === authorId) {
      this.mergeTargetAuthorId.set(null);
    }
  }

  confirmMergeAuthors(): void {
    if (this.mergingAuthors()) {
      return;
    }
    const selectedAuthors = this.mergeCandidates().filter(author => this.includedMergeIds().has(author.id));
    const target = selectedAuthors.find(author => author.id === this.mergeTargetAuthorId());
    const sources = selectedAuthors.filter(author => author.id !== target?.id);
    if (!target || sources.length === 0) {
      return;
    }

    this.mergingAuthors.set(true);
    this.bookMetadataManageService.mergeAuthors(target.id, sources.map(author => author.id)).subscribe({
      next: () => {
        const mergedIds = new Set(sources.map(author => author.id));
        this.allAuthorsState.update(current => (current ?? []).filter(author => !mergedIds.has(author.id)));
        this.selectionService.deselectAll();
        this.mergingAuthors.set(false);
        const returnToDuplicates = this.returnToDuplicates;
        this.returnToDuplicates = false;
        this.mergeAuthorsDialogVisible.set(false);
        this.mergeCandidates.set([]);
        this.mergeTargetAuthorId.set(null);
        if (returnToDuplicates) {
          this.refreshDuplicateResults(mergedIds);
        } else {
          this.authorService.invalidateAuthors();
        }
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.mergeAuthors.successSummary'),
          detail: this.t.translate('authorBrowser.mergeAuthors.successDetail', {
            count: sources.length,
            target: target.name,
          })
        });
      },
      error: error => {
        this.mergingAuthors.set(false);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.mergeAuthors.failureSummary'),
          detail: error?.error?.message || this.t.translate('authorBrowser.mergeAuthors.failureDetail')
        });
      }
    });
  }

  closeMergeAuthorsDialog(): void {
    if (this.mergingAuthors()) {
      return;
    }
    const returnToDuplicates = this.returnToDuplicates;
    this.returnToDuplicates = false;
    this.mergeAuthorsDialogVisible.set(false);
    this.mergeCandidates.set([]);
    this.mergeTargetAuthorId.set(null);
    if (returnToDuplicates) {
      this.duplicateDialogVisible.set(true);
    }
  }

  private async refreshDuplicateResults(mergedIds: Set<number>): Promise<void> {
    this.findingDuplicates.set(true);
    this.duplicateDialogVisible.set(true);
    try {
      const authors = await this.authorService.refreshAuthors();
      if (this.destroyRef.destroyed) return;
      this.allAuthorsState.set(authors);
      this.duplicateGroups.set(findAuthorDuplicates(authors));
    } catch {
      if (this.destroyRef.destroyed) return;
      const authors = (this.allAuthorsState() ?? []).filter(author => !mergedIds.has(author.id));
      this.allAuthorsState.set(authors);
      this.duplicateGroups.set(findAuthorDuplicates(authors));
      this.authorService.invalidateAuthors();
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('authorBrowser.duplicates.refreshFailedSummary'),
        detail: this.t.translate('authorBrowser.duplicates.refreshFailedDetail')
      });
    } finally {
      if (!this.destroyRef.destroyed) {
        this.findingDuplicates.set(false);
      }
    }
  }

  autoMatchSelected(): void {
    const ids = this.selectionService.getSelectedIds();
    this.selectionService.deselectAll();
    if (this.autoMatchInProgress()) {
      return;
    }

    const authorsById = new Map((this.allAuthorsState() ?? []).map(author => [author.id, author]));
    this.autoMatchQueue = ids
      .map(id => authorsById.get(id))
      .filter((author): author is AuthorSummary => author !== undefined);
    if (this.autoMatchQueue.length === 0) {
      return;
    }

    this.autoMatchStats = {matched: 0, ambiguous: 0, noResults: 0, failed: 0};
    this.autoMatchProgress.set({processed: 0, total: this.autoMatchQueue.length});
    this.autoMatchInProgress.set(true);
    this.processNextAutoMatch();
  }

  selectPendingMatch(candidate: AuthorSearchResult): void {
    const pending = this.pendingMatch();
    if (!pending || this.applyingPendingMatch()) {
      return;
    }

    this.applyingPendingMatch.set(true);
    this.authorService.matchAuthor(pending.author.id, {
      source: candidate.source,
      providerId: candidate.providerId ?? candidate.asin,
      asin: candidate.asin,
    }).subscribe({
      next: updated => {
        this.recordAuthorMatch(pending.author, updated, candidate);
        this.autoMatchStats.matched++;
        this.applyingPendingMatch.set(false);
        this.pendingMatch.set(null);
        this.completeAutoMatchItem();
        this.processNextAutoMatch();
      },
      error: () => {
        this.autoMatchStats.failed++;
        this.applyingPendingMatch.set(false);
        this.pendingMatch.set(null);
        this.completeAutoMatchItem();
        this.processNextAutoMatch();
      }
    });
  }

  skipPendingMatch(): void {
    if (!this.pendingMatch() || this.applyingPendingMatch()) {
      return;
    }
    this.autoMatchStats.ambiguous++;
    this.pendingMatch.set(null);
    this.completeAutoMatchItem();
    this.processNextAutoMatch();
  }

  private processNextAutoMatch(): void {
    const author = this.autoMatchQueue.shift();
    if (!author) {
      this.finishAutoMatch();
      return;
    }

    this.authorService.resolveAuthorMatch(author.id, author.name, undefined, author.asin).subscribe({
      next: resolution => {
        if (resolution.status === 'matched') {
          this.recordAuthorMatch(author, resolution.author, resolution.candidate);
          this.autoMatchStats.matched++;
          this.completeAutoMatchItem();
          this.processNextAutoMatch();
        } else if (resolution.status === 'ambiguous') {
          this.pendingMatch.set({author, candidates: resolution.candidates});
        } else {
          this.autoMatchStats.noResults++;
          this.completeAutoMatchItem();
          this.processNextAutoMatch();
        }
      },
      error: () => {
        this.autoMatchStats.failed++;
        this.completeAutoMatchItem();
        this.processNextAutoMatch();
      }
    });
  }

  private completeAutoMatchItem(): void {
    this.autoMatchProgress.update(progress => ({
      ...progress,
      processed: Math.min(progress.total, progress.processed + 1),
    }));
  }

  private recordAuthorMatch(author: AuthorSummary, updated: AuthorDetails, candidate: AuthorSearchResult): void {
    this.thumbnailCacheBusters.set(author.id, Date.now());
    this.allAuthorsState.update(current => (current ?? []).map(existing => existing.id === author.id
      ? {
          ...existing,
          name: updated.name,
          asin: updated.asin,
          hasPhoto: updated.hasPhoto ?? (existing.hasPhoto || !!candidate.imageUrl),
        }
      : existing
    ));
  }

  private finishAutoMatch(): void {
    this.autoMatchInProgress.set(false);
    const {matched, ambiguous, noResults, failed} = this.autoMatchStats;
    this.messageService.add({
      severity: failed > 0 ? 'warn' : 'success',
      summary: this.t.translate('authorBrowser.toast.autoMatchSuccessSummary'),
      detail: this.t.translate('authorBrowser.toast.autoMatchResultsDetail', {
        matched,
        ambiguous,
        noResults,
        failed,
      })
    });
  }

  deleteSelected(): void {
    const ids = this.selectionService.getSelectedIds();
    this.authorService.deleteAuthors(ids).subscribe({
      next: () => {
        this.selectionService.deselectAll();
        this.removeAuthorsFromList(ids);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.deleteSelectedSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteSelectedSuccessDetail')
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.deleteFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteFailedDetail')
        });
      }
    });
  }

  cleanupAuthorsWithoutBooks(): void {
    if (this.deletingAuthorsWithoutBooks()) {
      return;
    }

    this.confirmationService.confirm({
      header: this.t.translate('authorBrowser.cleanup.confirmTitle'),
      message: this.t.translate('authorBrowser.cleanup.confirmMessage'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      acceptLabel: this.t.translate('authorBrowser.cleanup.confirmAccept'),
      rejectLabel: this.t.translate('authorBrowser.cleanup.confirmReject'),
      accept: () => {
        this.deletingAuthorsWithoutBooks.set(true);
        this.authorService.deleteAuthorsWithoutBooks().subscribe({
          next: deletedCount => {
            this.messageService.add(deletedCount > 0
              ? {
                  severity: 'success',
                  summary: this.t.translate('authorBrowser.toast.cleanupSuccessSummary'),
                  detail: this.t.translate('authorBrowser.toast.cleanupSuccessDetail', {count: deletedCount})
                }
              : {
                  severity: 'info',
                  summary: this.t.translate('authorBrowser.toast.cleanupEmptySummary'),
                  detail: this.t.translate('authorBrowser.toast.cleanupEmptyDetail')
                });
          },
          error: () => {
            this.deletingAuthorsWithoutBooks.set(false);
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('authorBrowser.toast.cleanupFailedSummary'),
              detail: this.t.translate('authorBrowser.toast.cleanupFailedDetail')
            });
          },
          complete: () => this.deletingAuthorsWithoutBooks.set(false)
        });
      }
    });
  }

  private updateSortQueryParams(sort: string, dir: SortDirection): void {
    this.router.navigate([], {
      queryParams: {sort, dir},
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  private enrichAuthors(authors: AuthorSummary[], books: Book[]): EnrichedAuthor[] {
    const booksByAuthor = new Map<string, Book[]>();
    for (const book of books) {
      if (book.metadata?.authors) {
        for (const authorName of book.metadata.authors) {
          const key = authorName.toLowerCase();
          let list = booksByAuthor.get(key);
          if (!list) {
            list = [];
            booksByAuthor.set(key, list);
          }
          list.push(book);
        }
      }
    }

    return authors.map(author => {
      const authorBooks = booksByAuthor.get(author.name.toLowerCase()) ?? [];

      const libraryIds = new Set<number>();
      const libraryNameSet = new Set<string>();
      const categorySet = new Set<string>();
      const seriesSet = new Set<string>();
      let latestAddedOn: string | null = null;
      let lastReadTime: string | null = null;
      let readCount = 0;
      let inProgressCount = 0;
      let ratingSum = 0;
      let ratingCount = 0;

      for (const book of authorBooks) {
        libraryIds.add(book.libraryId);
        if (book.libraryName) libraryNameSet.add(book.libraryName);

        if (book.metadata?.categories) {
          for (const cat of book.metadata.categories) categorySet.add(cat);
        }
        if (book.metadata?.seriesName) {
          seriesSet.add(book.metadata.seriesName.toLowerCase());
        }
        if (book.addedOn && (!latestAddedOn || book.addedOn > latestAddedOn)) {
          latestAddedOn = book.addedOn;
        }
        if (book.lastReadTime && (!lastReadTime || book.lastReadTime > lastReadTime)) {
          lastReadTime = book.lastReadTime;
        }
        if (book.readStatus === ReadStatus.READ) readCount++;
        if (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING) inProgressCount++;
        if (book.personalRating != null) {
          ratingSum += book.personalRating;
          ratingCount++;
        }
      }

      const totalBooks = authorBooks.length;
      let readStatus: EnrichedAuthor['readStatus'] = 'unread';
      if (totalBooks > 0) {
        if (readCount === totalBooks) {
          readStatus = 'all-read';
        } else if (inProgressCount > 0) {
          readStatus = 'in-progress';
        } else if (readCount > 0) {
          readStatus = 'some-read';
        }
      }

      return {
        ...author,
        libraryIds,
        libraryNames: [...libraryNameSet].sort(),
        categories: [...categorySet].sort(),
        readStatus,
        hasSeries: seriesSet.size > 0,
        seriesCount: seriesSet.size,
        latestAddedOn,
        lastReadTime,
        readingProgress: totalBooks > 0 ? Math.round((readCount / totalBooks) * 100) : 0,
        avgPersonalRating: ratingCount > 0 ? ratingSum / ratingCount : null
      };
    });
  }

  private applyFilters(authors: EnrichedAuthor[], filters: AuthorFilters): EnrichedAuthor[] {
    return authors.filter(a => {
      if (filters.matchStatus === 'matched' && !a.asin) return false;
      if (filters.matchStatus === 'unmatched' && a.asin) return false;

      if (filters.photoStatus === 'has-photo' && !a.hasPhoto) return false;
      if (filters.photoStatus === 'no-photo' && a.hasPhoto) return false;

      if (filters.readStatus !== 'all' && a.readStatus !== filters.readStatus) return false;

      if (filters.bookCount !== 'all') {
        const c = a.bookCount;
        switch (filters.bookCount) {
          case '0': if (c !== 0) return false; break;
          case '1': if (c !== 1) return false; break;
          case '2': if (c !== 2) return false; break;
          case '3': if (c !== 3) return false; break;
          case '4': if (c !== 4) return false; break;
          case '5': if (c !== 5) return false; break;
          case '6-10': if (c < 6 || c > 10) return false; break;
          case '11-20': if (c < 11 || c > 20) return false; break;
          case '21-35': if (c < 21 || c > 35) return false; break;
          case '36+': if (c < 36) return false; break;
        }
      }

      if (filters.library !== 'all' && !a.libraryNames.includes(filters.library)) return false;
      if (filters.genre !== 'all' && !a.categories.includes(filters.genre)) return false;

      return true;
    });
  }

  private removeAuthorsFromList(ids: number[]): void {
    const idSet = new Set(ids);
    this.allAuthorsState.update(current => (current ?? []).filter(author => !idSet.has(author.id)));
  }

  private applySort(authors: EnrichedAuthor[], sortBy: string, direction: SortDirection): EnrichedAuthor[] {
    const sorted = [...authors];
    const dir = direction === 'asc' ? 1 : -1;

    switch (sortBy) {
      case 'name':
        return sorted.sort((a, b) => dir * a.name.localeCompare(b.name));
      case 'book-count':
        return sorted.sort((a, b) => dir * (a.bookCount - b.bookCount));
      case 'matched':
        return sorted.sort((a, b) => {
          const aVal = a.asin ? 1 : 0;
          const bVal = b.asin ? 1 : 0;
          if (aVal !== bVal) return dir * (aVal - bVal);
          return a.name.localeCompare(b.name);
        });
      case 'recently-added':
        return sorted.sort((a, b) => {
          if (!a.latestAddedOn && !b.latestAddedOn) return a.name.localeCompare(b.name);
          if (!a.latestAddedOn) return 1;
          if (!b.latestAddedOn) return -1;
          return dir * a.latestAddedOn.localeCompare(b.latestAddedOn);
        });
      case 'recently-read':
        return sorted.sort((a, b) => {
          if (!a.lastReadTime && !b.lastReadTime) return a.name.localeCompare(b.name);
          if (!a.lastReadTime) return 1;
          if (!b.lastReadTime) return -1;
          return dir * a.lastReadTime.localeCompare(b.lastReadTime);
        });
      case 'reading-progress':
        return sorted.sort((a, b) => dir * (a.readingProgress - b.readingProgress));
      case 'avg-rating':
        return sorted.sort((a, b) => {
          if (a.avgPersonalRating == null && b.avgPersonalRating == null) return a.name.localeCompare(b.name);
          if (a.avgPersonalRating == null) return 1;
          if (b.avgPersonalRating == null) return -1;
          return dir * (a.avgPersonalRating - b.avgPersonalRating);
        });
      case 'photo':
        return sorted.sort((a, b) => {
          const aVal = a.hasPhoto ? 1 : 0;
          const bVal = b.hasPhoto ? 1 : 0;
          if (aVal !== bVal) return dir * (aVal - bVal);
          return a.name.localeCompare(b.name);
        });
      case 'series-count':
        return sorted.sort((a, b) => dir * (a.seriesCount - b.seriesCount));
      default:
        return sorted;
    }
  }
}
