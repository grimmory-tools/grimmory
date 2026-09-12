import {NgTemplateOutlet} from '@angular/common';
import {Component, computed, effect, inject, linkedSignal, signal, untracked, viewChild} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {injectInfiniteQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {take} from 'rxjs/operators';

import {BrowseGridComponent} from '../../../shared/browse/grid/grid.component';
import {
  BrowseEmptyDef,
  BrowseGridItemDef,
  BrowseGridSkeletonDef,
} from '../../../shared/browse/grid/grid.directives';
import {bookCardHeightForWidth} from '../components/cards/book-card.layout';
import {BookCardComponent, bookCardCoverSrc} from '../components/cards/book-card.component';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {BookMenuComponent} from '../components/book-menu/book-menu.component';
import {BookCardSkeletonComponent} from '../components/cards/book-card-skeleton.component';
import {ArtworkRevealGroupDirective} from '../../../shared/components/cover/artwork-reveal-group.directive';
import {AppPageHeaderComponent} from '../../../shared/layout/page-header/app.page-header.component';
import {type PageHeader} from '../../../shared/layout/page-header/page-header.service';
import {LayoutService} from '../../../shared/layout/layout.service';
import {LocalStorageService} from '../../../shared/service/local-storage.service';
import {PageTitleService} from '../../../shared/service/page-title.service';
import {createGridDensity, type GridDensityDirection} from '../../../shared/util/grid-density.util';
import {CoverScalePreferenceService} from '../../../shared/service/cover-scale-preference.service';
import {LibraryService} from '../service/library.service';
import {BookNavigationService} from '../service/book-navigation.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {
  type BookPageParams,
  type BookQuerySortKey,
  type BookSortTerm,
  type BookQueryFacetKey,
} from '../data/book-query-params';
import {bookCollectionKeys} from '../data/book-query-keys';
import {bookBrowseScope, bookBrowseScopePreferenceContext} from './book-browse-scope';
import {createBookBrowseQueries} from './book-browse-queries';
import {createBookBrowsePreferences} from './book-browse-preferences';
import {createBookBrowseUrlState} from './book-browse-url-state';
import {type BookBrowseMultiSortDialogResult} from './book-browse-multi-sort-dialog.component';
import {bookSortHasDetailLine, bookSortOptions} from './book-browse-sort';
import {BookBrowseDetailLineService} from './book-browse-detail-line.service';
import {cn} from '../../../shared/ui/cn';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {BrowseSearchInputComponent} from '../../../shared/browse/search-input/search-input.component';
import {BrowseFilterRailComponent} from '../../../shared/browse/filter-rail/filter-rail.component';
import {
  countBrowseFacetValues,
  toggleBrowseFacetValue,
  withBrowseFacetRange,
  type BrowseFilterChip,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
} from '../../../shared/browse/facets';
import {BookBrowseToolbarComponent} from './book-browse-toolbar.component';
import {BookBrowseBulkBarComponent} from './book-browse-bulk-bar.component';
import {BrowseSelectModeControlsComponent} from '../../../shared/browse/bulk-actions/select-mode-controls.component';
import {BookQueryService} from '../data/book-query.service';
import {type BookSummary} from '../data/book-response.models';
import {
  injectPendingBookDeletions,
  injectPendingBookProgressResets,
  injectPendingBookReadStatuses,
  overlayPendingBookState,
  type PendingBookOverlay,
} from '../data/book-command-pending-state';
import {createBrowseSelection} from '../../../shared/browse/selection';
import {createBrowseResults} from '../../../shared/browse/results';
import {heldSignal} from '../../../shared/util/held-signal';
import {createBrowseSearchDraft} from '../../../shared/browse/search-draft';
import {installBrowseSelectionShortcuts} from '../../../shared/browse/selection-shortcuts';
import {BrowseFilterChipsComponent} from '../../../shared/browse/filter-chips/filter-chips.component';
import {BookBrowseTableComponent} from './book-browse-table.component';

