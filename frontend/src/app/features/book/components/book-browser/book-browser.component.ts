import {AfterViewInit, Component, HostListener, computed, effect, inject, OnDestroy, OnInit, signal, untracked, ViewChild} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, NavigationStart, Router} from '@angular/router';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {debounceTime, distinctUntilChanged, filter, map, takeUntil} from 'rxjs/operators';
import {combineLatest, finalize, Subject} from 'rxjs';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Library} from '../../model/library.model';
import {SortDirection, SortOption} from '../../model/sort.model';
import {Book} from '../../model/book.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {BookTableComponent} from './book-table/book-table.component';
import {animate, style, transition, trigger} from '@angular/animations';
import {Button} from 'primeng/button';
import {NgClass, NgStyle} from '@angular/common';
import {VirtualScrollerComponent, VirtualScrollerModule} from '@iharbeck/ngx-virtual-scroller';
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
import {BadgeModule} from 'primeng/badge';
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
import {filterBooksBySearchTerm} from './filters/HeaderFilter';
import {filterBooksByFilters} from './filters/sidebar-filter';

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
  imports: [
    Button, VirtualScrollerModule, BookCardComponent, Menu, InputText, FormsModule,
    BookTableComponent, BookFilterComponent, Tooltip, NgClass, NgStyle, Popover,
    Checkbox, Slider, Divider, MultiSelect, TieredMenu, BadgeModule, MultiSortPopoverComponent, TranslocoDirective
  ],
  providers: [SeriesCollapseFilter],
  animations: [
    trigger('slideInOut', [
      transition(':enter', [
        style({transform: 'translateY(100%)'}),
        animate('0.1s ease-in', style({transform: 'translateY(0)'}))
      ]),
      transition(':leave', [
        style({transform: 'translateY(0)'}),
        animate('0.1s ease-out', style({transform: 'translateY(100%)'}))
      ])
    ])
  ]
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
  private localStorageService = inject(LocalStorageService);
  private scrollService = inject(BookBrowserScrollService);
  private readonly t = inject(TranslocoService);

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
  // --- Layered pipeline: each computed only recomputes when its direct inputs change ---
  private readonly entityBooks = computed(() => {
    const {entityId, entityType} = this.entityInfo();
    return this.entityService.getBooksByEntity(this.bookService.books(), entityId, entityType);
  });
  private readonly searchedBooks = computed(() =>
    filterBooksBySearchTerm(this.entityBooks(), this.debouncedSearchTerm())
  );
  private readonly filteredBooks = computed(() =>
    filterBooksByFilters(this.searchedBooks(), this.selectedFilter(), this.selectedFilterMode())
  );
  private readonly forceExpandSeries = computed(() =>
    this.queryParamsService.shouldForceExpandSeries(this.queryParamMap())
  );
  private readonly collapsedBooks = computed(() =>
    this.seriesCollapseFilter.collapseBooks(
      this.filteredBooks(), this.forceExpandSeries(), this.seriesCollapsed()
    )
  );
  private readonly sortedBooks = computed(() =>
    this.sortService.applyMultiSort(this.collapsedBooks(), this.sortCriteria())
  );

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

  private readonly pipelineInputs = computed(() => ({
    books: this.bookService.books(),
    entity: this.entityInfo(),
    search: this.debouncedSearchTerm(),
    filter: this.selectedFilter(),
    filterMode: this.selectedFilterMode(),
    collapsed: this.seriesCollapsed(),
    forceExpand: this.forceExpandSeries(),
    sort: this.sortCriteria(),
  }));

  private readonly renderBooksEffect = effect((onCleanup) => {
    const contextKey = this.booksContextKey();
    this.pipelineInputs();

    const shouldRefreshInPlace = untracked(() => this.hasRenderedBooks()) && contextKey === this.lastBooksContextKey;
    this.lastBooksContextKey = contextKey;

    if (shouldRefreshInPlace) {
      // Same context (sort/filter/search change within the same entity).
      // Commit synchronously — the memoized computed chain is fast and
      // skipping the setTimeout avoids a one-frame skeleton flash.
      const requestId = this.booksRenderState.begin('refresh');
      this.booksRenderState.commit(requestId, this.sortedBooks());
      return;
    }

    // Context changed (navigation between entities or first load).
    // Defer so the skeleton can paint before the pipeline evaluates.
    const requestId = this.booksRenderState.begin('reset');

    const timeout = globalThis.setTimeout(() => {
      this.booksRenderState.commit(requestId, this.sortedBooks());
    });

    onCleanup(() => {
      clearTimeout(timeout);
      this.booksRenderState.cancel(requestId);
    });
  });
  readonly isBooksLoading = this.bookService.isBooksLoading;
  readonly booksError = this.bookService.booksError;
  readonly bookIndexById = computed(() => {
    const books = this.books();
    if (!books) return new Map<number, number>();
    const map = new Map<number, number>();
    for (let i = 0; i < books.length; i++) {
      map.set(books[i].id, i);
    }
    return map;
  });
  protected resetFilterSubject = new Subject<void>();

  readonly skeletonSlots = Array.from({length: 24}, (_, index) => index);
  readonly tableSkeletonRows = Array.from({length: 8}, (_, index) => index);
  readonly tableSkeletonColumns = Array.from({length: 5}, (_, index) => index);
  parsedFilters: Record<string, string[]> = {};
  bookTitle = '';
  dynamicDialogRef: DynamicDialogRef | undefined | null;
  EntityType = EntityType;
  currentFilterLabel: string | null = null;
  rawFilterParamFromUrl: string | null = null;
  visibleColumns: { field: string; header: string }[] = [];
  entityViewPreferences: EntityViewPreferences | undefined;
  currentViewMode: string | undefined;
  lastAppliedSortCriteria: SortOption[] = [];
  visibleSortOptions: SortOption[] = [];
  screenWidth = typeof window !== 'undefined' ? window.innerWidth : 1024;
  mobileColumnCount = 3;

  private readonly MOBILE_BREAKPOINT = 768;
  private readonly CARD_ASPECT_RATIO = 7 / 5;
  private readonly MOBILE_GAP = 8;
  private readonly MOBILE_PADDING = 48;
  private readonly MOBILE_TITLE_BAR_HEIGHT = 32;
  private readonly MOBILE_COLUMNS_STORAGE_KEY = 'mobileColumnsPreference';

  private settingFiltersFromUrl = false;
  private destroy$ = new Subject<void>();
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

  @ViewChild(BookTableComponent)
  bookTableComponent!: BookTableComponent;
  @ViewChild(BookFilterComponent, {static: false})
  bookFilterComponent!: BookFilterComponent;
  @ViewChild('scroll')
  virtualScroller: VirtualScrollerComponent | undefined;

  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth = window.innerWidth;
  }

  get isMobile(): boolean {
    return this.screenWidth < this.MOBILE_BREAKPOINT;
  }

  get mobileCardSize(): { width: number; height: number } {
    const columns = this.mobileColumnCount;
    const totalGaps = (columns - 1) * this.MOBILE_GAP;
    const availableWidth = this.screenWidth - totalGaps - this.MOBILE_PADDING;
    const cardWidth = Math.floor(availableWidth / columns);
    const coverHeight = this.isAudiobookOnlyLibrary ? cardWidth : Math.floor(cardWidth * this.CARD_ASPECT_RATIO);
    const cardHeight = coverHeight + this.MOBILE_TITLE_BAR_HEIGHT;
    return {width: cardWidth, height: cardHeight};
  }

  get currentCardSize() {
    if (this.isMobile) {
      return this.mobileCardSize;
    }
    const base = this.coverScalePreferenceService.currentCardSize();
    if (this.isAudiobookOnlyLibrary) {
      const squareSide = Math.round(base.width * 1.1);
      return { width: squareSide, height: squareSide + 31 };
    }
    return base;
  }

  get gridColumnMinWidth(): string {
    if (this.isMobile) {
      return `${this.mobileCardSize.width}px`;
    }
    if (this.isAudiobookOnlyLibrary) {
      return `${this.currentCardSize.width}px`;
    }
    return this.coverScalePreferenceService.gridColumnMinWidth();
  }

  getCardHeight(_book: Book): number {
    if (this.isMobile) {
      return this.mobileCardSize.height;
    }
    if (this.isAudiobookOnlyLibrary) {
      return this.currentCardSize.height;
    }
    return this.coverScalePreferenceService.getCardHeight(_book);
  }

  get showBooksLoadingPlaceholder(): boolean {
    return !this.booksError() && (this.isBooksLoading() || !this.hasRenderedBooks());
  }

  get showTableLoadingPlaceholder(): boolean {
    return this.showBooksLoadingPlaceholder && this.currentViewMode === VIEW_MODES.TABLE;
  }

  get showGridLoadingPlaceholder(): boolean {
    return this.showBooksLoadingPlaceholder && this.currentViewMode !== VIEW_MODES.TABLE;
  }

  get viewIcon(): string {
    return this.currentViewMode === VIEW_MODES.TABLE ? 'pi pi-table' : 'pi pi-objects-column';
  }

  get isFilterActive(): boolean {
    const selectedFilter = this.selectedFilter();
    return !!selectedFilter && Object.keys(selectedFilter).length > 0;
  }

  get computedFilterLabel(): string {
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
  }

  get isAudiobookOnlyLibrary(): boolean {
    const entity = this.entity();
    if (!entity || this.entityType() !== EntityType.LIBRARY) return false;
    const library = entity as Library;
    return !!library.allowedFormats && library.allowedFormats.length === 1 && library.allowedFormats[0] === 'AUDIOBOOK';
  }

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
      this.bookFilterComponent.setFilters?.(this.parsedFilters);
      this.bookFilterComponent.onFiltersChanged?.();
      this.bookFilterComponent.selectedFilterMode = this.selectedFilterMode();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private getScrollPositionKey(): string {
    const path = this.activatedRoute.snapshot.routeConfig?.path ?? '';
    return this.scrollService.createKey(path, this.activatedRoute.snapshot.params);
  }

  private setupScrollPositionTracking(): void {
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.saveScrollPosition();
    });
  }

  private saveScrollPosition(): void {
    if (this.virtualScroller?.viewPortInfo) {
      const key = this.getScrollPositionKey();
      const position = this.virtualScroller.viewPortInfo.scrollStartPosition ?? 0;
      this.scrollService.savePosition(key, position);
    }
  }

  private setupRouteChangeHandlers(): void {
    this.activatedRoute.paramMap.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.searchTerm.set('');
      this.bookTitle = '';
      this.bookSelectionService.deselectAll();
      this.clearFilter();
    });
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
      takeUntil(this.destroy$)
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
          this.bookFilterComponent.selectedFilterMode = parseResult.filterMode;
        }
      }

      this.currentFilterLabel = this.t.translate('book.browser.labels.allBooks');
      const filterParams = queryParamMap.get('filter');

      if (filterParams) {
        this.settingFiltersFromUrl = true;
        this.selectedFilter.set(parseResult.filters);

        if (this.bookFilterComponent) {
          this.bookFilterComponent.setFilters?.(parseResult.filters);
          this.bookFilterComponent.onFiltersChanged?.();
        }

        if (Object.keys(parseResult.filters).length > 0) {
          this.currentFilterLabel = this.computedFilterLabel;
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
      this.currentViewMode = parseResult.viewMode;

      if (!this.areSortCriteriaEqual(this.lastAppliedSortCriteria, this.bookSorter.selectedSortCriteria)) {
        this.lastAppliedSortCriteria = [...this.bookSorter.selectedSortCriteria];
        this.applySortCriteria(this.bookSorter.selectedSortCriteria);
      }


      this.queryParamsService.syncQueryParams(
        this.currentViewMode!,
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

    const hasSidebarFilters = !!normalizedFilters && Object.keys(normalizedFilters).length > 0;
    this.currentFilterLabel = hasSidebarFilters ? this.computedFilterLabel : this.t.translate('book.browser.labels.allBooks');

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
    this.bookSelectionService.selectAll();
    if (this.bookTableComponent) {
      this.bookTableComponent.selectAllBooks();
    }
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

  get canSaveSort(): boolean {
    const entityType = this.entityType();
    return entityType === EntityType.LIBRARY ||
           entityType === EntityType.SHELF ||
           entityType === EntityType.MAGIC_SHELF ||
           entityType === EntityType.ALL_BOOKS ||
           entityType === EntityType.UNSHELVED;
  }

  get hasSearchTerm(): boolean {
    return this.searchTerm().trim().length > 0;
  }

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
    this.resetFilterSubject.next();
  }

  clearFilter(): void {
    if (this.selectedFilter() !== null) {
      this.selectedFilter.set(null);
    }
    this.clearSearch();
  }

  toggleTableGrid(): void {
    this.currentViewMode = this.currentViewMode === VIEW_MODES.GRID ? VIEW_MODES.TABLE : VIEW_MODES.GRID;
    this.queryParamsService.updateViewMode(this.currentViewMode as 'grid' | 'table');
  }

  onViewModeChange(mode: string): void {
    if (mode && mode !== this.currentViewMode) {
      this.currentViewMode = mode;
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
      this.dynamicDialogRef.onClose.subscribe(result => {
        if (result.assigned) {
          this.bookSelectionService.deselectAll();
        }
      });
    }
  }

  lockUnlockMetadata(): void {
    this.dynamicDialogRef = this.dialogHelperService.openLockUnlockMetadataDialog(this.selectedBooks());
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.subscribe(() => {
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
      this.dynamicDialogRef.onClose.subscribe(() => {
        this.bookSelectionService.deselectAll();
      });
    }
  }

  multiBookEditMetadata(): void {
    this.dynamicDialogRef = this.dialogHelperService.openMultibookMetadataEditorDialog(this.selectedBooks());
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.subscribe(() => {
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
    const sourceBooks = this.bookService.books().filter(book =>
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
      this.dynamicDialogRef.onClose.subscribe(result => {
        if (result?.success) {
          this.bookSelectionService.deselectAll();
        }
      });
    }
  }

  canAttachFiles(): boolean {
    const selectedBookIds = Array.from(this.selectedBooks());
    if (selectedBookIds.length === 0) return false;

    const selectedBooks = this.bookService.books().filter(book =>
      selectedBookIds.includes(book.id)
    );

    if (selectedBooks.length === 0) return false;

    const libraryIds = new Set(selectedBooks.map(b => b.libraryId));
    return libraryIds.size === 1;
  }

  setMobileColumns(columns: number): void {
    this.mobileColumnCount = columns;
    this.localStorageService.set(this.MOBILE_COLUMNS_STORAGE_KEY, columns);
  }

  private loadMobileColumnsPreference(): void {
    const saved = this.localStorageService.get<number>(this.MOBILE_COLUMNS_STORAGE_KEY);
    if (saved !== null && [2, 3, 4].includes(saved)) {
      this.mobileColumnCount = saved;
    }
  }

}
