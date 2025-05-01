import {AfterViewInit, ChangeDetectorRef, Component, inject, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {MenuItem, MessageService, PrimeTemplate} from 'primeng/api';
import {LibraryService} from '../../service/library.service';
import {BookService} from '../../service/book.service';
import {map, switchMap} from 'rxjs/operators';
import {BehaviorSubject, Observable, of, Subject} from 'rxjs';
import {ShelfService} from '../../service/shelf.service';
import {ShelfAssignerComponent} from '../shelf-assigner/shelf-assigner.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {SortService} from '../../service/sort.service';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookState} from '../../model/state/book-state.model';
import {Book} from '../../model/book.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {BookTableComponent} from './book-table/book-table.component';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {MetadataFetchOptionsComponent} from '../../../metadata/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {Button} from 'primeng/button';
import {AsyncPipe, NgClass, NgForOf, NgIf} from '@angular/common';
import {VirtualScrollerModule} from '@iharbeck/ngx-virtual-scroller';
import {BookCardComponent} from './book-card/book-card.component';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Menu} from 'primeng/menu';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {BookFilterComponent} from './book-filter/book-filter.component';
import {Tooltip} from 'primeng/tooltip';
import {Fluid} from 'primeng/fluid';
import {UserService} from '../../../settings/user-management/user.service';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component';

export enum EntityType {
  LIBRARY = 'Library',
  SHELF = 'Shelf',
  ALL_BOOKS = 'All Books'
}

const QUERY_PARAMS = {
  VIEW: 'view',
  SORT: 'sort',
  DIRECTION: 'direction',
  FILTER: 'filter'
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
  imports: [Button, NgIf, VirtualScrollerModule, BookCardComponent, AsyncPipe, ProgressSpinner, Menu, NgForOf, InputText, FormsModule, BookTableComponent, BookFilterComponent, Tooltip, NgClass, Fluid, PrimeTemplate],
  animations: [
    trigger('slideInOut', [
      state('void', style({
        transform: 'translateY(100%)'
      })),
      state('*', style({
        transform: 'translateY(0)'
      })),
      transition(':enter', [
        animate('0.1s ease-in')
      ]),
      transition(':leave', [
        animate('0.1s ease-out')
      ])
    ])
  ]
})
export class BookBrowserComponent implements OnInit, AfterViewInit {
  bookState$: Observable<BookState> | undefined;
  entity$: Observable<Library | Shelf | null> | undefined;
  entityType$: Observable<EntityType> | undefined;
  bookOrAuthor$ = new BehaviorSubject<string>('');

  entity: Library | Shelf | null = null;
  entityType: EntityType | undefined;
  bookTitle: string = '';
  entityOptions: MenuItem[] | undefined;
  selectedBooks = new Set<number>();
  isDrawerVisible: boolean = false;
  dynamicDialogRef: DynamicDialogRef | undefined;
  EntityType = EntityType;

  @ViewChild(BookTableComponent) bookTableComponent!: BookTableComponent;
  @ViewChild(BookFilterComponent) bookFilterComponent!: BookFilterComponent;

  selectedFilter = new BehaviorSubject<{ type: string; value: any } | null>(null);
  protected resetFilterSubject = new Subject<void>();