const SELECTION_CLICK_AWAY_EXEMPT =
  'app-book-card, app-browse-bulk-actions-bar, app-menu, app-page-header, aside, ' +
  'app-book-browse-table, ' +
  '.cdk-overlay-container, .p-dialog-mask, [role="dialog"], ' +
  'button, a, input, textarea, select, label';

const PAGE_SIZE = 60;

interface BrowseChipsBand {
  count: number;
  query: string;
  chips: readonly BrowseFilterChip<BookQueryFacetKey>[];
}

const RAIL_OPEN_STORAGE_KEY = 'browseFilterRailOpen';

const GRID_GAP = 16;
const CARD_BASE_WIDTH = 135;
const DESKTOP_MIN_SCALE = 0.5;
const DESKTOP_MAX_SCALE = 1.5;
const MOBILE_COLUMNS_STORAGE_KEY = 'mobileColumnsPreference';
const DEFAULT_MOBILE_COLUMNS = 3;
const MIN_MOBILE_COLUMNS = 2;
const MAX_MOBILE_COLUMNS = 4;

@Component({
  selector: 'app-book-browse-page',
  imports: [
    NgTemplateOutlet,
    TranslocoPipe,
    AppPageHeaderComponent,
    BrowseGridComponent,
    BrowseGridItemDef,
    BrowseGridSkeletonDef,
    BrowseEmptyDef,
    BookCardComponent,
    BookMenuComponent,
    BookCardSkeletonComponent,
    ArtworkRevealGroupDirective,
    BookBrowseToolbarComponent,
    BookBrowseTableComponent,
    BookBrowseBulkBarComponent,
    BrowseSelectModeControlsComponent,
    BrowseFilterRailComponent,
    BrowseFilterChipsComponent,
    BrowseSearchInputComponent,
    AppButtonComponent,
  ],
  templateUrl: './book-browse-page.component.html',
})
export class BookBrowsePageComponent {
  private readonly bookQuery = inject(BookQueryService);
  private readonly queryClient = inject(QueryClient);
  private readonly pageTitle = inject(PageTitleService);
  private readonly route = inject(ActivatedRoute);
  private readonly layout = inject(LayoutService);
  private readonly localStorage = inject(LocalStorageService);
  private readonly coverScale = inject(CoverScalePreferenceService);
  private readonly detailLines = inject(BookBrowseDetailLineService);
  private readonly libraryService = inject(LibraryService);
  protected readonly bookNavigation = inject(BookNavigationService);
  private readonly urlHelper = inject(UrlHelperService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  private readonly pendingReadStatuses = injectPendingBookReadStatuses();
  private readonly pendingProgressResets = injectPendingBookProgressResets();
  protected readonly pendingDeletions = injectPendingBookDeletions();

  private readonly gridRef = viewChild(BrowseGridComponent);
  private readonly tableRef = viewChild(BookBrowseTableComponent);
  protected readonly bookMenu = viewChild(BookMenuComponent);
  private readonly headerRef = viewChild(AppPageHeaderComponent);
  protected readonly isMobile = computed(() => !this.layout.isDesktop());
  protected readonly mobileSelectMode = linkedSignal<boolean, boolean>({
    source: this.isMobile,
    computation: (isMobile, previous) => isMobile && (previous?.value ?? false),
  });

  private readonly gridDensity = createGridDensity(this.localStorage, {
    useFixedColumns: this.isMobile,
    screenWidth: () => window.innerWidth,
    storageKey: MOBILE_COLUMNS_STORAGE_KEY,
    defaultColumns: DEFAULT_MOBILE_COLUMNS,
    minColumns: MIN_MOBILE_COLUMNS,
    maxColumns: MAX_MOBILE_COLUMNS,
    scale: this.coverScale.scaleFactor,
    minScale: DESKTOP_MIN_SCALE,
    maxScale: DESKTOP_MAX_SCALE,
    gap: GRID_GAP,
    baseWidth: () => CARD_BASE_WIDTH,
    setScale: scale => this.coverScale.setScale(scale),
  });
  protected readonly gridGap = this.gridDensity.gap;
  protected readonly gridRowGap = GRID_GAP;
  protected readonly gridColumns = this.gridDensity.columns;
  protected readonly densitySmallerDisabled = this.gridDensity.smallerDisabled;
  protected readonly densityLargerDisabled = this.gridDensity.largerDisabled;
  protected readonly minCardWidth = computed(() =>
    this.isMobile() ? 1 : Math.round(CARD_BASE_WIDTH * this.coverScale.scaleFactor()),
  );
  private readonly railOpen = signal(this.localStorage.get<boolean>(RAIL_OPEN_STORAGE_KEY) === true);
  protected readonly railVisible = computed(() => !this.isMobile() && this.railOpen());
  protected readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);

