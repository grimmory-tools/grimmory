import {signal, WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, ParamMap, Router} from '@angular/router';
import {BehaviorSubject, Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ConfirmationService, MessageService} from 'primeng/api';

import {PageTitleService} from '../../../../shared/service/page-title.service';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {Book} from '../../model/book.model';
import {SortDirection, SortOption} from '../../model/sort.model';
import {UserService} from '../../../settings/user-management/user.service';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';
import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {BookDialogHelperService} from './book-dialog-helper.service';
import {TableColumnPreferenceService} from './table-column-preference.service';
import {BookMenuService} from '../../service/book-menu.service';
import {SidebarFilterTogglePrefService} from './filters/sidebar-filter-toggle-pref.service';
import {TaskHelperService} from '../../../settings/task-management/task-helper.service';
import {LoadingService} from '../../../../core/services/loading.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {BookNavigationService} from '../../service/book-navigation.service';
import {BookCardOverlayPreferenceService} from './book-card-overlay-preference.service';
import {BookSelectionService} from './book-selection.service';
import {BookBrowserQueryParamsService, VIEW_MODES} from './book-browser-query-params.service';
import {BookBrowserEntityService} from './book-browser-entity.service';
import {RouteScrollPositionService} from '../../../../shared/service/route-scroll-position.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {BookBrowserComponent, EntityType} from './book-browser.component';
import {SortService} from '../../service/sort.service';
import {TranslocoService} from '@jsverse/transloco';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {type VirtualGridMetrics} from '../../../../shared/util/virtual-grid.util';

function makeBook(id: number, libraryId: number, title: string, addedOn: string): Book {
  return {
    id,
    libraryId,
    metadata: {
      bookId: id,
      title,
    },
    addedOn,
  } as Book;
}

function makeCurrentUser() {
  return {
    id: 1,
    username: 'tester',
    name: 'Tester',
    email: 'tester@example.com',
    assignedLibraries: [],
    permissions: {
      admin: false,
      canUpload: false,
      canDownload: false,
      canEmailBook: false,
      canDeleteBook: false,
      canEditMetadata: false,
      canManageLibrary: false,
      canManageMetadataConfig: false,
      canSyncKoReader: false,
      canSyncKobo: false,
      canAccessOpds: false,
      canAccessBookdrop: false,
      canAccessLibraryStats: false,
      canAccessUserStats: false,
      canAccessTaskManager: false,
      canManageEmailConfig: false,
      canManageGlobalPreferences: false,
      canManageIcons: false,
      canManageFonts: false,
      demoUser: false,
      canBulkAutoFetchMetadata: false,
      canBulkCustomFetchMetadata: false,
      canBulkEditMetadata: false,
      canBulkRegenerateCover: false,
      canMoveOrganizeFiles: false,
      canBulkLockUnlockMetadata: false,
    },
    userSettings: {
      filterMode: 'and',
      enableSeriesView: false,
      visibleSortFields: ['addedOn', 'title'],
      entityViewPreferences: {
        global: {
          sortKey: 'addedOn',
          sortDir: 'DESC',
          view: 'GRID',
          coverSize: 1,
          seriesCollapsed: false,
          overlayBookType: true,
        },
        overrides: [],
      },
    },
  } as const;
}

interface BookBrowserHarness {
  component: BookBrowserComponent;
  books: WritableSignal<Book[]>;
  booksError: WritableSignal<string | null>;
  isBooksLoading: WritableSignal<boolean>;
  paramMap$: BehaviorSubject<ParamMap>;
  setHasNextPage: (value: boolean) => void;
  setIsFetchingNextPage: (value: boolean) => void;
  queryParamsService: {
    shouldForceExpandSeries: ReturnType<typeof vi.fn>;
    updateViewMode: ReturnType<typeof vi.fn>;
    updateFilters: ReturnType<typeof vi.fn>;
    updateFilterMode: ReturnType<typeof vi.fn>;
    updateMultiSort: ReturnType<typeof vi.fn>;
    parseQueryParams: ReturnType<typeof vi.fn>;
    syncQueryParams: ReturnType<typeof vi.fn>;
  };
  routeSnapshot: {
    routeConfig: {path: string};
    paramMap: ParamMap;
    queryParamMap: ParamMap;
    params: Record<string, string>;
  };
}

