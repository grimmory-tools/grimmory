import {AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, OnDestroy, computed, effect, inject, OnInit, signal, untracked, ViewChild} from '@angular/core';
import {takeUntilDestroyed, toObservable, toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, NavigationStart, Router} from '@angular/router';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {debounceTime, distinctUntilChanged, filter, map, take} from 'rxjs/operators';
import {combineLatest, finalize} from 'rxjs';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Library} from '../../model/library.model';
import {SortDirection, SortOption} from '../../model/sort.model';
import {Book} from '../../model/book.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {BookTableComponent} from './book-table/book-table.component';
import {computeGridColumns} from '../../../../shared/util/viewport.util';
import {Button} from 'primeng/button';
import {NgClass, NgStyle} from '@angular/common';
import {BookCardComponent} from './book-card/book-card.component';

import {Menu} from 'primeng/menu';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {BookFilterComponent} from './book-filter/book-filter.component';
import {Tooltip} from 'primeng/tooltip';
import {BookFilterMode, DEFAULT_VISIBLE_SORT_FIELDS, EntityViewPreferences, SortCriterion, UserService} from '../../../settings/user-management/user.service';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';
import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {BookSorter} from './sorting/BookSorter';
import {BookDialogHelperService} from './book-dialog-helper.service';
import {Checkbox} from 'primeng/checkbox';
import {Popover} from 'primeng/popover';
import {Slider} from 'primeng/slider';
import {Divider} from 'primeng/divider';
import {MultiSelect} from 'primeng/multiselect';
import {TableColumnPreferenceService} from './table-column-preference.service';
import {TieredMenu} from 'primeng/tieredmenu';
import {Badge} from 'primeng/badge';
import {BookMenuService} from '../../service/book-menu.service';
import {SidebarFilterTogglePrefService} from './filters/sidebar-filter-toggle-pref.service';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {TaskHelperService} from '../../../settings/task-management/task-helper.service';
import {FilterLabelHelper} from './filter-label.helper';
import {LoadingService} from '../../../../core/services/loading.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {BookNavigationService} from '../../service/book-navigation.service';
import {BookCardOverlayPreferenceService} from './book-card-overlay-preference.service';
import {BookSelectionService, CheckboxClickEvent} from './book-selection.service';
import {BookBrowserQueryParamsService, VIEW_MODES} from './book-browser-query-params.service';
import {BookBrowserEntityService, EntityInfo} from './book-browser-entity.service';
import {BookBrowserScrollService} from './book-browser-scroll.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MultiSortPopoverComponent} from './sorting/multi-sort-popover/multi-sort-popover.component';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {DeferredRenderState} from './deferred-render-state';

import {SortService} from '../../service/sort.service';
import {AppBooksApiService} from '../../service/app-books-api.service';
import {AppBookFilters} from '../../model/app-book.model';

export enum EntityType {
  LIBRARY = 'Library',
  SHELF = 'Shelf',
  MAGIC_SHELF = 'Magic Shelf',
  ALL_BOOKS = 'All Books',
  UNSHELVED = 'Unshelved Books',
}

@Component({
  selector: 'app-book-browser',
  standalone: true,
  templateUrl: './book-browser.component.html',
  styleUrls: ['./book-browser.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button, BookCardComponent, Menu, InputText, FormsModule,
    BookTableComponent, BookFilterComponent, Tooltip, NgClass, NgStyle, Popover,
    Checkbox, Slider, Divider, MultiSelect, TieredMenu, Badge, MultiSortPopoverComponent, TranslocoDirective,
  ],
  providers: [SeriesCollapseFilter],
})
export class BookBrowserComponent implements OnInit, AfterViewInit, OnDestroy {