  protected readonly urlState = createBookBrowseUrlState();
  private readonly routeParamMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  private readonly scope = computed(() =>
    bookBrowseScope(this.routeParamMap(), this.route.snapshot.data),
  );
  private readonly preferenceContext = computed(() => bookBrowseScopePreferenceContext(this.scope()));

  protected readonly queries = createBookBrowseQueries({
    selection: this.urlState.facets,
    query: this.urlState.query,
    scope: this.scope,
    enabled: this.railVisible,
  });
  protected readonly railReady = computed(() => !this.queries.pending());
  protected readonly sortOptions = computed(() => bookSortOptions(this.queries.sortTokens()));
  private readonly availableSortKeys = computed<ReadonlySet<BookQuerySortKey>>(
    () => new Set(this.sortOptions().map(option => option.id)),
  );

  protected readonly preferences = createBookBrowsePreferences({
    context: this.preferenceContext,
    availableSortKeys: this.availableSortKeys,
    urlState: this.urlState,
  });
  protected readonly sortTerms = this.preferences.sortTerms;

  protected readonly searchDraft = createBrowseSearchDraft({
    committed: this.urlState.query,
    commit: term => this.urlState.setQuery(term),
  });

  private readonly params = computed<BookPageParams>(() => ({
    ...this.queries.collectionParams(),
    size: PAGE_SIZE,
    sort: this.sortTerms(),
  }));
  private readonly collectionKeys = computed(() => bookCollectionKeys(this.params()));
  private readonly filtersKey = computed(() => this.collectionKeys().filtersKey);
  private readonly shuffleGeneration = signal(0);
  private readonly listKey = computed(() =>
    `${this.collectionKeys().listKey}:${this.shuffleGeneration()}`);
  private readonly booksQuery = injectInfiniteQuery(() => this.bookQuery.infinitePage(this.params()));

  protected readonly presentation = createBrowseResults<BookSummary>({
    query: this.booksQuery,
    listKey: this.listKey,
    artworkUrls: (books, lastVisibleIndex) => {
      return books.slice(0, lastVisibleIndex + 1).flatMap(book => {
        const url = bookCardCoverSrc(book, book.primaryFile, this.urlHelper);
        return url ? [url] : [];
      });
    },
    scrollToTop: () => {
      this.tableRef()?.scrollToTop();
      this.gridRef()?.scrollToTop();
    },
  });
  protected readonly chipsBand = heldSignal<BrowseChipsBand>(
    () => ({
      count: countBrowseFacetValues(this.urlState.facets()) + (this.urlState.query() ? 1 : 0),
      query: this.urlState.query(),
      chips: this.queries.chips(),
    }),
    this.presentation.showingPreviousResults,
  );

  private readonly pendingBookOverlay = computed<PendingBookOverlay>(() => ({
    readStatuses: this.pendingReadStatuses(),
    progressResets: this.pendingProgressResets(),
  }));