function createHarness(options?: {
  books?: Book[];
  totalElements?: number;
  booksError?: string | null;
  isBooksLoading?: boolean;
  translate?: (key: string) => string;
}): BookBrowserHarness {
  const books = signal<Book[]>(
    options?.books ?? [
      makeBook(2, 1, 'Zulu', '2024-02-01T00:00:00Z'),
      makeBook(1, 1, 'Alpha', '2024-01-01T00:00:00Z'),
      makeBook(3, 2, 'Bravo', '2024-03-01T00:00:00Z'),
    ]
  );
  const booksError = signal<string | null>(options?.booksError ?? null);
  const isBooksLoading = signal<boolean>(options?.isBooksLoading ?? false);
  const isFetchingNextPage = signal(false);
  const hasNextPage = signal(false);
  const currentUser = signal(makeCurrentUser());
  const showFilter = signal(false);
  const seriesCollapsed = signal(false);
  const routerEvents$ = new Subject<unknown>();
  const paramMap$ = new BehaviorSubject(convertToParamMap({libraryId: '1'}));
  const queryParamMap$ = new BehaviorSubject(convertToParamMap({}));
  const url$ = new BehaviorSubject<unknown[]>([]);
  const defaultSort: SortOption = {field: 'addedOn', direction: SortDirection.DESCENDING, label: 'Added On'};
  const queryParamsService = {
    parseQueryParams: vi.fn((_q, _u, _et, _ei, _sortOptions, defaultFilterMode) => ({
      viewMode: VIEW_MODES.GRID,
      sortOption: defaultSort,
      sortCriteria: [defaultSort],
      filters: {},
      filterMode: defaultFilterMode,
      viewModeFromToggle: false,
    })),
    syncQueryParams: vi.fn(),
    shouldForceExpandSeries: vi.fn(() => false),
    updateViewMode: vi.fn(),
    updateFilters: vi.fn(),
    updateFilterMode: vi.fn(),
    updateMultiSort: vi.fn(),
  };
  const routeSnapshot = {
    routeConfig: {path: 'library/:libraryId/books'},
    paramMap: paramMap$.value,
    queryParamMap: queryParamMap$.value,
    params: {libraryId: '1'},
  };

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      SortService,
      BookSelectionService,
      BookNavigationService,
      {
        provide: UserService,
        useValue: {
          currentUser: currentUser.asReadonly(),
          getCurrentUser: vi.fn(() => currentUser()),
          updateUserSetting: vi.fn(),
        },
      },
      {
        provide: CoverScalePreferenceService,
        useValue: {
          scaleFactor: vi.fn(() => 1),
          setScale: vi.fn(),
        },
      },
      {
        provide: TableColumnPreferenceService,
        useValue: {
          allColumns: [],
          visibleColumns: [],
          initPreferences: vi.fn(),
          saveVisibleColumns: vi.fn(),
        },
      },
      {
        provide: SidebarFilterTogglePrefService,
        useValue: {
          showFilter: showFilter.asReadonly(),
          toggle: vi.fn(() => showFilter.update(value => !value)),
        },
      },
      {
        provide: SeriesCollapseFilter,
        useValue: {
          seriesCollapsed: seriesCollapsed.asReadonly(),
          setContext: vi.fn(),
          collapseBooks: vi.fn((items: Book[]) => items),
          setCollapsed: vi.fn((value: boolean) => seriesCollapsed.set(value)),
        },
      },
      {
        provide: BookSelectionService,
        useValue: {
          selectedBooks: signal([]),
          selectedCount: signal(0),
          deselectAll: vi.fn(),
          setCurrentBooks: vi.fn(),
        },
      },
      {
        provide: BookNavigationService,
        useValue: {
          setAvailableBookIds: vi.fn(),
        },
      },
      {
        provide: RouteScrollPositionService,
        useValue: {
          createKey: vi.fn(() => 'test-key'),
          keyFor: vi.fn(() => 'test-key'),
          savePosition: vi.fn(),
          getPosition: vi.fn(() => 0),
          trackRoute: vi.fn(),
        },
      },
      {provide: ConfirmationService, useValue: {confirm: vi.fn()}},
      {provide: TaskHelperService, useValue: {}},
      {
        provide: BookCardOverlayPreferenceService,
        useValue: {
          showBookTypePill: vi.fn(() => true),
          setShowBookTypePill: vi.fn(),
        },
      },
      {provide: AppSettingsService, useValue: {appSettings: vi.fn(() => null)}},
      {provide: LayoutService, useValue: {sidebarTransitioning: signal(false)}},
      {
        provide: ActivatedRoute,
        useValue: {
          url: url$.asObservable(),
          paramMap: paramMap$.asObservable(),
          queryParamMap: queryParamMap$.asObservable(),
          snapshot: routeSnapshot,
        },
      },
      {
        provide: Router,
        useValue: {
          events: routerEvents$.asObservable(),
          navigate: vi.fn(),
        },
      },
      {provide: MessageService, useValue: {add: vi.fn()}},
      {
        provide: BookService,
        useValue: {
          books: books.asReadonly(),
          isBooksLoading: isBooksLoading.asReadonly(),
          booksError: booksError.asReadonly(),
        },
      },
      {provide: BookMetadataManageService, useValue: {}},
      {provide: BookDialogHelperService, useValue: {}},
      {
        provide: BookMenuService,
        useValue: {
          getMoreActionsMenu: vi.fn(() => []),
          getMetadataMenuItems: vi.fn(() => []),
        },
      },
      {
        provide: LibraryShelfMenuService,
        useValue: {
          initializeLibraryMenuItems: vi.fn(() => []),
          initializeMagicShelfMenuItems: vi.fn(() => []),
          initializeShelfMenuItems: vi.fn(() => []),
        },
      },
      {provide: PageTitleService, useValue: {setPageTitle: vi.fn()}},
      {provide: LoadingService, useValue: {show: vi.fn(), hide: vi.fn()}},
      {provide: LocalStorageService, useValue: {get: vi.fn(), set: vi.fn()}},
      {
        provide: BookBrowserQueryParamsService,
        useValue: queryParamsService,
      },
      {
        provide: BookBrowserEntityService,
        useValue: {
          getEntityInfo: vi.fn((paramMap: ParamMap) => ({
            entityId: Number(paramMap.get('libraryId')),
            entityType: EntityType.LIBRARY,
          })),
          getEntity: vi.fn((entityId: number) => ({id: entityId, name: `Library ${entityId}`})),
          getBooksByEntity: vi.fn((items: Book[], entityId: number) =>
            items.filter(book => book.libraryId === entityId)
          ),
          isLibrary: vi.fn(() => true),
          isMagicShelf: vi.fn(() => false),
        },
      },
      {
        provide: TranslocoService,
        useValue: {
          langChanges$: new Subject<string>().asObservable(),
          getActiveLang: vi.fn(() => 'en'),
          translate: vi.fn((key: string) => {
            if (options?.translate) {
              return options.translate(key);
            }

            return {
              'book.browser.tooltip.toggleView': 'Toggle between Grid and Table view',
              'book.browser.labels.allBooks': 'All Books',
              'book.browser.labels.unshelvedBooks': 'Unshelved Books',
            }[key] ?? key;
          }),
        },
      },
    ],
  });

  const component = TestBed.runInInjectionContext(() => new BookBrowserComponent());

  return {
    component,
    books,
    booksError,
    isBooksLoading,
    paramMap$,
    setHasNextPage: value => hasNextPage.set(value),
    setIsFetchingNextPage: value => isFetchingNextPage.set(value),
    queryParamsService,
    routeSnapshot,
  };
}

