import {computed, signal, WritableSignal} from '@angular/core';
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
import {SortDirection} from '../../model/sort.model';
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
import {BookBrowserScrollService} from './book-browser-scroll.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {BookBrowserComponent, EntityType} from './book-browser.component';
import {SortService} from '../../service/sort.service';
import {TranslocoService} from '@jsverse/transloco';
import {AppBooksApiService} from '../../service/app-books-api.service';
import {AppBookFilters, AppBookSort} from '../../model/app-book.model';

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
  queryParamsService: {
    shouldForceExpandSeries: ReturnType<typeof vi.fn>;
    updateViewMode: ReturnType<typeof vi.fn>;
    updateFilters: ReturnType<typeof vi.fn>;
    updateFilterMode: ReturnType<typeof vi.fn>;
    updateMultiSort: ReturnType<typeof vi.fn>;
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
  booksError?: string | null;
  isBooksLoading?: boolean;
  translate?: (key: string) => string;
}): BookBrowserHarness {
  const books = signal<Book[]>(
    options?.books ?? [
      makeBook(1, 1, 'Alpha', '2024-01-01T00:00:00Z'),
      makeBook(2, 1, 'Zulu', '2024-02-01T00:00:00Z'),
      makeBook(3, 2, 'Bravo', '2024-03-01T00:00:00Z'),
    ]
  );
  const booksError = signal<string | null>(options?.booksError ?? null);
  const isBooksLoading = signal<boolean>(options?.isBooksLoading ?? false);
  const currentUser = signal(makeCurrentUser());
  const showFilter = signal(false);
  const seriesCollapsed = signal(false);
  const routerEvents$ = new Subject<unknown>();
  const paramMap$ = new BehaviorSubject(convertToParamMap({libraryId: '1'}));
  const queryParamMap$ = new BehaviorSubject(convertToParamMap({}));
  const url$ = new BehaviorSubject<unknown[]>([]);
  const queryParamsService = {
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

  TestBed.configureTestingModule({
    providers: [
      SortService,
      BookSelectionService,
      BookNavigationService,
      BookBrowserScrollService,
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
          currentCardSize: vi.fn(() => ({width: 120, height: 160})),
          gridColumnMinWidth: vi.fn(() => '120px'),
          getCardHeight: vi.fn(() => 160),
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
      {
        provide: AppBooksApiService,
        useFactory: () => {
          const _filters = signal<AppBookFilters>({});
          const _sort = signal<AppBookSort>({field: 'addedOn', dir: 'desc'});
          const _search = signal('');

          const filteredSortedBooks = computed(() => {
            let result = books();
            const f = _filters();
            if (f.libraryId) result = result.filter(b => b.libraryId === f.libraryId);

            const s = _sort();
            const dir = s.dir === 'asc' ? 1 : -1;
            result = [...result].sort((a, b) => {
              if (s.field === 'title') return dir * ((a.metadata?.title ?? '') as string).localeCompare((b.metadata?.title ?? '') as string);
              if (s.field === 'addedOn') return dir * (a.addedOn ?? '').localeCompare(b.addedOn ?? '');
              return 0;
            });
            return result;
          });

          return {
            books: filteredSortedBooks,
            totalElements: computed(() => filteredSortedBooks().length),
            hasNextPage: computed(() => false),
            isLoading: isBooksLoading.asReadonly(),
            isFetchingNextPage: computed(() => false),
            isError: computed(() => !!booksError()),
            error: computed(() => booksError()),
            filterOptions: computed(() => null),
            setFilters: (f: AppBookFilters) => _filters.set(f),
            setSort: (s: AppBookSort) => _sort.set(s),
            setSearch: (s: string) => _search.set(s),
            fetchNextPage: vi.fn(),
            invalidate: vi.fn(),
          };
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
    queryParamsService,
    routeSnapshot,
  };
}

describe('BookBrowserComponent', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('switches view mode through the display settings control', () => {
    const {component, queryParamsService} = createHarness();

    component.currentViewMode = VIEW_MODES.GRID;

    component.onViewModeChange(VIEW_MODES.TABLE);

    expect(component.currentViewMode).toBe(VIEW_MODES.TABLE);
    expect(queryParamsService.updateViewMode).toHaveBeenCalledWith(VIEW_MODES.TABLE);
  });

  it('keeps rendered books visible during same-context refreshes', () => {
    const {component} = createHarness();

    expect(component.books()).toBeUndefined();

    vi.runOnlyPendingTimers();

    expect(component.books()?.map(book => book.id)).toEqual([2, 1]);
    expect(component.isBooksRefreshing()).toBe(false);

    component.applySortCriteria([
      {label: 'Title', field: 'title', direction: SortDirection.ASCENDING},
    ]);
    TestBed.flushEffects();

    expect(component.books()?.map(book => book.id)).toEqual([1, 2]);
    expect(component.hasRenderedBooks()).toBe(true);
    expect(component.isBooksRefreshing()).toBe(false);
  });

  it('clears rendered books until the deferred commit completes after a context change', () => {
    const {component, paramMap$, routeSnapshot} = createHarness();

    vi.runOnlyPendingTimers();
    expect(component.books()?.map(book => book.id)).toEqual([2, 1]);

    routeSnapshot.paramMap = convertToParamMap({libraryId: '2'});
    routeSnapshot.params = {libraryId: '2'};
    paramMap$.next(routeSnapshot.paramMap);
    TestBed.flushEffects();

    expect(component.books()).toBeUndefined();
    expect(component.hasRenderedBooks()).toBe(false);

    vi.runOnlyPendingTimers();

    expect(component.books()?.map(book => book.id)).toEqual([3]);
    expect(component.hasRenderedBooks()).toBe(true);
  });

  it('hides loading placeholders when the books query is in an error state', () => {
    const {component} = createHarness({
      booksError: 'Failed to load books',
      isBooksLoading: true,
    });

    expect(component.showBooksLoadingPlaceholder).toBe(false);
    expect(component.showGridLoadingPlaceholder).toBe(false);
    expect(component.showTableLoadingPlaceholder).toBe(false);
  });
});