  protected readonly books = computed<readonly BookSummary[]>(() => {
    const books = this.presentation.items();
    const overlay = this.pendingBookOverlay();
    if (overlay.readStatuses.size === 0
      && overlay.progressResets.size === 0) {
      return books;
    }
    return books.map(book => overlayPendingBookState(book, overlay));
  });
  protected readonly hasNextPage = this.presentation.shownHasNextPage;

  protected readonly selection = createBrowseSelection({
    filtersKey: this.filtersKey,
    listKey: this.listKey,
    items: this.books,
    totalElements: this.presentation.total,
  });
  protected readonly selectionEnabled = computed(() => !this.isMobile() || this.mobileSelectMode());
  protected readonly fetchMatchingBookIds = (): Promise<readonly number[]> =>
    this.queryClient.query(this.bookQuery.ids(this.params()));

  private readonly detailLineKey = heldSignal<BookQuerySortKey | null>(
    () => {
      const primary = this.sortTerms().at(0);
      return primary && !this.preferences.isDefaultSort(this.sortTerms()) && bookSortHasDetailLine(primary.key)
        ? primary.key
        : null;
    },
    this.presentation.showingPreviousResults,
  );
  protected readonly squareCovers = computed(() => this.squareCoversFor(this.books()));
  protected readonly metaLines = computed<2 | 3>(() => this.detailLineKey() === null ? 2 : 3);
  protected readonly estimateItemHeight = (width: number): number =>
    bookCardHeightForWidth(width, {square: this.squareCovers(), metaLines: this.metaLines()});
  protected readonly bookItemKey = (book: BookSummary): number => book.id;

  protected readonly pageHeader = computed<PageHeader>(() => {
    const total = this.presentation.total();
    return {
      title: this.queries.title(),
      count: total == null ? undefined : total.toLocaleString(),
    };
  });

  protected readonly mobileTableLayout = computed(
    () => this.isMobile() && this.preferences.viewMode() === 'table',
  );
  protected readonly layoutClasses = computed(() => {
    const page = 'app-page pb-0!';
    const row = 'flex items-start gap-7';
    const column = 'min-w-0 flex-1';

    if (this.mobileTableLayout()) {
      return {
        page: cn(page, 'h-[calc(100dvh-var(--mobile-topbar-height))] overflow-hidden'),
        row: cn(row, 'min-h-0 flex-1'),
        column: cn(column, 'flex min-h-0 flex-col h-full'),
      };
    }

    if (this.isMobile()) {
      return {page, row, column: cn(column, 'pb-8')};
    }

    return {
      page,
      row,
      column: cn(
        column,
        'flex min-h-0 flex-col h-[calc(100dvh-var(--page-stuck-offset,0px))]',
        this.preferences.viewMode() === 'table' && 'pb-4',
      ),
    };
  });
  protected readonly gridBoxClass = computed(() => cn('md:pb-8', !this.railVisible() && 'md:-mr-8 md:pr-8'));
  protected readonly chipsBandClass = computed(() =>
    cn(
      'sticky top-[var(--page-stuck-offset,0px)] z-10 -mx-4 bg-page px-4',
      this.isMobile() ? 'pb-1.5' : 'pb-3',
      this.headerRef()?.isStuck() &&
        'shadow-[0_1px_0_0_color-mix(in_srgb,var(--color-border)_70%,transparent)]',
    ),
  );
  protected readonly hairlineStripClass = computed(() =>
    cn(
      'sticky top-[var(--page-stuck-offset,0px)] z-10 -mx-4 h-0 border-b',
      this.headerRef()?.isStuck() ? 'border-border/70' : 'border-transparent',
    ),
  );