describe('BookBrowserComponent', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('ResizeObserver', class {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('switches view mode through the display settings control', () => {
    const {component, queryParamsService} = createHarness();

    component.currentViewMode.set(VIEW_MODES.GRID);

    component.onViewModeChange(VIEW_MODES.TABLE);

    expect(component.currentViewMode()).toBe(VIEW_MODES.TABLE);
    expect(queryParamsService.updateViewMode).toHaveBeenCalledWith(VIEW_MODES.TABLE);
  });

  it('updates books when sorting changes', () => {
    const {component} = createHarness();

    TestBed.flushEffects();

    expect(component.books().map(book => book.id)).toEqual([2, 1]);

    component.applySortCriteria([
      {label: 'Title', field: 'title', direction: SortDirection.ASCENDING},
    ]);
    TestBed.flushEffects();

    expect(component.books().map(book => book.id)).toEqual([1, 2]);
  });

  it('updates books after a context change', () => {
    const {component, paramMap$, routeSnapshot} = createHarness();

    TestBed.flushEffects();

    expect(component.books().map(book => book.id)).toEqual([2, 1]);

    routeSnapshot.paramMap = convertToParamMap({libraryId: '2'});
    routeSnapshot.params = {libraryId: '2'};
    paramMap$.next(routeSnapshot.paramMap);
    TestBed.flushEffects();

    expect(component.books().map(book => book.id)).toEqual([3]);
  });

  it('hides loading placeholders when the books query is in an error state', () => {
    const {component} = createHarness({
      booksError: 'Failed to load books',
      isBooksLoading: true,
    });

    expect(component.showBooksLoadingPlaceholder()).toBe(false);
    expect(component.showTableLoadingPlaceholder()).toBe(false);
  });

  it('sizes grid loading placeholders to fill the viewport', () => {
    const {component} = createHarness({books: [], isBooksLoading: true});
    const loadingGrid = component as unknown as {
      minimumLoadingGridItemCount(metrics: VirtualGridMetrics): number;
    };

    component.currentViewMode.set(VIEW_MODES.GRID);

    expect(loadingGrid.minimumLoadingGridItemCount({
      viewportWidth: 960,
      viewportHeight: 900,
      columns: 6,
      itemHeight: 200,
      gap: 20,
    })).toBe(36);
  });

  it('calls SeriesCollapseFilter.collapseBooks when computing books', () => {
    createHarness();
    const filter = TestBed.inject(SeriesCollapseFilter);
    const collapseBooksSpy = vi.spyOn(filter, 'collapseBooks');

    vi.runOnlyPendingTimers();
    TestBed.flushEffects();

    expect(collapseBooksSpy).toHaveBeenCalled();
  });

  it.skip('uses the known total book count while more pages are available', () => {
    const {component, setHasNextPage} = createHarness({totalElements: 100});

    setHasNextPage(true);

    vi.runOnlyPendingTimers();
    TestBed.flushEffects();

    expect(component.virtualRowCount()).toBe(100);
    expect(component.virtualGrid.virtualizer.options().count).toBe(100);
  });

  it.skip('uses one unloaded slot for collapsed series while more pages are available', () => {
    const {component, setHasNextPage} = createHarness({totalElements: 100});
    const filter = TestBed.inject(SeriesCollapseFilter);
    filter.setCollapsed(true);
    setHasNextPage(true);

    vi.runOnlyPendingTimers();
    TestBed.flushEffects();

    expect(component.virtualRowCount()).toBe(component.books().length + 1);
    expect(component.virtualGrid.virtualizer.options().count).toBe(component.books().length + 1);
  });

  it('uses the rendered book count once pagination is exhausted', () => {
    const {component} = createHarness();
    const filter = TestBed.inject(SeriesCollapseFilter);
    vi.mocked(filter.collapseBooks).mockImplementation((items: Book[]) => items.slice(0, 1));

    vi.runOnlyPendingTimers();
    TestBed.flushEffects();

    expect(component.books()).toHaveLength(1);
    expect(component.virtualRowCount()).toBe(1);
    expect(component.virtualGrid.virtualizer.options().count).toBe(1);
  });
});
