import {Component, inject, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {ConfirmationService, MenuItem, MessageService, PrimeTemplate} from 'primeng/api';
import {LibraryService} from '../../service/library.service';
import {BookService} from '../../service/book.service';
import {catchError, debounceTime, filter, map, switchMap, take} from 'rxjs/operators';
import {BehaviorSubject, combineLatest, Observable, of, Subject} from 'rxjs';
import {ShelfService} from '../../service/shelf.service';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {SortService} from '../../service/sort.service';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookState} from '../../model/state/book-state.model';
import {Book} from '../../model/book.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {BookTableComponent} from './book-table/book-table.component';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {Button} from 'primeng/button';
import {AsyncPipe, NgClass, NgStyle} from '@angular/common';
import {VirtualScrollerModule} from '@iharbeck/ngx-virtual-scroller';
import {BookCardComponent} from './book-card/book-card.component';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Menu} from 'primeng/menu';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {BookFilterComponent} from './book-filter/book-filter.component';
import {Tooltip} from 'primeng/tooltip';
import {EntityViewPreferences, UserService} from '../../../settings/user-management/user.service';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';
import {SideBarFilter} from './filters/SidebarFilter';
import {HeaderFilter} from './filters/HeaderFilter';
import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {BookSorter} from './sorting/BookSorter';
import {BookDialogHelperService} from './BookDialogHelperService';
import {Checkbox} from 'primeng/checkbox';
import {Popover} from 'primeng/popover';
import {Slider} from 'primeng/slider';
import {Divider} from 'primeng/divider';
import {MultiSelect} from 'primeng/multiselect';
import {TableColumnPreferenceService} from './table-column-preference-service';
import {TieredMenu} from 'primeng/tieredmenu';
import {BookMenuService} from '../../service/book-menu.service';
import {MagicShelf, MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {SidebarFilterTogglePrefService} from './filters/sidebar-filter-toggle-pref-service';
import {MetadataRefreshRequest} from '../../../metadata/model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {GroupRule} from '../../../magic-shelf/component/magic-shelf-component';
import {TaskCreateRequest, TaskService, TaskType} from '../../../settings/task-management/task.service';
import {TaskHelperService} from '../../../settings/task-management/task-helper.service';

export enum EntityType {
  LIBRARY = 'Library',
  SHELF = 'Shelf',
  MAGIC_SHELF = 'Magic Shelf',
  ALL_BOOKS = 'All Books',
  UNSHELVED = 'Unshelved Books',
}

const QUERY_PARAMS = {
  VIEW: 'view',
  SORT: 'sort',
  DIRECTION: 'direction',
  FILTER: 'filter',
  SIDEBAR: 'sidebar',
  FROM: 'from',
};

const VIEW_MODES = {
  GRID: 'grid',
  TABLE: 'table',
};

const SORT_DIRECTION = {
  ASCENDING: 'asc',
  DESCENDING: 'desc',
};

@Component({
  selector: 'app-book-browser',
  standalone: true,
  templateUrl: './book-browser.component.html',
  styleUrls: ['./book-browser.component.scss'],
  imports: [
    Button, VirtualScrollerModule, BookCardComponent, AsyncPipe, ProgressSpinner, Menu, InputText, FormsModule,
    BookTableComponent, BookFilterComponent, Tooltip, NgClass, PrimeTemplate, NgStyle, Popover,
    Checkbox, Slider, Divider, MultiSelect, TieredMenu
  ],
  providers: [SeriesCollapseFilter],
  animations: [
    trigger('slideInOut', [
      state('void', style({transform: 'translateY(100%)'})),
      state('*', style({transform: 'translateY(0)'})),
      transition(':enter', [animate('0.1s ease-in')]),
      transition(':leave', [animate('0.1s ease-out')])
    ])
  ]
})
export class BookBrowserComponent implements OnInit {
  protected userService = inject(UserService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected columnPreferenceService = inject(TableColumnPreferenceService);
  protected sidebarFilterTogglePrefService = inject(SidebarFilterTogglePrefService);
  private activatedRoute = inject(ActivatedRoute);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private bookService = inject(BookService);
  private shelfService = inject(ShelfService);
  private dialogHelperService = inject(BookDialogHelperService);
  private bookMenuService = inject(BookMenuService);
  private sortService = inject(SortService);
  private router = inject(Router);
  private libraryShelfMenuService = inject(LibraryShelfMenuService);
  protected seriesCollapseFilter = inject(SeriesCollapseFilter);
  protected confirmationService = inject(ConfirmationService);
  protected magicShelfService = inject(MagicShelfService);
  protected bookRuleEvaluatorService = inject(BookRuleEvaluatorService);
  protected taskHelperService = inject(TaskHelperService);

  bookState$: Observable<BookState> | undefined;
  entity$: Observable<Library | Shelf | MagicShelf | null> | undefined;
  entityType$: Observable<EntityType> | undefined;
  searchTerm$ = new BehaviorSubject<string>('');
  selectedFilter = new BehaviorSubject<Record<string, any> | null>(null);
  selectedFilterMode = new BehaviorSubject<'and' | 'or'>('and');
  protected resetFilterSubject = new Subject<void>();
  entity: Library | Shelf | MagicShelf | null = null;
  entityType: EntityType | undefined;
  bookTitle = '';
  entityOptions: MenuItem[] | undefined;
  selectedBooks = new Set<number>();
  isDrawerVisible = false;
  dynamicDialogRef: DynamicDialogRef | undefined | null;
  EntityType = EntityType;
  currentFilterLabel: string | null = null;
  rawFilterParamFromUrl: string | null = null;
  hasSearchTerm = false;
  visibleColumns: { field: string; header: string }[] = [];
  entityViewPreferences: EntityViewPreferences | undefined;
  currentViewMode: string | undefined;
  lastAppliedSort: SortOption | null = null;
  private settingFiltersFromUrl = false;
  protected metadataMenuItems: MenuItem[] | undefined;
  protected tieredMenuItems: MenuItem[] | undefined;
  currentBooks: Book[] = [];
  lastSelectedIndex: number | null = null;
  showFilter = false;

  private sideBarFilter = new SideBarFilter(this.selectedFilter, this.selectedFilterMode);
  private headerFilter = new HeaderFilter(this.searchTerm$);
  protected bookSorter = new BookSorter(
    selectedSort => this.onManualSortChange(selectedSort)
  );

  @ViewChild(BookTableComponent)
  bookTableComponent!: BookTableComponent;
  @ViewChild(BookFilterComponent, {static: false})
  bookFilterComponent!: BookFilterComponent;

  get currentCardSize() {
    return this.coverScalePreferenceService.currentCardSize;
  }

  get gridColumnMinWidth(): string {
    return this.coverScalePreferenceService.gridColumnMinWidth;
  }

  get viewIcon(): string {
    return this.currentViewMode === VIEW_MODES.GRID ? 'pi pi-objects-column' : 'pi pi-table';
  }

  get hasSidebarFilters(): boolean {
    return !!this.selectedFilter.value && Object.keys(this.selectedFilter.value).length > 0;
  }

  get isFilterActive(): boolean {
    return this.selectedFilter.value !== null;
  }

  ngOnInit(): void {
    this.coverScalePreferenceService.scaleChange$.pipe(debounceTime(1000)).subscribe();

    const currentPath = this.activatedRoute.snapshot.routeConfig?.path;
    if (currentPath === 'all-books' || currentPath === 'unshelved-books') {
      const entityType = currentPath === 'all-books' ? EntityType.ALL_BOOKS : EntityType.UNSHELVED;
      this.entityType = entityType;
      this.entityType$ = of(entityType);
      this.entity$ = of(null);
    } else {
      const routeEntityInfo$ = this.getEntityInfoFromRoute();
      this.entityType$ = routeEntityInfo$.pipe(map(info => info.entityType));
      this.entity$ = routeEntityInfo$.pipe(
        switchMap(({entityId, entityType}) => this.fetchEntity(entityId, entityType))
      );
      this.entity$.subscribe(entity => {
        this.entity = entity ?? null;
        this.entityOptions = entity
          ? this.isLibrary(entity)
            ? this.libraryShelfMenuService.initializeLibraryMenuItems(entity)
            : this.isMagicShelf(entity)
              ? this.libraryShelfMenuService.initializeMagicShelfMenuItems(entity)
              : this.libraryShelfMenuService.initializeShelfMenuItems(entity)
          : [];
      });
    }

    this.activatedRoute.paramMap.subscribe(() => {
      this.searchTerm$.next('');
      this.bookTitle = '';
      this.deselectAllBooks();
      this.clearFilter();
    });

    this.metadataMenuItems = this.bookMenuService.getMetadataMenuItems(
      () => this.autoFetchMetadata(),
      () => this.fetchMetadata(),
      () => this.bulkEditMetadata(),
      () => this.multiBookEditMetadata(),
    );
    this.tieredMenuItems = this.bookMenuService.getTieredMenuItems(this.selectedBooks);

    combineLatest([
      this.activatedRoute.paramMap,
      this.activatedRoute.queryParamMap,
      this.userService.userState$.pipe(filter(u => !!u?.user && u.loaded))
    ]).subscribe(([paramMap, queryParamMap, user]) => {

      const viewParam = queryParamMap.get(QUERY_PARAMS.VIEW);
      const sortParam = queryParamMap.get(QUERY_PARAMS.SORT);
      const directionParam = queryParamMap.get(QUERY_PARAMS.DIRECTION);
      const filterParams = queryParamMap.get(QUERY_PARAMS.FILTER);

      this.sidebarFilterTogglePrefService.showFilter$.subscribe(value => {
        this.showFilter = value;
      });

      const parsedFilters: Record<string, string[]> = {};

      if (filterParams) {
        this.settingFiltersFromUrl = true;

        filterParams.split(',').forEach(pair => {
          const [key, ...valueParts] = pair.split(':');
          const value = valueParts.join(':');
          if (key && value) {
            parsedFilters[key] = value.split('|').map(v => v.trim()).filter(Boolean);
          }
        });

        this.selectedFilter.next(parsedFilters);
        if (this.bookFilterComponent) {
          this.bookFilterComponent.setFilters?.(parsedFilters);
          this.bookFilterComponent.onFiltersChanged?.();
        }

        const firstFilter = filterParams.split(',')[0];
        const [key, ...values] = firstFilter.split(':');
        const firstValue = values.join(':').split('|')[0];
        if (key && firstValue) {
          this.currentFilterLabel = this.capitalize(key) + ': ' + firstValue;
        } else {
          this.currentFilterLabel = 'All Books';
        }

        this.rawFilterParamFromUrl = filterParams;
        this.settingFiltersFromUrl = false;
      } else {
        this.rawFilterParamFromUrl = null;
        this.currentFilterLabel = 'All Books';
      }

      this.entityViewPreferences = user.user?.userSettings?.entityViewPreferences;
      const globalPrefs = this.entityViewPreferences?.global;
      const currentEntityTypeStr = this.entityType ? this.entityType.toString().toUpperCase() : undefined;
      this.coverScalePreferenceService.initScaleValue(this.coverScalePreferenceService.scaleFactor);
      this.columnPreferenceService.initPreferences(user.user?.userSettings?.tableColumnPreference);
      this.visibleColumns = this.columnPreferenceService.visibleColumns;

      const override = this.entityViewPreferences?.overrides?.find(o =>
        o.entityType?.toUpperCase() === currentEntityTypeStr &&
        o.entityId === this.entity?.id
      );

      const effectivePrefs = override?.preferences ?? globalPrefs ?? {
        sortKey: 'addedOn',
        sortDir: 'ASC',
        view: 'GRID'
      };

      const userSortKey = effectivePrefs.sortKey;
      const userSortDir = effectivePrefs.sortDir?.toUpperCase() === 'DESC'
        ? SortDirection.DESCENDING
        : SortDirection.ASCENDING;

      const effectiveSortKey = sortParam || userSortKey;
      const effectiveSortDir = directionParam
        ? (directionParam.toLowerCase() === SORT_DIRECTION.DESCENDING ? SortDirection.DESCENDING : SortDirection.ASCENDING)
        : userSortDir;

      const matchedSort = this.bookSorter.sortOptions.find(opt => opt.field === effectiveSortKey);

      this.bookSorter.selectedSort = matchedSort ? {
        label: matchedSort.label,
        field: matchedSort.field,
        direction: effectiveSortDir
      } : {
        label: 'Added On',
        field: 'addedOn',
        direction: SortDirection.DESCENDING
      };

      const fromParam = queryParamMap.get(QUERY_PARAMS.FROM);
      this.currentViewMode = fromParam === 'toggle'
        ? (viewParam === VIEW_MODES.TABLE || viewParam === VIEW_MODES.GRID
          ? viewParam
          : VIEW_MODES.GRID)
        : (effectivePrefs.view?.toLowerCase() ?? VIEW_MODES.GRID);

      this.bookSorter.updateSortOptions();

      if (this.lastAppliedSort?.field !== this.bookSorter.selectedSort.field || this.lastAppliedSort?.direction !== this.bookSorter.selectedSort.direction) {
        this.lastAppliedSort = {...this.bookSorter.selectedSort};
        this.applySortOption(this.bookSorter.selectedSort);
      }

      const queryParams: any = {
        [QUERY_PARAMS.VIEW]: this.currentViewMode,
        [QUERY_PARAMS.SORT]: this.bookSorter.selectedSort.field,
        [QUERY_PARAMS.DIRECTION]: this.bookSorter.selectedSort.direction === SortDirection.ASCENDING ? SORT_DIRECTION.ASCENDING : SORT_DIRECTION.DESCENDING
      };

      if (Object.keys(parsedFilters).length > 0) {
        queryParams[QUERY_PARAMS.FILTER] = Object.entries(parsedFilters).map(([k, v]) => `${k}:${v.join('|')}`).join(',');
      }

      const currentParams = this.activatedRoute.snapshot.queryParams;
      const changed = Object.keys(queryParams).some(k => currentParams[k] !== queryParams[k]);
      if (changed) {
        const mergedParams = {...currentParams, ...queryParams};
        this.router.navigate([], {
          queryParams: mergedParams,
          replaceUrl: true
        });
      }
    });

    this.searchTerm$.subscribe(term => {
      this.hasSearchTerm = !!term && term.trim().length > 0;
    });
  }

  onFilterSelected(filters: Record<string, any> | null): void {
    if (this.settingFiltersFromUrl) return;

    this.selectedFilter.next(filters);
    this.rawFilterParamFromUrl = null;

    const hasSidebarFilters = !!filters && Object.keys(filters).length > 0;
    this.currentFilterLabel = hasSidebarFilters ? 'All Books (Filtered)' : 'All Books';
  }

  onFilterModeChanged(mode: 'and' | 'or'): void {
    this.selectedFilterMode.next(mode);
  }

  toggleSidebar(): void {
    this.showFilter = !this.showFilter;
    this.sidebarFilterTogglePrefService.selectedShowFilter = this.showFilter;
  }

  updateScale(): void {
    this.coverScalePreferenceService.setScale(this.coverScalePreferenceService.scaleFactor);
  }

  onVisibleColumnsChange(selected: any[]) {
    const allFields = this.bookTableComponent.allColumns.map(col => col.field);
    this.visibleColumns = selected.sort(
      (a, b) => allFields.indexOf(a.field) - allFields.indexOf(b.field)
    );
  }

  onCheckboxClicked(event: { index: number; bookId: number; selected: boolean; shiftKey: boolean }) {
    const {index, bookId, selected, shiftKey} = event;
    if (!shiftKey || this.lastSelectedIndex === null) {
      if (selected) {
        this.selectedBooks.add(bookId);
      } else {
        this.selectedBooks.delete(bookId);
      }
      this.lastSelectedIndex = index;
    } else {
      const start = Math.min(this.lastSelectedIndex, index);
      const end = Math.max(this.lastSelectedIndex, index);
      const isUnselectingRange = !selected;
      for (let i = start; i <= end; i++) {
        const book = this.currentBooks[i];
        if (!book) continue;

        if (isUnselectingRange) {
          this.selectedBooks.delete(book.id);
        } else {
          this.selectedBooks.add(book.id);
        }
      }
    }
  }

  handleBookSelect(bookId: number, selected: boolean): void {
    if (selected) {
      this.selectedBooks.add(bookId);
    } else {
      this.selectedBooks.delete(bookId);
    }
    this.isDrawerVisible = this.selectedBooks.size > 0;
  }

  onSelectedBooksChange(selectedBookIds: Set<number>): void {
    this.selectedBooks = new Set(selectedBookIds);
    this.isDrawerVisible = this.selectedBooks.size > 0;
  }

  selectAllBooks(): void {
    if (!this.currentBooks) return;
    for (const book of this.currentBooks) {
      this.selectedBooks.add(book.id);
    }
    if (this.bookTableComponent) {
      this.bookTableComponent.selectAllBooks();
    }
  }

  deselectAllBooks(): void {
    this.selectedBooks.clear();
    this.isDrawerVisible = false;
    if (this.bookTableComponent) {
      this.bookTableComponent.clearSelectedBooks();
    }
  }

  confirmDeleteBooks(): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete ${this.selectedBooks.size} book(s)?`,
      header: 'Confirm Deletion',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.bookService.deleteBooks(this.selectedBooks).subscribe(() => {
          this.selectedBooks.clear();
        });
      },
      reject: () => {
      }
    });
  }

  onSeriesCollapseCheckboxChange(value: boolean): void {
    this.seriesCollapseFilter.setCollapsed(value);
  }

  onManualSortChange(sortOption: SortOption): void {
    this.applySortOption(sortOption);

    const currentParams = this.activatedRoute.snapshot.queryParams;
    const newParams = {
      ...currentParams,
      sort: sortOption.field,
      direction: sortOption.direction === SortDirection.ASCENDING ? SORT_DIRECTION.ASCENDING : SORT_DIRECTION.DESCENDING
    };

    this.router.navigate([], {
      queryParams: newParams,
      replaceUrl: true
    });
  }

  applySortOption(sortOption: SortOption): void {
    if (this.entityType === EntityType.ALL_BOOKS) {
      this.bookState$ = this.fetchAllBooks();
    } else if (this.entityType === EntityType.UNSHELVED) {
      this.bookState$ = this.fetchUnshelvedBooks();
    } else {
      const routeParam$ = this.getEntityInfoFromRoute();
      this.bookState$ = routeParam$.pipe(
        switchMap(({entityId, entityType}) => this.fetchBooksByEntity(entityId, entityType))
      );
    }
    this.bookState$
      .pipe(
        filter(state => state.loaded && !state.error),
        map(state => state.books || [])
      )
      .subscribe(books => {
        this.currentBooks = books;
      });
  }

  onSearchTermChange(term: string): void {
    this.searchTerm$.next(term);
  }

  clearSearch(): void {
    this.bookTitle = '';
    this.onSearchTermChange('');
    this.resetFilters();
  }

  resetFilters() {
    this.resetFilterSubject.next();
  }

  clearFilter() {
    if (this.selectedFilter.value !== null) {
      this.selectedFilter.next(null);
    }
    this.clearSearch();
  }

  toggleTableGrid(): void {
    this.currentViewMode = this.currentViewMode === VIEW_MODES.GRID ? VIEW_MODES.TABLE : VIEW_MODES.GRID;
    this.router.navigate([], {
      queryParams: {
        view: this.currentViewMode,
        [QUERY_PARAMS.FROM]: 'toggle'
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  unshelfBooks() {
    if (!this.entity) return;
    this.bookService.updateBookShelves(this.selectedBooks, new Set(), new Set([this.entity.id])).subscribe({
      next: () => {
        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Books shelves updated'});
        this.selectedBooks.clear();
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update books shelves'});
      }
    });
  }

  openShelfAssigner(): void {
    this.dynamicDialogRef = this.dialogHelperService.openShelfAssigner(this.selectedBooks);
    this.dynamicDialogRef?.onClose.subscribe(() => {
      this.selectedBooks = new Set<number>();
    });
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

  moveFiles() {
    this.dialogHelperService.openFileMoverDialog(this.selectedBooks);
  }

  private isLibrary(entity: Library | Shelf | MagicShelf): entity is Library {
    return (entity as Library).paths !== undefined;
  }

  private isMagicShelf(entity: any): entity is MagicShelf {
    return entity && 'filterJson' in entity;
  }

  private getEntityInfoFromRoute(): Observable<{ entityId: number; entityType: EntityType }> {
    return this.activatedRoute.paramMap.pipe(
      map(params => {
        const libraryId = Number(params.get('libraryId') || NaN);
        const shelfId = Number(params.get('shelfId') || NaN);
        const magicShelfId = Number(params.get('magicShelfId') || NaN);

        if (!isNaN(libraryId)) {
          this.entityType = EntityType.LIBRARY;
          return {entityId: libraryId, entityType: EntityType.LIBRARY};
        } else if (!isNaN(shelfId)) {
          this.entityType = EntityType.SHELF;
          return {entityId: shelfId, entityType: EntityType.SHELF};
        } else if (!isNaN(magicShelfId)) {
          this.entityType = EntityType.MAGIC_SHELF;
          return {entityId: magicShelfId, entityType: EntityType.MAGIC_SHELF};
        } else {
          this.entityType = EntityType.ALL_BOOKS;
          return {entityId: NaN, entityType: EntityType.ALL_BOOKS};
        }
      })
    );
  }

  private fetchEntity(entityId: number, entityType: EntityType): Observable<Library | Shelf | MagicShelf | null> {
    switch (entityType) {
      case EntityType.LIBRARY:
        return this.fetchLibrary(entityId);
      case EntityType.SHELF:
        return this.fetchShelf(entityId);
      case EntityType.MAGIC_SHELF:
        return this.fetchMagicShelf(entityId);
      default:
        return of(null);
    }
  }

  private fetchBooksByEntity(entityId: number, entityType: EntityType): Observable<BookState> {
    switch (entityType) {
      case EntityType.LIBRARY:
        return this.fetchBooks(book => book.libraryId === entityId);
      case EntityType.SHELF:
        return this.fetchBooks(book =>
          book.shelves?.some(shelf => shelf.id === entityId) ?? false
        );
      case EntityType.MAGIC_SHELF:
        return this.fetchBooksMagicShelfBooks(entityId);
      case EntityType.ALL_BOOKS:
      default:
        return this.fetchAllBooks();
    }
  }

  private fetchAllBooks(): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => this.processBookState(bookState)),
      switchMap(bookState => this.applyBookFilters(bookState))
    );
  }

  private fetchUnshelvedBooks(): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => ({
        ...bookState,
        books: (bookState.books || []).filter(book => !book.shelves || book.shelves.length === 0)
      })),
      map(bookState => this.processBookState(bookState)),
      switchMap(bookState => this.applyBookFilters(bookState))
    );
  }

  private fetchBooksMagicShelfBooks(magicShelfId: number): Observable<BookState> {
    return combineLatest([
      this.bookService.bookState$,
      this.magicShelfService.getShelf(magicShelfId)
    ]).pipe(
      map(([bookState, magicShelf]) => {
        if (!bookState.loaded || bookState.error || !magicShelf?.filterJson) {
          return bookState;
        }
        const filteredBooks: Book[] | undefined = bookState.books?.filter(book =>
          this.bookRuleEvaluatorService.evaluateGroup(book, JSON.parse(magicShelf.filterJson!) as GroupRule)
        );
        const sortedBooks = this.sortService.applySort(filteredBooks ?? [], this.bookSorter.selectedSort!);
        return {...bookState, books: sortedBooks};
      }),
      switchMap(bookState => this.applyBookFilters(bookState))
    );
  }

  private fetchBooks(bookFilter: (book: Book) => boolean): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => {
        if (bookState.loaded && !bookState.error) {
          const filteredBooks = bookState.books?.filter(bookFilter) || [];
          const sortedBooks = this.sortService.applySort(filteredBooks, this.bookSorter.selectedSort!);
          return {...bookState, books: sortedBooks};
        }
        return bookState;
      }),
      switchMap(bookState => this.applyBookFilters(bookState))
    );
  }

  private shouldForceExpandSeries(): boolean {
    const queryParams = this.activatedRoute.snapshot.queryParams;
    const filterParam = queryParams[QUERY_PARAMS.FILTER];
    return (
      filterParam &&
      typeof filterParam === 'string' &&
      filterParam.split(',').some(pair => pair.trim().startsWith('series:'))
    );
  }

  private applyBookFilters(bookState: BookState): Observable<BookState> {
    const forceExpandSeries = this.shouldForceExpandSeries();
    return this.headerFilter.filter(bookState).pipe(
      switchMap(filtered => this.sideBarFilter.filter(filtered)),
      switchMap(filtered => this.seriesCollapseFilter.filter(filtered, forceExpandSeries)),
      map(filtered =>
        (filtered.loaded && !filtered.error)
          ? ({
            ...filtered,
            books: this.sortService.applySort(filtered.books || [], this.bookSorter.selectedSort!)
          })
          : filtered
      )
    );
  }

  private processBookState(bookState: BookState): BookState {
    if (bookState.loaded && !bookState.error) {
      const sortedBooks = this.sortService.applySort(bookState.books || [], this.bookSorter.selectedSort!);
      return {...bookState, books: sortedBooks};
    }
    return bookState;
  }

  private fetchLibrary(libraryId: number): Observable<Library | null> {
    return this.libraryService.libraryState$.pipe(
      map(libraryState => {
        if (libraryState.libraries) {
          return libraryState.libraries.find(lib => lib.id === libraryId) || null;
        }
        return null;
      })
    );
  }

  private fetchShelf(shelfId: number): Observable<Shelf | null> {
    return this.shelfService.shelfState$.pipe(
      map(shelfState => {
        if (shelfState.shelves) {
          return shelfState.shelves.find(shelf => shelf.id === shelfId) || null;
        }
        return null;
      })
    );
  }

  private fetchMagicShelf(magicShelfId: number): Observable<MagicShelf | null> {
    return this.magicShelfService.shelvesState$.pipe(
      take(1),
      switchMap((state) => {
        const cached = state.shelves?.find(s => s.id === magicShelfId) ?? null;
        if (cached) {
          return of(cached);
        }
        return this.magicShelfService.getShelf(magicShelfId).pipe(
          map(shelf => shelf ?? null),
          catchError(() => of(null))
        );
      })
    );
  }

  private capitalize(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }
}