  protected userService = inject(UserService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected columnPreferenceService = inject(TableColumnPreferenceService);
  protected sidebarFilterTogglePrefService = inject(SidebarFilterTogglePrefService);
  protected seriesCollapseFilter = inject(SeriesCollapseFilter);
  protected confirmationService = inject(ConfirmationService);
  protected taskHelperService = inject(TaskHelperService);
  protected bookCardOverlayPreferenceService = inject(BookCardOverlayPreferenceService);
  protected bookSelectionService = inject(BookSelectionService);
  protected appSettingsService = inject(AppSettingsService);

  private activatedRoute = inject(ActivatedRoute);
  private router = inject(Router);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private dialogHelperService = inject(BookDialogHelperService);
  private bookMenuService = inject(BookMenuService);
  private libraryShelfMenuService = inject(LibraryShelfMenuService);
  private pageTitle = inject(PageTitleService);
  private loadingService = inject(LoadingService);
  private bookNavigationService = inject(BookNavigationService);
  private queryParamsService = inject(BookBrowserQueryParamsService);
  private entityService = inject(BookBrowserEntityService);
  private sortService = inject(SortService);
  private appBooksApi = inject(AppBooksApiService);
  private localStorageService = inject(LocalStorageService);
  private scrollService = inject(BookBrowserScrollService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly defaultSortCriteria: SortOption[] = [{
    field: 'addedOn',
    direction: SortDirection.DESCENDING,
    label: 'Added On'
  }];
  private readonly routePath = toSignal(
    this.activatedRoute.url.pipe(
      map(() => this.activatedRoute.snapshot.routeConfig?.path ?? '')
    ),
    {initialValue: this.activatedRoute.snapshot.routeConfig?.path ?? ''}
  );
  private readonly routeParamMap = toSignal(this.activatedRoute.paramMap, {
    initialValue: this.activatedRoute.snapshot.paramMap
  });
  private readonly queryParamMap = toSignal(this.activatedRoute.queryParamMap, {
    initialValue: this.activatedRoute.snapshot.queryParamMap
  });
  private readonly searchTerm = signal('');
  private readonly debouncedSearchTerm = toSignal(
    toObservable(this.searchTerm).pipe(
      debounceTime(500),
      distinctUntilChanged()
    ),
    {initialValue: this.searchTerm()}
  );
  private readonly selectedFilter = signal<Record<string, string[]> | null>(null);
  private readonly selectedFilterMode = signal<BookFilterMode>('and');
  private readonly sortCriteria = signal<SortOption[]>(this.defaultSortCriteria);
  private readonly seriesCollapsed = this.seriesCollapseFilter.seriesCollapsed;
  readonly selectedBooks = this.bookSelectionService.selectedBooks;
  readonly selectedCount = this.bookSelectionService.selectedCount;
  readonly showFilter = this.sidebarFilterTogglePrefService.showFilter;
  private readonly currentUser$ = toObservable(this.userService.currentUser).pipe(filter(u => !!u));
  readonly entityInfo = computed<EntityInfo>(() => {
    const routePath = this.routePath();
    if (routePath === 'all-books') {
      return {entityId: NaN, entityType: EntityType.ALL_BOOKS};
    }
    if (routePath === 'unshelved-books') {
      return {entityId: NaN, entityType: EntityType.UNSHELVED};
    }
    return this.entityService.getEntityInfo(this.routeParamMap());
  });
  readonly entityType = computed(() => this.entityInfo().entityType);
  readonly entity = computed(() => {
    const {entityId, entityType} = this.entityInfo();
    return this.entityService.getEntity(entityId, entityType);
  });
  readonly entityOptions = computed<MenuItem[]>(() => {
    const entity = this.entity();
    if (!entity) {
      return [];
    }

    const actions = this.entityService.isLibrary(entity)
      ? this.libraryShelfMenuService.initializeLibraryMenuItems(entity)
      : this.entityService.isMagicShelf(entity)
        ? this.libraryShelfMenuService.initializeMagicShelfMenuItems(entity)
        : this.libraryShelfMenuService.initializeShelfMenuItems(entity);

    return actions;
  });
  // --- Server-side data pipeline: delegates filtering, sorting, searching to the API ---
  private readonly forceExpandSeries = computed(() =>
    this.queryParamsService.shouldForceExpandSeries(this.queryParamMap())
  );

  /** Sync entity scope, filters, search, and sort to the API service. */
  private readonly syncApiStateEffect = effect(() => {
    const {entityId, entityType} = this.entityInfo();
    const search = this.debouncedSearchTerm();
    const sidebarFilters = this.selectedFilter();
    const sortCriteria = this.sortCriteria();

    const scopeFilters: AppBookFilters = {};
    switch (entityType) {
      case EntityType.LIBRARY:
        if (!Number.isNaN(entityId)) scopeFilters.libraryId = entityId;
        break;
      case EntityType.SHELF:
        if (!Number.isNaN(entityId)) scopeFilters.shelfId = entityId;
        break;
      case EntityType.MAGIC_SHELF:
        if (!Number.isNaN(entityId)) scopeFilters.magicShelfId = entityId;
        break;
      case EntityType.UNSHELVED:
        scopeFilters.unshelved = true;
        break;
    }

    if (sidebarFilters) {
      for (const [type, values] of Object.entries(sidebarFilters)) {
        if (!values || values.length === 0) continue;
        const strValues = values.map(String);
        switch (type) {
          case 'author': scopeFilters.authors = strValues; break;
          case 'category': scopeFilters.category = strValues; break;
          case 'series': scopeFilters.series = strValues; break;
          case 'publisher': scopeFilters.publisher = strValues; break;
          case 'tag': scopeFilters.tag = strValues; break;
          case 'mood': scopeFilters.mood = strValues; break;
          case 'narrator': scopeFilters.narrator = strValues; break;
          case 'language': scopeFilters.language = strValues; break;
          case 'readStatus': scopeFilters.status = strValues; break;
          case 'bookType': scopeFilters.fileType = strValues; break;
          case 'ageRating': scopeFilters.ageRating = strValues; break;
          case 'contentRating': scopeFilters.contentRating = strValues; break;
          case 'matchScore': scopeFilters.matchScore = strValues; break;
          case 'publishedDate': scopeFilters.publishedDate = strValues; break;
          case 'fileSize': scopeFilters.fileSize = strValues; break;
          case 'personalRating': scopeFilters.personalRating = strValues; break;
          case 'amazonRating': scopeFilters.amazonRating = strValues; break;
          case 'goodreadsRating': scopeFilters.goodreadsRating = strValues; break;
          case 'hardcoverRating': scopeFilters.hardcoverRating = strValues; break;
          case 'pageCount': scopeFilters.pageCount = strValues; break;
          case 'shelfStatus':
            scopeFilters.shelfStatus = strValues;
            break;
          case 'comicCharacter': scopeFilters.comicCharacter = strValues; break;
          case 'comicTeam': scopeFilters.comicTeam = strValues; break;
          case 'comicLocation': scopeFilters.comicLocation = strValues; break;
          case 'comicCreator': scopeFilters.comicCreator = strValues; break;
          case 'shelf': scopeFilters.shelves = strValues; break;
          case 'library': scopeFilters.libraries = strValues; break;
        }
      }
      const mode = this.selectedFilterMode();
      scopeFilters.filterMode = mode === 'single' ? 'or' : mode;
    }

    this.appBooksApi.setFilters(scopeFilters);
    this.appBooksApi.setSearch(search);

    if (sortCriteria.length > 0) {
      const primary = sortCriteria[0];
      const dir = primary.direction === SortDirection.ASCENDING ? 'asc' as const : 'desc' as const;
      this.appBooksApi.setSort({field: primary.field, dir});
    }
  });

  // --- Deferred render: lets skeleton/refresh indicator paint before committing ---
  private readonly booksRenderState = new DeferredRenderState<Book[]>();
  readonly books = this.booksRenderState.value;
  readonly hasRenderedBooks = this.booksRenderState.hasValue;
  readonly isBooksRefreshing = this.booksRenderState.isRefreshing;

  private lastBooksContextKey: string | null = null;
  private readonly booksContextKey = computed(() => {
    const {entityId, entityType} = this.entityInfo();
    return Number.isNaN(entityId) ? entityType : `${entityType}:${entityId}`;
  });

  private readonly pipelineInputs = computed(() => this.appBooksApi.books());

  private readonly renderBooksEffect = effect((onCleanup) => {
    const contextKey = this.booksContextKey();
    const books = this.pipelineInputs();

    const shouldRefreshInPlace = untracked(() => this.hasRenderedBooks()) && contextKey === this.lastBooksContextKey;
    this.lastBooksContextKey = contextKey;

    if (shouldRefreshInPlace) {
      this.booksRenderState.update(books);
      return;
    }

    const requestId = this.booksRenderState.begin('reset');

    const timeout = globalThis.setTimeout(() => {
      this.booksRenderState.commit(requestId, books);
    });

    onCleanup(() => {
      clearTimeout(timeout);
      this.booksRenderState.cancel(requestId);
    });
  });
  readonly isBooksLoading = computed(() => this.appBooksApi.isLoading());
  readonly booksError = this.appBooksApi.error;
  readonly bookIndexById = computed(() => {
    const books = this.books();
    if (!books) return new Map<number, number>();
    const map = new Map<number, number>();
    for (let i = 0; i < books.length; i++) {
      map.set(books[i].id, i);
    }
    return map;
  });
  readonly compareBookItems = (a: Book, b: Book): boolean => a?.id === b?.id;

  private readonly GRID_GAP = 21;
  private readonly containerWidth = signal(0);
  private containerResizeObserver: ResizeObserver | undefined;

  readonly gridColumns = computed(() => {
    return computeGridColumns(this.containerWidth(), parseInt(this.gridColumnMinWidth(), 10) || 180, this.GRID_GAP);
  });

  /**
   * Estimated total content height based on totalElements from the first API page.
   * Used as min-height so the scrollbar reflects the full collection from the start.
   * Only grows, never shrinks — prevents scroll teleportation.
   */
  readonly estimatedTotalHeight = computed(() => {
    const total = this.appBooksApi.totalElements();
    if (total === 0) return 0;
    const cols = this.gridColumns();
    const rows = Math.ceil(total / cols);
    const rowHeight = this.currentCardSize().height + this.GRID_GAP;
    return rows * rowHeight;
  });

  /**
   * Total height of currently loaded books in grid view.
   */
  readonly renderedHeight = computed(() => {
    const books = this.books();
    if (!books || books.length === 0) return 0;
    const cols = this.gridColumns();
    const rows = Math.ceil(books.length / cols);
    const rowHeight = this.currentCardSize().height + this.GRID_GAP;
    return rows * rowHeight;
  });


  skeletonSlots = Array.from({length: 24}, (_, index) => index);
  readonly tableSkeletonRows = Array.from({length: 8}, (_, index) => index);
  readonly tableSkeletonColumns = Array.from({length: 5}, (_, index) => index);
  parsedFilters: Record<string, string[]> = {};
  bookTitle = '';
  dynamicDialogRef: DynamicDialogRef | undefined | null;
  EntityType = EntityType;

  private readonly activeLang = toSignal(this.t.langChanges$, {
    initialValue: this.t.getActiveLang()
  });

  readonly computedFilterLabel = computed(() => {
    this.activeLang();
    const filters = this.selectedFilter();

    if (!filters || Object.keys(filters).length === 0) {
      return this.t.translate('book.browser.labels.allBooks');
    }

    const filterEntries = Object.entries(filters);

    if (filterEntries.length === 1) {
      const [filterType, values] = filterEntries[0];
      const filterName = FilterLabelHelper.getFilterTypeName(filterType);

      if (values.length === 1) {
        const displayValue = FilterLabelHelper.getFilterDisplayValue(filterType, values[0]);
        return `${filterName}: ${displayValue}`;
      }

      return `${filterName} (${values.length})`;
    }

    const filterSummary = filterEntries
      .map(([type, values]) => `${FilterLabelHelper.getFilterTypeName(type)} (${values.length})`)
      .join(', ');

    return filterSummary.length > 50
      ? this.t.translate('book.browser.labels.activeFilters', {count: filterEntries.length})
      : filterSummary;
  });

  readonly currentFilterLabel = computed(() => this.computedFilterLabel());

  rawFilterParamFromUrl: string | null = null;
  visibleColumns: { field: string; header: string }[] = [];
  entityViewPreferences: EntityViewPreferences | undefined;
  currentViewMode = signal<string | undefined>(undefined);
  lastAppliedSortCriteria: SortOption[] = [];
  visibleSortOptions: SortOption[] = [];
  screenWidth = signal(typeof window !== 'undefined' ? window.innerWidth : 1024);
  mobileColumnCount = signal(3);

  private readonly MOBILE_BREAKPOINT = 768;
  private readonly CARD_ASPECT_RATIO = 7 / 5;
  private readonly MOBILE_GAP = 8;
  private readonly MOBILE_PADDING = 48;
  private readonly MOBILE_TITLE_BAR_HEIGHT = 32;
  private readonly MOBILE_COLUMNS_STORAGE_KEY = 'mobileColumnsPreference';

  private settingFiltersFromUrl = false;
  protected metadataMenuItems: MenuItem[] | undefined;
  protected moreActionsMenuItems: MenuItem[] | undefined;
  protected readonly onBookCardSelect = (book: Book, selected: boolean): void => {
    this.handleBookSelect(book, selected);
  };

  protected bookSorter = new BookSorter(
    sortCriteria => this.onMultiSortChange(sortCriteria),
    this.t
  );
  private readonly syncBrowserStateEffect = effect(() => {
    this.activeLang();
    const entityType = this.entityType();
    const entity = this.entity();

    if (entityType === EntityType.ALL_BOOKS) {
      this.pageTitle.setPageTitle(this.t.translate('book.browser.labels.allBooks'));
      this.seriesCollapseFilter.setContext(null, null);
      return;
    }

    if (entityType === EntityType.UNSHELVED) {
      this.pageTitle.setPageTitle(this.t.translate('book.browser.labels.unshelvedBooks'));
      this.seriesCollapseFilter.setContext(null, null);
      return;
    }

    if (entity) {
      this.pageTitle.setPageTitle(entity.name);
    }

    if (!entity) {
      this.seriesCollapseFilter.setContext(null, null);
      return;
    }

    switch (entityType) {
      case EntityType.LIBRARY:
        this.seriesCollapseFilter.setContext('LIBRARY', entity.id ?? 0);
        break;
      case EntityType.SHELF:
        this.seriesCollapseFilter.setContext('SHELF', entity.id ?? 0);
        break;
      case EntityType.MAGIC_SHELF:
        this.seriesCollapseFilter.setContext('MAGIC_SHELF', entity.id ?? 0);
        break;
      default:
        this.seriesCollapseFilter.setContext(null, null);
    }
  });
  private readonly syncBooksEffect = effect(() => {
    const books = this.books();
    if (!books) {
      this.bookSelectionService.setCurrentBooks([]);
      this.bookNavigationService.setAvailableBookIds([]);
      return;
    }

    this.bookSelectionService.setCurrentBooks(books);
    this.bookNavigationService.setAvailableBookIds(books.map(book => book.id));
  });
  private readonly syncMoreActionsMenuEffect = effect(() => {
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(
      this.selectedBooks(),
      this.userService.currentUser()
    );
  });

  /**
   * Triggers sequential page loads if the current scroll position is beyond the loaded content.
   * This is critical for scroll restoration on page reload.
   */
  private readonly fillScrollGapEffect = effect(() => {
    const books = this.books();
    if (books && books.length > 0) {
      this.checkAndFetchIfNeeded();
    }
  });

  @ViewChild(BookTableComponent)
  bookTableComponent!: BookTableComponent;
  @ViewChild(BookFilterComponent, {static: true})
  bookFilterComponent!: BookFilterComponent;

  private scrollContainer: HTMLElement | undefined;
  private sentinelObserver: IntersectionObserver | undefined;

  @ViewChild('scrollContainer')
  set scrollContainerRef(ref: ElementRef<HTMLElement> | undefined) {
    this.containerResizeObserver?.disconnect();
    if (this.scrollContainer) {
      this.scrollContainer.removeEventListener('scroll', this.onScroll);
    }
    this.scrollContainer = ref?.nativeElement;
    if (this.scrollContainer) {
      const el = this.scrollContainer;
      this.containerWidth.set(el.clientWidth);
      this.containerResizeObserver = new ResizeObserver(entries => {
        this.containerWidth.set(entries[0]?.contentRect.width ?? el.clientWidth);
      });
      this.containerResizeObserver.observe(el);
      el.addEventListener('scroll', this.onScroll, {passive: true});
      // Initial check in case we are already scrolled down
      globalThis.requestAnimationFrame(() => this.checkAndFetchIfNeeded());
    }
  }

  @ViewChild('scrollSentinel')
  set scrollSentinelRef(ref: ElementRef<HTMLElement> | undefined) {
    this.sentinelObserver?.disconnect();
    const el = ref?.nativeElement;
    if (el) {
      this.sentinelObserver = new IntersectionObserver(
        entries => {
          if (entries[0]?.isIntersecting) {
            this.checkAndFetchNextPage();
          }
        },
        {root: this.scrollContainer, rootMargin: '600px'}
      );
      this.sentinelObserver.observe(el);
    }
  }

  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth.set(window.innerWidth);
  }

  readonly isMobile = computed(() => this.screenWidth() < this.MOBILE_BREAKPOINT);

  readonly mobileCardSize = computed(() => {
    const columns = this.mobileColumnCount();
    const totalGaps = (columns - 1) * this.MOBILE_GAP;
    const availableWidth = this.screenWidth() - totalGaps - this.MOBILE_PADDING;
    const cardWidth = Math.floor(availableWidth / columns);
    const coverHeight = this.isAudiobookOnlyLibrary() ? cardWidth : Math.floor(cardWidth * this.CARD_ASPECT_RATIO);
    const cardHeight = coverHeight + this.MOBILE_TITLE_BAR_HEIGHT;
    return {width: cardWidth, height: cardHeight};
  });

  readonly currentCardSize = computed(() => {
    if (this.isMobile()) {
      return this.mobileCardSize();
    }
    const base = this.coverScalePreferenceService.currentCardSize();
    if (this.isAudiobookOnlyLibrary()) {
      const squareSide = Math.round(base.width * 1.1);
      return { width: squareSide, height: squareSide + 31 };
    }
    return base;
  });

  readonly gridColumnMinWidth = computed(() => {
    if (this.isMobile()) {
      return `${this.mobileCardSize().width}px`;
    }
    if (this.isAudiobookOnlyLibrary()) {
      return `${this.currentCardSize().width}px`;
    }
    return this.coverScalePreferenceService.gridColumnMinWidth();
  });

  getCardHeight(_book: Book): number {
    if (this.isMobile()) {
      return this.mobileCardSize().height;
    }
    if (this.isAudiobookOnlyLibrary()) {
      return this.currentCardSize().height;
    }
    return this.coverScalePreferenceService.getCardHeight(_book);
  }

  readonly showBooksLoadingPlaceholder = computed(() =>
    !this.booksError() && !this.hasRenderedBooks()
  );

  readonly showTableLoadingPlaceholder = computed(() =>
    this.showBooksLoadingPlaceholder() && this.currentViewMode() === VIEW_MODES.TABLE
  );

  readonly showGridLoadingPlaceholder = computed(() =>
    this.showBooksLoadingPlaceholder() && this.currentViewMode() === VIEW_MODES.GRID
  );

  readonly viewIcon = computed(() =>
    this.currentViewMode() === VIEW_MODES.TABLE ? 'pi pi-table' : 'pi pi-objects-column'
  );

  readonly isFilterActive = computed(() => {
    const selectedFilter = this.selectedFilter();
    return !!selectedFilter && Object.keys(selectedFilter).length > 0;
  });

  readonly isAudiobookOnlyLibrary = computed(() => {
    const entity = this.entity();
    if (!entity || this.entityType() !== EntityType.LIBRARY) return false;
    const library = entity as Library;
    return !!library.allowedFormats && library.allowedFormats.length === 1 && library.allowedFormats[0] === 'AUDIOBOOK';
  });

  get seriesViewEnabled(): boolean {
    return Boolean(this.userService.getCurrentUser()?.userSettings?.enableSeriesView);
  }

  get hasMetadataMenuItems(): boolean {
    return (this.metadataMenuItems?.length ?? 0) > 0;
  }

  get hasMoreActionsItems(): boolean {
    return (this.moreActionsMenuItems?.length ?? 0) > 0;
  }

  ngOnInit(): void {
    this.loadMobileColumnsPreference();
    this.setupRouteChangeHandlers();
    this.setupQueryParamSubscription();
    this.setupScrollPositionTracking();
  }

  ngAfterViewInit(): void {
    if (this.bookFilterComponent) {
      this.bookFilterComponent.setFilters(this.parsedFilters);
      this.bookFilterComponent.onFilterModeChange(this.selectedFilterMode());
    }
  }

  ngOnDestroy(): void {
    if (this.scrollContainer) {
      this.scrollContainer.removeEventListener('scroll', this.onScroll);
    }
    this.containerResizeObserver?.disconnect();
    this.sentinelObserver?.disconnect();
  }

  private onScroll = (): void => {
    this.checkAndFetchIfNeeded();
  };

  /**
   * Checks if the current scroll position plus viewport exceeds the rendered height (with a buffer)
   * and fetches the next page if necessary.
   */
  private checkAndFetchIfNeeded(): void {
    if (!this.scrollContainer || this.currentViewMode() !== VIEW_MODES.GRID) return;

    const {scrollTop, clientHeight} = this.scrollContainer;
    const buffer = 1000; // Large buffer to facilitate scroll restoration
    const renderedHeight = untracked(() => this.renderedHeight());

    if (scrollTop + clientHeight >= renderedHeight - buffer) {
      untracked(() => this.checkAndFetchNextPage());
    }
  }

  private checkAndFetchNextPage(): void {
    if (this.appBooksApi.hasNextPage() && !this.appBooksApi.isFetchingNextPage()) {
      this.appBooksApi.fetchNextPage();
    }
  }

  private getScrollPositionKey(): string {
    const path = this.activatedRoute.snapshot.routeConfig?.path ?? '';
    return this.scrollService.createKey(path, this.activatedRoute.snapshot.params);
  }

  private setupScrollPositionTracking(): void {
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.saveScrollPosition();
    });
  }

  private saveScrollPosition(): void {
    if (this.scrollContainer) {
      const key = this.getScrollPositionKey();
      this.scrollService.savePosition(key, this.scrollContainer.scrollTop);
    }
  }

  private setupRouteChangeHandlers(): void {
    this.activatedRoute.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.searchTerm.set('');
      this.bookTitle = '';
      this.bookSelectionService.deselectAll();
      this.clearFilter();
      this.scrollToTop();
    });
  }

  private scrollToTop(): void {
    if (this.scrollContainer) {
      this.scrollContainer.scrollTop = 0;
    }
    if (this.bookTableComponent) {
      this.bookTableComponent.scrollToTop();
    }
  }

  private readonly syncMetadataMenuEffect = effect(() => {
    const user = this.userService.currentUser();
    if (!user) return;

    this.metadataMenuItems = this.bookMenuService.getMetadataMenuItems(
      () => this.autoFetchMetadata(),
      () => this.fetchMetadata(),
      () => this.bulkEditMetadata(),
      () => this.multiBookEditMetadata(),
      () => this.regenerateCoversForSelected(),
      () => this.generateCustomCoversForSelected(),
      user
    );
  });

  private setupQueryParamSubscription(): void {
    combineLatest([
      this.activatedRoute.paramMap.pipe(map(() => this.entityInfo())),
      this.activatedRoute.queryParamMap,
      this.currentUser$,
    ]).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(([entityInfo, queryParamMap, currentUser]) => {
      const parseResult = this.queryParamsService.parseQueryParams(
        queryParamMap,
        currentUser.userSettings?.entityViewPreferences,
        entityInfo.entityType,
        entityInfo.entityId,
        this.bookSorter.sortOptions,
        currentUser.userSettings?.filterMode ?? 'and'
      );


      if (parseResult.filterMode !== this.selectedFilterMode()) {
        this.selectedFilterMode.set(parseResult.filterMode);
        if (this.bookFilterComponent) {
          this.bookFilterComponent.onFilterModeChange(parseResult.filterMode);
        }
      }

      const filterParams = queryParamMap.get('filter');

      if (filterParams) {
        this.settingFiltersFromUrl = true;
        this.selectedFilter.set(parseResult.filters);

        if (this.bookFilterComponent) {
          this.bookFilterComponent.setFilters(parseResult.filters);
        }

        this.rawFilterParamFromUrl = filterParams;
        this.settingFiltersFromUrl = false;
      } else {
        this.clearFilter();
        this.rawFilterParamFromUrl = null;
      }

      this.parsedFilters = parseResult.filters;


      this.entityViewPreferences = currentUser.userSettings?.entityViewPreferences;
      this.columnPreferenceService.initPreferences(currentUser.userSettings?.tableColumnPreference);
      this.visibleColumns = this.columnPreferenceService.visibleColumns;

      const visibleFields = currentUser.userSettings?.visibleSortFields ?? DEFAULT_VISIBLE_SORT_FIELDS;
      const sortOptionsByField = new Map(this.bookSorter.sortOptions.map(o => [o.field, o]));
      this.visibleSortOptions = visibleFields.map(f => sortOptionsByField.get(f)).filter((o): o is SortOption => !!o);


      if (!this.areSortCriteriaEqual(this.bookSorter.selectedSortCriteria, parseResult.sortCriteria)) {
        this.bookSorter.setSortCriteria(parseResult.sortCriteria);
      }
      this.currentViewMode.set(parseResult.viewMode);

      if (!this.areSortCriteriaEqual(this.lastAppliedSortCriteria, this.bookSorter.selectedSortCriteria)) {
        this.lastAppliedSortCriteria = [...this.bookSorter.selectedSortCriteria];
        this.applySortCriteria(this.bookSorter.selectedSortCriteria);
      }


      this.queryParamsService.syncQueryParams(
        this.currentViewMode()!,
        this.selectedFilterMode(),
        this.parsedFilters
      );
    });
  }

  onFilterSelected(filters: Record<string, unknown> | null): void {
    if (this.settingFiltersFromUrl) return;

    const normalizedFilters = filters
      ? Object.fromEntries(
          Object.entries(filters).map(([key, value]) => [
            key,
            (Array.isArray(value) ? value : [value]).map(filterValue => String(filterValue))
          ])
        )
      : null;

    this.selectedFilter.set(normalizedFilters);
    this.rawFilterParamFromUrl = null;

    this.queryParamsService.updateFilters(normalizedFilters);
  }

  onFilterModeChanged(mode: BookFilterMode): void {
    if (this.settingFiltersFromUrl || mode === this.selectedFilterMode()) return;

    this.selectedFilterMode.set(mode);
    this.queryParamsService.updateFilterMode(mode, this.parsedFilters);
  }

  toggleSidebar(): void {
    this.sidebarFilterTogglePrefService.toggle();
  }

  onVisibleColumnsChange(selected: { field: string; header: string }[]): void {
    const allFields = this.bookTableComponent.allColumns.map(col => col.field);
    this.visibleColumns = selected.sort(
      (a, b) => allFields.indexOf(a.field) - allFields.indexOf(b.field)
    );
  }

  onCheckboxClicked(event: CheckboxClickEvent): void {
    this.bookSelectionService.handleCheckboxClick(event);
  }

  handleBookSelect(book: Book, selected: boolean): void {
    this.bookSelectionService.handleBookSelection(book, selected);
  }

  onSelectedBooksChange(selectedBookIds: Set<number>): void {
    this.bookSelectionService.setSelectedBooks(selectedBookIds);
  }

  selectAllBooks(): void {
    this.appBooksApi.fetchAllBookIds().pipe(take(1)).subscribe({
      next: (allIds) => {
        this.bookSelectionService.selectAll(allIds);
        if (this.bookTableComponent) {
          this.bookTableComponent.selectAllBooks();
        }
      },
      error: () => {
        this.bookSelectionService.selectAll();
        if (this.bookTableComponent) {
          this.bookTableComponent.selectAllBooks();
        }
      }
    });
  }

  deselectAllBooks(): void {
    this.bookSelectionService.deselectAll();
    if (this.bookTableComponent) {
      this.bookTableComponent.clearSelectedBooks();
    }
  }

  confirmDeleteBooks(): void {
    const selectedBooks = this.selectedBooks();
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.deleteMessage', {count: selectedBooks.size}),
      header: this.t.translate('book.browser.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptLabel: this.t.translate('common.delete'),
      rejectLabel: this.t.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-outlined',
      accept: () => {
        const count = selectedBooks.size;
        const loader = this.loadingService.show(this.t.translate('book.browser.loading.deleting', {count}));

        this.bookService.deleteBooks(selectedBooks)
          .pipe(finalize(() => this.loadingService.hide(loader)))
          .subscribe(() => {
            this.bookSelectionService.deselectAll();
          });
      }
    });
  }

  onSeriesCollapseCheckboxChange(value: boolean): void {
    this.seriesCollapseFilter.setCollapsed(value);
  }

  onMultiSortChange(sortCriteria: SortOption[]): void {
    this.applySortCriteria(sortCriteria);
    this.queryParamsService.updateMultiSort(sortCriteria);
  }

  // Backward compatibility wrapper
  onManualSortChange(sortOption: SortOption): void {
    this.onMultiSortChange([sortOption]);
  }

  applySortCriteria(sortCriteria: SortOption[]): void {
    this.sortCriteria.set(sortCriteria.length > 0 ? sortCriteria : this.defaultSortCriteria);
  }

  // Backward compatibility wrapper
  applySortOption(sortOption: SortOption): void {
    this.applySortCriteria([sortOption]);
  }

  private areSortCriteriaEqual(a: SortOption[], b: SortOption[]): boolean {
    if (a.length !== b.length) return false;
    return a.every((criterion, index) =>
      criterion.field === b[index].field && criterion.direction === b[index].direction
    );
  }

  onSortCriteriaChange(criteria: SortOption[]): void {
    this.bookSorter.setSortCriteria(criteria);
    this.onMultiSortChange(criteria);
  }

  readonly canSaveSort = computed(() => {
    const entityType = this.entityType();
    return entityType === EntityType.LIBRARY ||
           entityType === EntityType.SHELF ||
           entityType === EntityType.MAGIC_SHELF ||
           entityType === EntityType.ALL_BOOKS ||
           entityType === EntityType.UNSHELVED;
  });

  readonly hasSearchTerm = computed(() => this.searchTerm().trim().length > 0);

  onSaveSortConfig(criteria: SortOption[]): void {
    const entityType = this.entityType();
    if (!entityType) return;

    const user = this.userService.getCurrentUser();
    if (!user) return;
    const entity = this.entity();

    const sortCriteria: SortCriterion[] = criteria.map(c => ({
      field: c.field,
      direction: c.direction === SortDirection.ASCENDING ? 'ASC' as const : 'DESC' as const
    }));

    const prefs: EntityViewPreferences = structuredClone(
      user.userSettings.entityViewPreferences ?? {global: {sortKey: 'title', sortDir: 'ASC', view: 'GRID', coverSize: 1.0, seriesCollapsed: false, overlayBookType: true}, overrides: []}
    );

    if (entityType === EntityType.ALL_BOOKS || entityType === EntityType.UNSHELVED) {
      prefs.global = {
        ...prefs.global,
        sortKey: sortCriteria[0]?.field ?? 'title',
        sortDir: sortCriteria[0]?.direction ?? 'ASC',
        sortCriteria
      };
    } else {
      if (!entity) return;
      if (!prefs.overrides) prefs.overrides = [];

      let overrideEntityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF';
      switch (entityType) {
        case EntityType.LIBRARY: overrideEntityType = 'LIBRARY'; break;
        case EntityType.SHELF: overrideEntityType = 'SHELF'; break;
        case EntityType.MAGIC_SHELF: overrideEntityType = 'MAGIC_SHELF'; break;
        default: return;
      }

      const existingIndex = prefs.overrides.findIndex(
        o => o.entityType === overrideEntityType && o.entityId === entity.id
      );

      if (existingIndex >= 0) {
        prefs.overrides[existingIndex].preferences = {
          ...prefs.overrides[existingIndex].preferences,
          sortKey: sortCriteria[0]?.field ?? 'title',
          sortDir: sortCriteria[0]?.direction ?? 'ASC',
          sortCriteria
        };
      } else {
        prefs.overrides.push({
          entityType: overrideEntityType,
          entityId: entity.id!,
          preferences: {
            sortKey: sortCriteria[0]?.field ?? 'title',
            sortDir: sortCriteria[0]?.direction ?? 'ASC',
            sortCriteria,
            view: 'GRID',
            coverSize: 1.0,
            seriesCollapsed: false,
            overlayBookType: true
          }
        });
      }
    }

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('book.browser.toast.sortSavedSummary'),
      detail: entityType === EntityType.ALL_BOOKS || entityType === EntityType.UNSHELVED
        ? this.t.translate('book.browser.toast.sortSavedGlobalDetail')
        : this.t.translate('book.browser.toast.sortSavedEntityDetail', {entityType: entityType.toLowerCase()})
    });
  }

  get sortCriteriaCount(): number {
    return this.bookSorter.selectedSortCriteria.length;
  }

  onSearchTermChange(term: string): void {
    this.searchTerm.set(term);
  }

  clearSearch(): void {
    this.bookTitle = '';
    this.onSearchTermChange('');
    this.resetFilters();
  }

  resetFilters(): void {
    this.bookFilterComponent?.clearActiveFilter();
  }

  clearFilter(): void {
    if (this.selectedFilter() !== null) {
      this.selectedFilter.set(null);
    }
    this.clearSearch();
  }

  toggleTableGrid(): void {
    const newMode = this.currentViewMode() === VIEW_MODES.GRID ? VIEW_MODES.TABLE : VIEW_MODES.GRID;
    this.currentViewMode.set(newMode);
    this.queryParamsService.updateViewMode(newMode as 'grid' | 'table');
  }

  onViewModeChange(mode: string): void {
    if (mode && mode !== this.currentViewMode()) {
      this.currentViewMode.set(mode);
      this.queryParamsService.updateViewMode(mode as 'grid' | 'table');
    }
  }

  unshelfBooks(): void {
    const entity = this.entity();
    if (!entity) return;
    const selectedBooks = this.selectedBooks();
    const count = selectedBooks.size;
    const loader = this.loadingService.show(this.t.translate('book.browser.loading.unshelving', {count}));

    this.bookService.updateBookShelves(selectedBooks, new Set(), new Set([entity.id!]))
      .pipe(finalize(() => this.loadingService.hide(loader)))
      .subscribe({
        next: () => {
          this.messageService.add({severity: 'info', summary: this.t.translate('common.success'), detail: this.t.translate('book.browser.toast.unshelveSuccessDetail')});
          this.bookSelectionService.deselectAll();
        },
        error: () => {
          this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('book.browser.toast.unshelveFailedDetail')});
        }
      });
  }

  openShelfAssigner(): void {
    this.dynamicDialogRef = this.dialogHelperService.openShelfAssignerDialog(null, this.selectedBooks());
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.pipe(take(1)).subscribe(result => {
        if (result?.assigned) {
          this.bookSelectionService.deselectAll();
        }
      });
    }
  }

  lockUnlockMetadata(): void {
    this.dynamicDialogRef = this.dialogHelperService.openLockUnlockMetadataDialog(this.selectedBooks());
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.pipe(take(1)).subscribe(() => {
        this.bookSelectionService.deselectAll();
      });
    }
  }

  autoFetchMetadata(): void {
    const selectedBooks = this.selectedBooks();
    if (selectedBooks.size === 0) return;
    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: Array.from(selectedBooks),
    }).subscribe();
  }

  fetchMetadata(): void {
    this.dialogHelperService.openMetadataRefreshDialog(this.selectedBooks());
  }

  bulkEditMetadata(): void {
    this.dynamicDialogRef = this.dialogHelperService.openBulkMetadataEditDialog(this.selectedBooks());
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.pipe(take(1)).subscribe(() => {
        this.bookSelectionService.deselectAll();
      });
    }
  }

  multiBookEditMetadata(): void {
    this.dynamicDialogRef = this.dialogHelperService.openMultibookMetadataEditorDialog(this.selectedBooks());
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.pipe(take(1)).subscribe(() => {
        this.bookSelectionService.deselectAll();
      });
    }
  }

  regenerateCoversForSelected(): void {
    const selectedBooks = this.selectedBooks();
    if (selectedBooks.size === 0) return;
    const count = selectedBooks.size;
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.regenCoverMessage', {count}),
      header: this.t.translate('book.browser.confirm.regenCoverHeader'),
      icon: 'pi pi-image',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {
        label: this.t.translate('common.yes'),
        severity: 'success'
      },
      rejectButtonProps: {
        label: this.t.translate('common.no'),
        severity: 'secondary'
      },
      accept: () => {
        this.bookMetadataManageService.regenerateCoversForBooks(Array.from(selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.browser.toast.regenCoverStartedSummary'),
              detail: this.t.translate('book.browser.toast.regenCoverStartedDetail', {count}),
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.browser.toast.failedSummary'),
              detail: this.t.translate('book.browser.toast.regenCoverFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  generateCustomCoversForSelected(): void {
    const selectedBooks = this.selectedBooks();
    if (selectedBooks.size === 0) return;
    const count = selectedBooks.size;
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.customCoverMessage', {count}),
      header: this.t.translate('book.browser.confirm.customCoverHeader'),
      icon: 'pi pi-palette',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {
        label: this.t.translate('common.yes'),
        severity: 'success'
      },
      rejectButtonProps: {
        label: this.t.translate('common.no'),
        severity: 'secondary'
      },
      accept: () => {
        this.bookMetadataManageService.generateCustomCoversForBooks(Array.from(selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.browser.toast.customCoverStartedSummary'),
              detail: this.t.translate('book.browser.toast.customCoverStartedDetail', {count}),
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.browser.toast.failedSummary'),
              detail: this.t.translate('book.browser.toast.customCoverFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  moveFiles(): void {
    this.dialogHelperService.openFileMoverDialog(this.selectedBooks());
  }

  attachFilesToBook(): void {
    const selectedBookIds = Array.from(this.selectedBooks());
    const sourceBooks = (this.books() ?? []).filter(book =>
      selectedBookIds.includes(book.id)
    );

    if (sourceBooks.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('book.browser.toast.noEligibleBooksSummary'),
        detail: this.t.translate('book.browser.toast.noEligibleBooksDetail')
      });
      return;
    }

    // Check if all books are from the same library
    const libraryIds = new Set(sourceBooks.map(b => b.libraryId));
    if (libraryIds.size > 1) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('book.browser.toast.multipleLibrariesSummary'),
        detail: this.t.translate('book.browser.toast.multipleLibrariesDetail')
      });
      return;
    }

    this.dynamicDialogRef = this.dialogHelperService.openBulkBookFileAttacherDialog(sourceBooks);
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.pipe(take(1)).subscribe(result => {
        if (result?.success) {
          this.bookSelectionService.deselectAll();
        }
      });
    }
  }

  canAttachFiles(): boolean {
    const selectedBookIds = Array.from(this.selectedBooks());
    if (selectedBookIds.length === 0) return false;

    const selectedBooks = (this.books() ?? []).filter(book =>
      selectedBookIds.includes(book.id)
    );

    if (selectedBooks.length === 0) return false;

    const libraryIds = new Set(selectedBooks.map(b => b.libraryId));
    return libraryIds.size === 1;
  }

  setMobileColumns(columns: number): void {
    this.mobileColumnCount.set(columns);
    this.localStorageService.set(this.MOBILE_COLUMNS_STORAGE_KEY, columns);
  }

  private loadMobileColumnsPreference(): void {
    const saved = this.localStorageService.get<number>(this.MOBILE_COLUMNS_STORAGE_KEY);
    if (saved !== null && [2, 3, 4].includes(saved)) {
      this.mobileColumnCount.set(saved);
    }
  }
}