  protected userService = inject(UserService);
  private activatedRoute = inject(ActivatedRoute);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private bookService = inject(BookService);
  private shelfService = inject(ShelfService);
  private dialogService = inject(DialogService);
  private sortService = inject(SortService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private libraryShelfMenuService = inject(LibraryShelfMenuService);

  sortOptions: any[] = [
    {label: 'Title', icon: '', field: 'title', command: () => this.sortBooks('title')},
    {label: 'Author', icon: '', field: 'author', command: () => this.sortBooks('author')},
    {label: 'Last Read', field: 'lastReadTime', command: () => this.sortBooks('lastReadTime')},
    {label: 'Added On', field: 'addedOn', command: () => this.sortBooks('addedOn')},
    {label: 'File Size', icon: '', field: 'fileSizeKb', command: () => this.sortBooks('fileSizeKb')},
    {label: 'Locked', icon: '', field: 'locked', command: () => this.sortBooks('locked')},
    {label: 'Publisher', icon: '', field: 'publisher', command: () => this.sortBooks('publisher')},
    {label: 'Published Date', icon: '', field: 'publishedDate', command: () => this.sortBooks('publishedDate')},
    {label: 'Amazon Rating', icon: '', field: 'amazonRating', command: () => this.sortBooks('amazonRating')},
    {label: 'Amazon #', icon: '', field: 'amazonReviewCount', command: () => this.sortBooks('amazonReviewCount')},
    {label: 'Goodreads Rating', icon: '', field: 'goodreadsRating', command: () => this.sortBooks('goodreadsRating')},
    {label: 'Goodreads #', icon: '', field: 'goodreadsReviewCount', command: () => this.sortBooks('goodreadsReviewCount')},
    {label: 'Pages', icon: '', field: 'pageCount', command: () => this.sortBooks('pageCount')}
  ];

  selectedSort: SortOption | undefined = undefined;
  currentViewMode: string | undefined = undefined;
  lastAppliedSort: SortOption | null = null;
  filterVisibility = true;


  ngOnInit(): void {
    this.bookService.loadBooks();

    const isAllBooksRoute = this.activatedRoute.snapshot.routeConfig?.path === 'all-books';

    if (isAllBooksRoute) {
      this.entityType = EntityType.ALL_BOOKS;
      this.entityType$ = of(EntityType.ALL_BOOKS);
      this.entity$ = of(null);
      this.bookState$ = this.fetchAllBooks();
    } else {

      const routeEntityInfo$ = this.getEntityInfoFromRoute();

      this.bookState$ = routeEntityInfo$.pipe(
        switchMap(({entityId, entityType}) => this.fetchBooksByEntity(entityId, entityType)),
      );

      this.entity$ = routeEntityInfo$.pipe(
        switchMap(({entityId, entityType}) => this.fetchEntity(entityId, entityType))
      );

      this.entityType$ = routeEntityInfo$.pipe(
        map(({entityType}) => entityType)
      );

      this.entity$?.subscribe((entity) => {
        if (!entity) {
          this.entityOptions = [];
          return;
        }
        if (this.isLibrary(entity)) {
          this.entity = entity;
          this.entityOptions = this.libraryShelfMenuService.initializeLibraryMenuItems(entity);
        } else {
          this.entity = entity;
          this.entityOptions = this.libraryShelfMenuService.initializeShelfMenuItems(entity);
        }
      });
    }

    this.activatedRoute.paramMap.subscribe(() => {
      this.bookOrAuthor$.next('');
      this.bookTitle = '';
      this.deselectAllBooks();
      this.clearFilter();
    });
  }

  private isLibrary(entity: Library | Shelf): entity is Library {
    return (entity as Library).paths !== undefined;
  }

  toggleTableGrid(): void {
    this.currentViewMode = this.currentViewMode === VIEW_MODES.GRID ? VIEW_MODES.TABLE : VIEW_MODES.GRID;
    this.router.navigate([], {
      queryParams: {view: this.currentViewMode},
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  get viewIcon(): string {
    return this.currentViewMode === VIEW_MODES.GRID ? 'pi pi-objects-column' : 'pi pi-table';
  }

  private getEntityInfoFromRoute(): Observable<{ entityId: number; entityType: EntityType }> {
    return this.activatedRoute.paramMap.pipe(
      map(params => {
        const libraryId = Number(params.get('libraryId') || NaN);
        const shelfId = Number(params.get('shelfId') || NaN);
        if (!isNaN(libraryId)) {
          this.entityType = EntityType.LIBRARY;
          return {entityId: libraryId, entityType: EntityType.LIBRARY};
        } else if (!isNaN(shelfId)) {
          this.entityType = EntityType.SHELF;
          return {entityId: shelfId, entityType: EntityType.SHELF};
        } else {
          return {entityId: NaN, entityType: EntityType.ALL_BOOKS};
        }
      })
    );
  }

  private fetchEntity(entityId: number, entityType: EntityType): Observable<Library | Shelf | null> {
    if (entityType == EntityType.LIBRARY) {
      return this.fetchLibrary(entityId);
    } else if (EntityType.SHELF) {
      return this.fetchShelf(entityId);
    }
    return of(null);
  }

  private fetchBooksByEntity(entityId: number, entityType: EntityType): Observable<BookState> {
    if (entityType == EntityType.LIBRARY) {
      return this.fetchBooksByLibrary(entityId);
    } else if (entityType == EntityType.SHELF) {
      return this.fetchBooksByShelf(entityId);
    } else {
      return this.fetchAllBooks();
    }
  }

  private fetchBooksByLibrary(libraryId: number): Observable<BookState> {
    return this.fetchBooks(book => book.libraryId === libraryId);
  }

  private fetchBooksByShelf(shelfId: number): Observable<BookState> {
    return this.fetchBooks(book => {
      return book.shelves?.some(shelf => shelf.id === shelfId) ?? false;
    });
  }

  private fetchAllBooks(): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => this.processBookState(bookState)),
      switchMap(bookState => this.headerFilter(bookState)),
      switchMap(bookState => this.sideBarFilter(bookState))
    );
  }

  private sideBarFilter(bookState: BookState): Observable<BookState> {
    return this.selectedFilter.pipe(
      map(selectedFilter => {
        if (!selectedFilter) {
          return bookState;
        }
        let filteredBooks = bookState.books || [];
        switch (selectedFilter.type) {
          case 'author':
            filteredBooks = filteredBooks.filter(book =>
              book.metadata?.authors?.some(author => author === selectedFilter.value)
            );
            break;
          case 'category':
            filteredBooks = filteredBooks.filter(book =>
              book.metadata?.categories?.some(category => category === selectedFilter.value)
            );
            break;
          case 'publisher':
            filteredBooks = filteredBooks.filter(book =>
              book.metadata?.publisher === selectedFilter.value
            );
            break;
          case 'award':
            filteredBooks = filteredBooks.filter(book =>
              book.metadata?.awards?.some(
                award => award.name === selectedFilter.value.name && award.designation === 'WINNER'
              )
            );
            break;
          case 'series':
            filteredBooks = filteredBooks.filter(book =>
              book.metadata?.seriesName === selectedFilter.value
            );
            break;
          default:
            break;
        }
        return {...bookState, books: filteredBooks};
      })
    );
  }

  private headerFilter(bookState: BookState): Observable<BookState> {
    return this.bookOrAuthor$.pipe(
      map(title => {
        if (title && title.trim() !== '') {
          const filteredBooks = bookState.books?.filter(book => {
            const matchesTitle = book.metadata?.title?.toLowerCase().includes(title.toLowerCase());
            const matchesAuthor = book.metadata?.authors.some(author =>
              author.toLowerCase().includes(title.toLowerCase())
            );
            return matchesTitle || matchesAuthor;
          }) || null;
          return {...bookState, books: filteredBooks};
        }
        return bookState;
      })
    );
  }

  private fetchBooks(bookFilter: (book: Book) => boolean): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => {
        if (bookState.loaded && !bookState.error) {
          const filteredBooks = bookState.books?.filter(bookFilter) || [];
          const sortedBooks = this.sortService.applySort(filteredBooks, this.selectedSort!);
          return {...bookState, books: sortedBooks};
        }
        return bookState;
      }),
      switchMap(bookState => this.headerFilter(bookState)),
      switchMap(bookState => this.sideBarFilter(bookState))
    );
  }

  private processBookState(bookState: BookState): BookState {
    if (bookState.loaded && !bookState.error) {
      const sortedBooks = this.sortService.applySort(bookState.books || [], this.selectedSort!);
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

  deselectAllBooks(): void {
    this.selectedBooks.clear();
    this.isDrawerVisible = false;
    if (this.bookTableComponent) {
      this.bookTableComponent.clearSelectedBooks();
    }
  }

  unshelfBooks() {
    if (this.entity) {
      this.bookService.updateBookShelves(this.selectedBooks, new Set(), new Set([this.entity.id])).subscribe(
        {
          next: () => {
            this.messageService.add({severity: 'info', summary: 'Success', detail: 'Books shelves updated'});
            this.selectedBooks = new Set<number>();
          },
          error: () => {
            this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update books shelves'});
          }
        }
      );
    }
  }

  applySortOption(sortOption: SortOption): void {
    if (this.entityType === EntityType.ALL_BOOKS) {
      this.bookState$ = this.fetchAllBooks();
    } else {
      const routeParam$ = this.getEntityInfoFromRoute();
      this.bookState$ = routeParam$.pipe(
        switchMap(({entityId, entityType}) => this.fetchBooksByEntity(entityId, entityType))
      );
    }
  }

  onBookTitleChange(newTitle: string): void {
    this.bookOrAuthor$.next(newTitle);
  }

  clearSearch(): void {
    this.bookTitle = '';
    this.onBookTitleChange('');
    this.resetFilters();
  }

  resetFilters() {
    this.resetFilterSubject.next();
  }

  openShelfAssigner() {
    this.dynamicDialogRef = this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Books' Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {
        position: 'absolute',
        top: '15%',
      },
      data: {
        isMultiBooks: true,
        bookIds: this.selectedBooks
      },
    });
    this.dynamicDialogRef.onClose.subscribe(() => {
      this.selectedBooks.clear();
    });
  }

  updateMetadata() {
    this.dialogService.open(MetadataFetchOptionsComponent, {
      header: 'Metadata Refresh Options',
      modal: true,
      closable: true,
      data: {
        bookIds: Array.from(this.selectedBooks),
        metadataRefreshType: MetadataRefreshType.BOOKS
      }
    })
  }

  toggleFilterSidebar() {
    this.bookFilterComponent.showFilters = !this.bookFilterComponent.showFilters;

    this.router.navigate([], {
      queryParams: {
        [QUERY_PARAMS.FILTER]: this.bookFilterComponent.showFilters.toString()
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  get isFilterActive(): boolean {
    return this.selectedFilter.value !== null;
  }

  clearFilter() {
    if (this.selectedFilter.value !== null) {
      this.selectedFilter.next(null);
    }
    this.clearSearch();
  }

  sortBooks(field: string): void {
    const existingSort = this.sortOptions.find(opt => opt.field === field);

    if (!existingSort) return;

    if (this.selectedSort?.field === field) {
      this.selectedSort = {
        ...this.selectedSort,
        direction: this.selectedSort.direction === SortDirection.ASCENDING
          ? SortDirection.DESCENDING
          : SortDirection.ASCENDING
      };
    } else {
      this.selectedSort = {
        label: existingSort.label,
        field: existingSort.field,
        direction: SortDirection.ASCENDING
      };
    }

    this.updateSortOptions();
    this.applySortOption(this.selectedSort);

    this.router.navigate([], {
      queryParams: {
        sort: this.selectedSort.field,
        direction: this.selectedSort.direction === SortDirection.ASCENDING ? SORT_DIRECTION.ASCENDING : SORT_DIRECTION.DESCENDING
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  updateSortOptions() {
    const directionIcon = this.selectedSort!.direction === SortDirection.ASCENDING ? 'pi pi-arrow-up' : 'pi pi-arrow-down';
    this.sortOptions = this.sortOptions.map((option) => ({
      ...option,
      icon: option.field === this.selectedSort!.field ? directionIcon : '',
    }));
  }

  ngAfterViewInit() {
    this.bookFilterComponent.filterSelected.subscribe((item) => {
      this.selectedFilter.next(item);
    });

    this.activatedRoute.queryParamMap.subscribe(paramMap => {
      const viewParam = paramMap.get(QUERY_PARAMS.VIEW);
      const sortParam = paramMap.get(QUERY_PARAMS.SORT);
      const directionParam = paramMap.get(QUERY_PARAMS.DIRECTION);
      const filterParam = paramMap.get(QUERY_PARAMS.FILTER);

      this.currentViewMode = viewParam === VIEW_MODES.TABLE || viewParam === VIEW_MODES.GRID ? viewParam : VIEW_MODES.GRID;

      const matchingSort = this.sortOptions.find(opt => opt.field === sortParam);
      const direction = directionParam === SORT_DIRECTION.DESCENDING ? SortDirection.DESCENDING : SortDirection.ASCENDING;
      this.bookFilterComponent.showFilters = filterParam === 'true' || (filterParam === null && this.filterVisibility);

      if (matchingSort) {
        this.selectedSort = {
          label: matchingSort.label,
          field: matchingSort.field,
          direction
        };
      } else {
        this.selectedSort = {
          label: 'Added On',
          field: 'addedOn',
          direction: SortDirection.DESCENDING
        };
      }

      if (this.lastAppliedSort?.field !== this.selectedSort.field ||
        this.lastAppliedSort?.direction !== this.selectedSort.direction) {
        this.lastAppliedSort = {...this.selectedSort};
        this.applySortOption(this.selectedSort);
      }

      const queryParams: any = {
        [QUERY_PARAMS.VIEW]: this.currentViewMode,
        [QUERY_PARAMS.SORT]: this.selectedSort.field,
        [QUERY_PARAMS.DIRECTION]: this.selectedSort.direction === SortDirection.ASCENDING ? SORT_DIRECTION.ASCENDING : SORT_DIRECTION.DESCENDING,
        [QUERY_PARAMS.FILTER]: this.bookFilterComponent.showFilters.toString()
      };

      const currentParams = this.activatedRoute.snapshot.queryParams;

      if (
        currentParams[QUERY_PARAMS.VIEW] !== queryParams[QUERY_PARAMS.VIEW] ||
        currentParams[QUERY_PARAMS.SORT] !== queryParams[QUERY_PARAMS.SORT] ||
        currentParams[QUERY_PARAMS.DIRECTION] !== queryParams[QUERY_PARAMS.DIRECTION] ||
        currentParams[QUERY_PARAMS.FILTER] !== queryParams[QUERY_PARAMS.FILTER]
      ) {
        this.router.navigate([], {
          queryParams,
          replaceUrl: true
        });
      }
    });

    this.cdr.detectChanges();
  }

  lockUnlockMetadata() {
    this.dynamicDialogRef = this.dialogService.open(LockUnlockMetadataDialogComponent, {
      header: 'Toggle Metadata Lock',
      modal: true,
      closable: true,
      data: {
        bookIds: Array.from(this.selectedBooks)
      }
    });
    this.dynamicDialogRef.onClose.subscribe(() => {
      this.deselectAllBooks();
    });
  }
}