  constructor() {
    effect(() => {
      const range = this.gridRef()?.renderedRange() ?? this.tableRef()?.renderedRange();
      if (range) {
        untracked(() => this.presentation.onRenderedRange(range));
      }
    });

    effect(() => {
      const title = this.pageHeader().title;
      if (title) {
        this.pageTitle.setPageTitle(title);
      }
    });

    installBrowseSelectionShortcuts({
      enabled: () => this.selectionEnabled(),
      active: () => this.selection.active(),
      suspended: () => this.menuOpenBookId() !== null,
      clear: () => this.selection.clear(),
      selectAll: () => this.selection.selectAll(),
      onEscapeWhileInactive: () => {
        if (this.mobileSelectMode()) {
          this.mobileSelectMode.set(false);
        }
      },
      clickAwayExempt: SELECTION_CLICK_AWAY_EXEMPT,
    });
  }

  protected detailLineFor(book: BookSummary): string | null {
    const key = this.detailLineKey();
    return key === null ? null : this.detailLines.lineFor(key, book);
  }

  private squareCoversFor(books: readonly BookSummary[]): boolean {
    const scope = this.scope();
    if (scope?.kind === 'library') {
      const formats = this.libraryService.libraries()
        .find(library => library.id === scope.entityId)?.allowedFormats;
      if (formats?.length === 1 && formats[0] === 'AUDIOBOOK') {
        return true;
      }
    }
    return books.length > 0
      && books.every(book => book.primaryFile?.bookType === 'AUDIOBOOK');
  }

  protected onDensityChange(direction: GridDensityDirection): void {
    const grid = this.gridRef()?.densityGrid();
    if (grid) {
      this.gridDensity.adjust(direction, grid);
    }
  }

  protected onMobileSelectToggle(): void {
    if (this.mobileSelectMode()) {
      this.selection.clear();
      this.mobileSelectMode.set(false);
      return;
    }
    this.mobileSelectMode.set(true);
  }

  protected onBookDetailRequested(book: BookSummary): void {
    this.bookNavigation.openBook(book.id, this.books().map(presented => presented.id));
  }

  protected onFiltersToggle(): void {
    if (this.isMobile()) {
      this.urlState.openFilterPage();
      return;
    }
    const next = !this.railOpen();
    this.railOpen.set(next);
    this.localStorage.set(RAIL_OPEN_STORAGE_KEY, next);
  }

  protected onToggleFacet(toggle: BrowseFilterToggle<BookQueryFacetKey>): void {
    this.urlState.setFacets(toggleBrowseFacetValue(
      this.urlState.facets(),
      toggle.key,
      toggle.value,
      toggle.selected,
    ));
  }

  protected onCommitFacetRange(commit: BrowseFilterRangeCommit<BookQueryFacetKey>): void {
    this.urlState.setFacetRange(
      withBrowseFacetRange(this.urlState.facets(), commit.key, commit.min, commit.max, this.queries.definitions()),
    );
  }

  protected onClearQuery(): void {
    this.searchDraft.set('');
    this.urlState.clearQuery();
  }

  protected onClearAllFilters(): void {
    this.searchDraft.set('');
    this.urlState.clearFilters();
  }

  protected onSortDirectionChange(term: BookSortTerm): void {
    this.preferences.setSortTerms([term, ...this.sortTerms().slice(1)]);
  }

  protected onRandomSortRequested(): void {
    this.shuffleGeneration.update(generation => generation + 1);
    void this.bookQuery.restartInfinitePage(this.params());
  }

  protected async onMultiSortRequested(): Promise<void> {
    const ref = await this.dialogLauncher.openMultiSortDialog({
      terms: this.sortTerms(),
      options: this.sortOptions(),
      saveDefaultFor: this.queries.title(),
    });
    ref?.onClose.pipe(take(1)).subscribe((result?: BookBrowseMultiSortDialogResult) => {
      if (!result) {
        return;
      }
      this.preferences.setSortTerms(result.terms);
      if (result.saveAsDefault) {
        this.preferences.saveSortDefault(result.terms);
      }
    });
  }
}
