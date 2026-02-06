import {AfterViewInit, ApplicationRef, ChangeDetectorRef, Component, HostListener, inject, NgZone, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, NavigationStart, Router} from '@angular/router';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {BookService} from '../../service/book.service';
import {debounceTime, filter, map, switchMap, takeUntil} from 'rxjs/operators';
import {BehaviorSubject, combineLatest, finalize, Observable, of, Subject, Subscription} from 'rxjs';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookState} from '../../model/state/book-state.model';
import {Book} from '../../model/book.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {BookTableComponent} from './book-table/book-table.component';
import {animate, style, transition, trigger} from '@angular/animations';
import {Button} from 'primeng/button';
import {AsyncPipe, NgClass, NgStyle} from '@angular/common';
import {VirtualScrollerComponent, VirtualScrollerModule} from '@iharbeck/ngx-virtual-scroller';
import {BookCardComponent} from './book-card/book-card.component';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Menu} from 'primeng/menu';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {BookFilterComponent} from './book-filter/book-filter.component';
import {Tooltip} from 'primeng/tooltip';
import {BookFilterMode, EntityViewPreferences, UserService} from '../../../settings/user-management/user.service';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';
import {SideBarFilter} from './filters/sidebar-filter';
import {HeaderFilter} from './filters/HeaderFilter';
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
import {MagicShelf} from '../../../magic-shelf/service/magic-shelf.service';
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
import {BookBrowserEntityService} from './book-browser-entity.service';
import {BookFilterOrchestrationService} from './book-filter-orchestration.service';
import {BookBrowserScrollService} from './book-browser-scroll.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MultiSortPopoverComponent} from './sorting/multi-sort-popover/multi-sort-popover.component';
import {CdkDragDrop} from '@angular/cdk/drag-drop';
import {SortService} from '../../service/sort.service';

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
    Button, VirtualScrollerModule, BookCardComponent, AsyncPipe, ProgressSpinner, Menu, InputText, FormsModule,
    BookTableComponent, BookFilterComponent, Tooltip, NgClass, NgStyle, Popover,
    Checkbox, Slider, Divider, MultiSelect, TieredMenu, BadgeModule, MultiSortPopoverComponent
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

  private cdr = inject(ChangeDetectorRef);
  private ngZone = inject(NgZone);
  private appRef = inject(ApplicationRef);
  private activatedRoute = inject(ActivatedRoute);
  private router = inject(Router);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private dialogHelperService = inject(BookDialogHelperService);
  private bookMenuService = inject(BookMenuService);
  private libraryShelfMenuService = inject(LibraryShelfMenuService);
  private pageTitle = inject(PageTitleService);
  private loadingService = inject(LoadingService);
  private bookNavigationService = inject(BookNavigationService);
  private queryParamsService = inject(BookBrowserQueryParamsService);
  private entityService = inject(BookBrowserEntityService);
  private filterOrchestrationService = inject(BookFilterOrchestrationService);
  private localStorageService = inject(LocalStorageService);
  private scrollService = inject(BookBrowserScrollService);

  bookState$: Observable<BookState> | undefined;
  entity$: Observable<Library | Shelf | MagicShelf | null> | undefined;
  entityType$: Observable<EntityType> | undefined;
  searchTerm$ = new BehaviorSubject<string>('');
  selectedFilter = new BehaviorSubject<Record<string, string[]> | null>(null);
  selectedFilterMode = new BehaviorSubject<BookFilterMode>('and');
  protected resetFilterSubject = new Subject<void>();

  parsedFilters: Record<string, string[]> = {};
  entity: Library | Shelf | MagicShelf | null = null;
  entityType: EntityType | undefined;
  bookTitle = '';
  entityOptions: MenuItem[] | undefined;
  isDrawerVisible = false;
  dynamicDialogRef: DynamicDialogRef | undefined | null;
  EntityType = EntityType;
  currentFilterLabel: string | null = null;
  rawFilterParamFromUrl: string | null = null;
  hasSearchTerm = false;
  visibleColumns: { field: string; header: string }[] = [];
  entityViewPreferences: EntityViewPreferences | undefined;
  currentViewMode: string | undefined;
  lastAppliedSortCriteria: SortOption[] = [];
  showFilter = false;
  screenWidth = typeof window !== 'undefined' ? window.innerWidth : 1024;
  mobileColumnCount = 3;
  selectedCount = 0;

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

  private sideBarFilter = new SideBarFilter(this.selectedFilter, this.selectedFilterMode);
  private headerFilter = new HeaderFilter(this.searchTerm$);
  protected bookSorter = new BookSorter(
    sortCriteria => this.onMultiSortChange(sortCriteria)
  );
  private sortService = inject(SortService);

  private bookStateSubscription: Subscription | undefined;

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
    const coverHeight = Math.floor(cardWidth * this.CARD_ASPECT_RATIO);
    const cardHeight = coverHeight + this.MOBILE_TITLE_BAR_HEIGHT;
    return {width: cardWidth, height: cardHeight};
  }

  get selectedBooks(): Set<number> {
    return this.bookSelectionService.selectedBooks;
  }

  get currentCardSize() {
    if (this.isMobile) {
      return this.mobileCardSize;
    }
    return this.coverScalePreferenceService.currentCardSize;
  }

  get gridColumnMinWidth(): string {
    if (this.isMobile) {
      return `${this.mobileCardSize.width}px`;
    }
    return this.coverScalePreferenceService.gridColumnMinWidth;
  }

  getCardHeight(_book: Book): number {
    // Use uniform height for all book types to ensure smooth virtual scrolling.
    // Mixed heights cause choppy/jumpy scrolling because the virtual scroller
    // cannot accurately estimate positions when item heights vary.
    if (this.isMobile) {
      return this.mobileCardSize.height;
    }
    return this.coverScalePreferenceService.getCardHeight(_book);
  }

  get viewIcon(): string {
    return this.currentViewMode === VIEW_MODES.GRID ? 'pi pi-objects-column' : 'pi pi-table';
  }

  get isFilterActive(): boolean {
    return !!this.selectedFilter.value && Object.keys(this.selectedFilter.value).length > 0;
  }

  get computedFilterLabel(): string {
    const filters = this.selectedFilter.value;

    if (!filters || Object.keys(filters).length === 0) {
      return 'All Books';
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
      ? `${filterEntries.length} Active Filters`
      : filterSummary;
  }

  get seriesViewEnabled(): boolean {
    return Boolean(this.userService.getCurrentUser()?.userSettings?.enableSeriesView);
  }

  get hasMetadataMenuItems(): boolean {
    return this.metadataMenuItems!.length > 0;
  }

  get hasMoreActionsItems(): boolean {
    return this.moreActionsMenuItems!.length > 0;
  }

  ngOnInit(): void {
    this.pageTitle.setPageTitle('');
    this.coverScalePreferenceService.scaleChange$.pipe(debounceTime(1000)).subscribe();
    this.loadMobileColumnsPreference();

    this.initializeEntityRouting();
    this.setupRouteChangeHandlers();
    this.setupUserStateSubscription();
    this.setupQueryParamSubscription();
    this.setupSearchTermSubscription();
    this.setupScrollPositionTracking();
    this.setupSelectionSubscription();
  }

  ngAfterViewInit(): void {
    if (this.bookFilterComponent) {
      this.bookFilterComponent.setFilters?.(this.parsedFilters);
      this.bookFilterComponent.onFiltersChanged?.();
      this.bookFilterComponent.selectedFilterMode = this.selectedFilterMode.getValue();
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

  private initializeEntityRouting(): void {
    const currentPath = this.activatedRoute.snapshot.routeConfig?.path;

    if (currentPath === 'all-books' || currentPath === 'unshelved-books') {
      const entityType = currentPath === 'all-books' ? EntityType.ALL_BOOKS : EntityType.UNSHELVED;
      this.entityType = entityType;
      this.entityType$ = of(entityType);
      this.entity$ = of(null);
      this.seriesCollapseFilter.setContext(null, null);
      this.pageTitle.setPageTitle(currentPath === 'all-books' ? 'All Books' : 'Unshelved Books');
    } else {
      const routeEntityInfo$ = this.entityService.getEntityInfoFromRoute(this.activatedRoute);
      this.entityType$ = routeEntityInfo$.pipe(map(info => {
        this.entityType = info.entityType;
        return info.entityType;
      }));
      this.entity$ = routeEntityInfo$.pipe(
        switchMap(({entityId, entityType}) => this.entityService.fetchEntity(entityId, entityType))
      );
      this.entity$.subscribe(entity => this.handleEntityLoaded(entity));
    }
  }

  private handleEntityLoaded(entity: Library | Shelf | MagicShelf | null): void {
    if (entity) {
      this.pageTitle.setPageTitle(entity.name);
    }
    this.entity = entity ?? null;
    this.updateSeriesCollapseContext();
    this.entityOptions = entity
      ? this.entityService.isLibrary(entity)
        ? this.libraryShelfMenuService.initializeLibraryMenuItems(entity)
        : this.entityService.isMagicShelf(entity)
          ? this.libraryShelfMenuService.initializeMagicShelfMenuItems(entity)
          : this.libraryShelfMenuService.initializeShelfMenuItems(entity)
      : [];
  }

  private setupRouteChangeHandlers(): void {
    this.activatedRoute.paramMap.subscribe(() => {
      this.searchTerm$.next('');
      this.bookTitle = '';
      this.bookSelectionService.deselectAll();
      this.clearFilter();
    });
  }

  private setupUserStateSubscription(): void {
    this.userService.userState$.pipe(filter(u => !!u?.user && u.loaded))
      .subscribe(userState => {
        this.metadataMenuItems = this.bookMenuService.getMetadataMenuItems(
          () => this.autoFetchMetadata(),
          () => this.fetchMetadata(),
          () => this.bulkEditMetadata(),
          () => this.multiBookEditMetadata(),
          () => this.regenerateCoversForSelected(),
          () => this.generateCustomCoversForSelected(),
          userState.user
        );
      });

    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  private setupQueryParamSubscription(): void {
    combineLatest([
      this.activatedRoute.paramMap,
      this.activatedRoute.queryParamMap,
      this.userService.userState$.pipe(filter(u => !!u?.user && u.loaded))
    ]).subscribe(([_, queryParamMap, user]) => {
      const parseResult = this.queryParamsService.parseQueryParams(
        queryParamMap,
        user.user?.userSettings?.entityViewPreferences,
        this.entityType,
        this.entity?.id ?? undefined,
        this.bookSorter.sortOptions,
        user.user?.userSettings?.filterMode ?? 'and'
      );


      if (parseResult.filterMode !== this.selectedFilterMode.getValue()) {
        this.selectedFilterMode.next(parseResult.filterMode);
        if (this.bookFilterComponent) {
          this.bookFilterComponent.selectedFilterMode = parseResult.filterMode;
        }
      }

      this.sidebarFilterTogglePrefService.showFilter$.subscribe(value => {
        this.showFilter = value;
      });


      this.currentFilterLabel = 'All Books';
      const filterParams = queryParamMap.get('filter');

      if (filterParams) {
        this.settingFiltersFromUrl = true;
        this.selectedFilter.next(parseResult.filters);

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


      this.entityViewPreferences = user.user?.userSettings?.entityViewPreferences;
      this.coverScalePreferenceService.initScaleValue(this.coverScalePreferenceService.scaleFactor);
      this.columnPreferenceService.initPreferences(user.user?.userSettings?.tableColumnPreference);
      this.visibleColumns = this.columnPreferenceService.visibleColumns;


      this.bookSorter.setSortCriteria(parseResult.sortCriteria);
      this.currentViewMode = parseResult.viewMode;

      if (!this.areSortCriteriaEqual(this.lastAppliedSortCriteria, this.bookSorter.selectedSortCriteria)) {
        this.lastAppliedSortCriteria = [...this.bookSorter.selectedSortCriteria];
        this.applySortCriteria(this.bookSorter.selectedSortCriteria);
      }


      this.queryParamsService.syncQueryParams(
        this.currentViewMode!,
        this.bookSorter.selectedSortCriteria,
        this.selectedFilterMode.getValue(),
        this.parsedFilters
      );
    });
  }

  private setupSearchTermSubscription(): void {
    this.searchTerm$.subscribe(term => {
      this.hasSearchTerm = !!term && term.trim().length > 0;
    });
  }

  private setupSelectionSubscription(): void {
    this.bookSelectionService.selectedBooks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(selectedBooks => {
        this.ngZone.run(() => {
          this.selectedCount = selectedBooks.size;
          this.cdr.detectChanges();
          this.appRef.tick();
        });
      });
  }

  onFilterSelected(filters: Record<string, any> | null): void {
    if (this.settingFiltersFromUrl) return;

    this.selectedFilter.next(filters);
    this.rawFilterParamFromUrl = null;

    const hasSidebarFilters = !!filters && Object.keys(filters).length > 0;
    this.currentFilterLabel = hasSidebarFilters ? this.computedFilterLabel : 'All Books';

    this.queryParamsService.updateFilters(filters);
  }

  onFilterModeChanged(mode: BookFilterMode): void {
    if (this.settingFiltersFromUrl || mode === this.selectedFilterMode.getValue()) return;

    this.selectedFilterMode.next(mode);
    this.queryParamsService.updateFilterMode(mode, this.parsedFilters);
  }

  toggleSidebar(): void {
    this.showFilter = !this.showFilter;
    this.sidebarFilterTogglePrefService.selectedShowFilter = this.showFilter;
  }

  updateScale(): void {
    this.coverScalePreferenceService.setScale(this.coverScalePreferenceService.scaleFactor);
  }

  onVisibleColumnsChange(selected: { field: string; header: string }[]): void {
    const allFields = this.bookTableComponent.allColumns.map(col => col.field);
    this.visibleColumns = selected.sort(
      (a, b) => allFields.indexOf(a.field) - allFields.indexOf(b.field)
    );
  }

  onCheckboxClicked(event: CheckboxClickEvent): void {
    this.bookSelectionService.handleCheckboxClick(event);
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  handleBookSelect(book: Book, selected: boolean): void {
    this.bookSelectionService.handleBookSelection(book, selected);
    this.isDrawerVisible = this.bookSelectionService.hasSelection();
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  onSelectedBooksChange(selectedBookIds: Set<number>): void {
    this.bookSelectionService.setSelectedBooks(selectedBookIds);
    this.isDrawerVisible = this.bookSelectionService.hasSelection();
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  selectAllBooks(): void {
    this.bookSelectionService.selectAll();
    if (this.bookTableComponent) {
      this.bookTableComponent.selectAllBooks();
    }
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  deselectAllBooks(): void {
    this.bookSelectionService.deselectAll();
    this.isDrawerVisible = false;
    if (this.bookTableComponent) {
      this.bookTableComponent.clearSelectedBooks();
    }
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  confirmDeleteBooks(): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete ${this.selectedBooks.size} book(s)?\n\nThis will permanently remove the book files from your filesystem.\n\nThis action cannot be undone.`,
      header: 'Confirm Deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-outlined',
      accept: () => {
        const count = this.selectedBooks.size;
        const loader = this.loadingService.show(`Deleting ${count} book(s)...`);

        this.bookService.deleteBooks(this.selectedBooks)
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
    // Use first criterion for API call (backend doesn't support multi-sort)
    const primarySort = sortCriteria[0] ?? {field: 'addedOn', direction: 'DESCENDING', label: 'Added On'};

    if (this.entityType === EntityType.ALL_BOOKS) {
      this.bookState$ = this.entityService.fetchAllBooks(primarySort).pipe(
        map(bookState => this.applyClientSideMultiSort(bookState, sortCriteria)),
        switchMap(bookState => this.applyBookFilters(bookState))
      );
    } else if (this.entityType === EntityType.UNSHELVED) {
      this.bookState$ = this.entityService.fetchUnshelvedBooks(primarySort).pipe(
        map(bookState => this.applyClientSideMultiSort(bookState, sortCriteria)),
        switchMap(bookState => this.applyBookFilters(bookState))
      );
    } else {
      const routeParam$ = this.entityService.getEntityInfoFromRoute(this.activatedRoute);
      this.bookState$ = routeParam$.pipe(
        switchMap(({entityId, entityType}) =>
          this.entityService.fetchBooksByEntity(entityId, entityType, primarySort)
        ),
        map(bookState => this.applyClientSideMultiSort(bookState, sortCriteria)),
        switchMap(bookState => this.applyBookFilters(bookState))
      );
    }

    if (this.bookStateSubscription) {
      this.bookStateSubscription.unsubscribe();
    }

    this.bookStateSubscription = this.bookState$
      .pipe(
        filter(state => state.loaded && !state.error),
        takeUntil(this.destroy$),
        map(state => state.books || [])
      )
      .subscribe(books => {
        this.bookSelectionService.setCurrentBooks(books);
        this.bookNavigationService.setAvailableBookIds(books.map(book => book.id));
      });
  }

  private applyClientSideMultiSort(bookState: BookState, sortCriteria: SortOption[]): BookState {
    if (!bookState.books || sortCriteria.length <= 1) {
      return bookState;
    }
    return {
      ...bookState,
      books: this.sortService.applyMultiSort(bookState.books, sortCriteria)
    };
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

  // Multi-sort popover handlers
  onAddSortCriterion(field: string): void {
    this.bookSorter.addSortCriterion(field);
  }

  onRemoveSortCriterion(index: number): void {
    this.bookSorter.removeSortCriterion(index);
  }

  onToggleSortDirection(index: number): void {
    this.bookSorter.toggleCriterionDirection(index);
  }

  onReorderSortCriteria(event: CdkDragDrop<SortOption[]>): void {
    this.bookSorter.reorderCriteria(event);
  }

  get sortCriteriaCount(): number {
    return this.bookSorter.selectedSortCriteria.length;
  }

  onSearchTermChange(term: string): void {
    this.searchTerm$.next(term);
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
    if (this.selectedFilter.value !== null) {
      this.selectedFilter.next(null);
    }
    this.clearSearch();
  }

  toggleTableGrid(): void {
    this.currentViewMode = this.currentViewMode === VIEW_MODES.GRID ? VIEW_MODES.TABLE : VIEW_MODES.GRID;
    this.queryParamsService.updateViewMode(this.currentViewMode as 'grid' | 'table');
  }

  unshelfBooks(): void {
    if (!this.entity) return;
    const count = this.selectedBooks.size;
    const loader = this.loadingService.show(`Unshelving ${count} book(s)...`);

    this.bookService.updateBookShelves(this.selectedBooks, new Set(), new Set([this.entity.id!]))
      .pipe(finalize(() => this.loadingService.hide(loader)))
      .subscribe({
        next: () => {
          this.messageService.add({severity: 'info', summary: 'Success', detail: 'Books shelves updated'});
          this.bookSelectionService.deselectAll();
        },
        error: () => {
          this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update books shelves'});
        }
      });
  }

  openShelfAssigner(): void {
    this.dynamicDialogRef = this.dialogHelperService.openShelfAssignerDialog(null, this.selectedBooks);
    if (this.dynamicDialogRef) {
      this.dynamicDialogRef.onClose.subscribe(result => {
        if (result.assigned) {
          this.bookSelectionService.deselectAll();
        }
      });
    }
  }

  lockUnlockMetadata(): void {
    this.dynamicDialogRef = this.dialogHelperService.openLockUnlockMetadataDialog(this.selectedBooks);
  }

  autoFetchMetadata(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: Array.from(this.selectedBooks),
    }).subscribe();
  }

  fetchMetadata(): void {
    this.dialogHelperService.openMetadataRefreshDialog(this.selectedBooks);
  }

  bulkEditMetadata(): void {
    this.dialogHelperService.openBulkMetadataEditDialog(this.selectedBooks);
  }

  multiBookEditMetadata(): void {
    this.dialogHelperService.openMultibookMetadataEditorDialog(this.selectedBooks);
  }

  regenerateCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: `Are you sure you want to regenerate covers for ${count} book(s)?`,
      header: 'Confirm Cover Regeneration',
      icon: 'pi pi-image',
      acceptLabel: 'Yes',
      rejectLabel: 'No',
      acceptButtonProps: {
        label: 'Yes',
        severity: 'success'
      },
      rejectButtonProps: {
        label: 'No',
        severity: 'secondary'
      },
      accept: () => {
        this.bookService.regenerateCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Cover Regeneration Started',
              detail: `Regenerating covers for ${count} book(s). Refresh the page when complete.`,
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Failed',
              detail: 'Could not start cover regeneration.',
              life: 3000
            });
          }
        });
      }
    });
  }

  generateCustomCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: `Are you sure you want to generate custom covers for ${count} book(s)?`,
      header: 'Confirm Custom Cover Generation',
      icon: 'pi pi-palette',
      acceptLabel: 'Yes',
      rejectLabel: 'No',
      acceptButtonProps: {
        label: 'Yes',
        severity: 'success'
      },
      rejectButtonProps: {
        label: 'No',
        severity: 'secondary'
      },
      accept: () => {
        this.bookService.generateCustomCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Custom Cover Generation Started',
              detail: `Generating custom covers for ${count} book(s).`,
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Failed',
              detail: 'Could not start custom cover generation.',
              life: 3000
            });
          }
        });
      }
    });
  }

  moveFiles(): void {
    this.dialogHelperService.openFileMoverDialog(this.selectedBooks);
  }

  attachFilesToBook(): void {
    // Get selected books that are single-file books (no alternative formats)
    const currentState = this.bookService.getCurrentBookState();
    const selectedBookIds = Array.from(this.selectedBooks);
    const sourceBooks = (currentState.books || []).filter(book =>
      selectedBookIds.includes(book.id) && !book.alternativeFormats?.length
    );

    if (sourceBooks.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No Eligible Books',
        detail: 'Selected books must be single-file books (no alternative formats).'
      });
      return;
    }

    // Check if all books are from the same library
    const libraryIds = new Set(sourceBooks.map(b => b.libraryId));
    if (libraryIds.size > 1) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Multiple Libraries',
        detail: 'All selected books must be from the same library.'
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
    if (this.selectedBooks.size === 0) return false;

    const currentState = this.bookService.getCurrentBookState();
    const selectedBookIds = Array.from(this.selectedBooks);
    const eligibleBooks = (currentState.books || []).filter(book =>
      selectedBookIds.includes(book.id) && !book.alternativeFormats?.length
    );

    if (eligibleBooks.length === 0) return false;

    // Check if all eligible books are from the same library
    const libraryIds = new Set(eligibleBooks.map(b => b.libraryId));
    return libraryIds.size === 1;
  }

  user() {
    return this.userService.getCurrentUser();
  }

  private updateSeriesCollapseContext(): void {
    let type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF' | null = null;
    let id: number | null = null;

    if (this.entity && this.entityType) {
      switch (this.entityType) {
        case EntityType.LIBRARY:
          type = 'LIBRARY';
          id = this.entity.id ?? 0;
          break;
        case EntityType.SHELF:
          type = 'SHELF';
          id = this.entity.id ?? 0;
          break;
        case EntityType.MAGIC_SHELF:
          type = 'MAGIC_SHELF';
          id = this.entity.id ?? 0;
          break;
      }
    }

    this.seriesCollapseFilter.setContext(type, id);
  }

  private applyBookFilters(bookState: BookState): Observable<BookState> {
    const forceExpandSeries = this.filterOrchestrationService.shouldForceExpandSeries(
      this.activatedRoute.snapshot.queryParamMap
    );

    // Use first sort criterion for series collapse filter
    const primarySort: SortOption = this.bookSorter.selectedSort ?? {
      field: 'addedOn',
      direction: SortDirection.DESCENDING,
      label: 'Added On'
    };

    return this.filterOrchestrationService.applyFilters(
      bookState,
      this.headerFilter,
      this.sideBarFilter,
      this.seriesCollapseFilter,
      forceExpandSeries,
      primarySort
    );
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
