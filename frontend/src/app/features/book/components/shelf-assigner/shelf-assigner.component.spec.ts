import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Observable, of, throwError} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';

import {LoadingService} from '../../../../core/services/loading.service';
import {UserService} from '../../../settings/user-management/user.service';
import {Book} from '../../model/book.model';
import {Shelf} from '../../model/shelf.model';
import {BookService} from '../../service/book.service';
import {ShelfService} from '../../service/shelf.service';
import {BookDialogHelperService} from '../book-browser/book-dialog-helper.service';
import {ShelfAssignerComponent} from './shelf-assigner.component';

interface CurrentUserLike {
  id: number;
  userSettings: {
    sidebarShelfSorting: {
      field: string;
      order: string;
    };
  };
}

describe('ShelfAssignerComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function createShelf(overrides: Partial<Shelf> = {}): Shelf {
    return {
      id: 1,
      name: 'Shelf',
      icon: null,
      iconType: null,
      userId: 7,
      ...overrides,
    };
  }

  function createBook(overrides: Partial<Book> = {}): Book {
    return {
      id: 101,
      libraryId: 1,
      libraryName: 'Library',
      shelves: [],
      ...overrides,
    };
  }

  function createCurrentUser(
    sorting: {field: string; order: string} = {field: 'name', order: 'asc'},
    id: number = 7,
  ): CurrentUserLike {
    return {
      id,
      userSettings: {
        sidebarShelfSorting: sorting,
      },
    };
  }

  function createHarness(options: {
    book?: Book;
    bookIds?: Set<number>;
    isMultiBooks?: boolean;
    shelves?: Shelf[];
    currentUser?: CurrentUserLike | null;
    updateResult?: Observable<Book[]>;
  } = {}) {
    const shelves = signal(options.shelves ?? []);
    const currentUser = signal<CurrentUserLike | null>(options.currentUser ?? createCurrentUser());
    const updateBookShelves = vi.fn(() => options.updateResult ?? of([] as Book[]));
    const dialogRef = {close: vi.fn()};
    const messageService = {add: vi.fn()};
    const bookDialogHelper = {openShelfCreatorDialog: vi.fn()};
    const loader = document.createElement('div');
    const loadingService = {
      show: vi.fn(() => loader),
      hide: vi.fn(),
    };
    const translate = vi.fn(<T = string>(key: string, params?: Record<string, unknown>) => {
      if (typeof params?.['count'] === 'number') {
        return `translated:${key}:${String(params['count'])}` as T;
      }

      return `translated:${key}` as T;
    });

    TestBed.configureTestingModule({
      providers: [
        {
          provide: ShelfService,
          useValue: {shelves},
        },
        {
          provide: DynamicDialogConfig,
          useValue: {
            data: {
              book: options.book ?? createBook(),
              bookIds: options.bookIds ?? new Set<number>(),
              isMultiBooks: options.isMultiBooks ?? false,
            },
          },
        },
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: MessageService, useValue: messageService},
        {provide: BookService, useValue: {updateBookShelves}},
        {provide: BookDialogHelperService, useValue: bookDialogHelper},
        {provide: LoadingService, useValue: loadingService},
        {provide: UserService, useValue: {currentUser}},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new ShelfAssignerComponent());
    TestBed.flushEffects();

    return {
      component,
      shelves,
      currentUser,
      updateBookShelves,
      dialogRef,
      messageService,
      bookDialogHelper,
      loadingService,
      loader,
      translate,
    };
  }

  it('initializes selected shelves from the injected book once shelf data is available', () => {
    const unread = createShelf({id: 1, name: 'Unread'});
    const favorites = createShelf({id: 2, name: 'Favorites'});
    const foreignShelf = createShelf({id: 3, name: 'Other User Shelf', userId: 8});
    const book = createBook({
      shelves: [
        createShelf({id: 2, name: 'Favorites'}),
      ],
    });

    const {component} = createHarness({
      book,
      shelves: [unread, favorites, foreignShelf],
    });

    expect(component.selectedShelves).toEqual([favorites]);
    expect(component.isShelfSelected(favorites)).toBe(true);
    expect(component.isShelfSelected(unread)).toBe(false);
  });

  it('falls back to name ascending sorting when sidebar preferences are invalid', () => {
    const {component} = createHarness({
      currentUser: createCurrentUser({field: 'createdAt', order: 'SIDEWAYS'}),
      shelves: [
        createShelf({id: 2, name: 'Zulu'}),
        createShelf({id: 1, name: 'Alpha'}),
        createShelf({id: 3, name: 'Hidden', userId: 42}),
      ],
    });

    expect(component.shelves().map(shelf => shelf.name)).toEqual(['Alpha', 'Zulu']);
  });

  it('sorts shelves by id descending when the stored sidebar preferences are valid', () => {
    const {component} = createHarness({
      currentUser: createCurrentUser({field: 'id', order: 'desc'}),
      shelves: [
        createShelf({id: 1, name: 'One'}),
        createShelf({id: 3, name: 'Three'}),
        createShelf({id: 2, name: 'Two'}),
      ],
    });

    expect(component.shelves().map(shelf => shelf.id)).toEqual([3, 2, 1]);
  });

  it('filters shelves by a trimmed case-insensitive query', () => {
    const {component} = createHarness();
    const shelves = [
      createShelf({id: 1, name: 'Science Fiction'}),
      createShelf({id: 2, name: 'History'}),
      createShelf({id: 3, name: 'Sci-Fi Favorites'}),
    ];

    component.searchQuery = ' sci ';

    expect(component.filterShelves(shelves).map(shelf => shelf.name)).toEqual([
      'Science Fiction',
      'Sci-Fi Favorites',
    ]);
  });

  it('maps custom svg and PrimeNG shelf icons to icon-display selections', () => {
    const {component} = createHarness();

    expect(component.getShelfIcon(createShelf({
      iconType: 'CUSTOM_SVG',
      icon: 'custom-icons/moon.svg',
    }))).toEqual({
      type: 'CUSTOM_SVG',
      value: 'custom-icons/moon.svg',
    });
    expect(component.getShelfIcon(createShelf({
      iconType: 'PRIME_NG',
      icon: 'bookmark',
    }))).toEqual({
      type: 'PRIME_NG',
      value: 'pi pi-bookmark',
    });
  });

  it('shapes assign and unassign payloads for a single-book update and closes with success', () => {
    const existing = createShelf({id: 1, name: 'Existing'});
    const added = createShelf({id: 2, name: 'Added'});
    const removed = createShelf({id: 3, name: 'Removed'});
    const {component, updateBookShelves, loadingService, loader, messageService, dialogRef, translate} = createHarness({
      book: createBook({
        id: 11,
        shelves: [existing, removed],
      }),
    });

    component.selectedShelves = [existing, added];

    component.updateBooksShelves();

    expect(translate).toHaveBeenCalledWith('book.shelfAssigner.loading.updatingShelves', {count: 1});
    expect(loadingService.show).toHaveBeenCalledWith('translated:book.shelfAssigner.loading.updatingShelves:1');
    expect(updateBookShelves).toHaveBeenCalledWith(
      new Set([11]),
      new Set([1, 2]),
      new Set([3]),
    );
    expect(loadingService.hide).toHaveBeenCalledWith(loader);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'info',
      summary: 'translated:common.success',
      detail: 'translated:book.shelfAssigner.toast.updateSuccessDetail',
    });
    expect(dialogRef.close).toHaveBeenCalledWith({assigned: true});
  });

  it('uses the injected multi-book ids, skips unassigns, and closes with failure on update errors', () => {
    const selected = createShelf({id: 5, name: 'Selected'});
    const targetBookIds = new Set([41, 42]);
    const {component, updateBookShelves, loadingService, loader, messageService, dialogRef, translate} = createHarness({
      book: createBook({
        id: 11,
        shelves: [createShelf({id: 9, name: 'Ignored Existing'})],
      }),
      bookIds: targetBookIds,
      isMultiBooks: true,
      updateResult: throwError(() => new Error('update failed')),
    });

    component.selectedShelves = [selected];

    component.updateBooksShelves();

    expect(translate).toHaveBeenCalledWith('book.shelfAssigner.loading.updatingShelves', {count: 2});
    expect(updateBookShelves).toHaveBeenCalledWith(
      targetBookIds,
      new Set([5]),
      new Set(),
    );
    expect(loadingService.hide).toHaveBeenCalledWith(loader);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:common.error',
      detail: 'translated:book.shelfAssigner.toast.updateFailedDetail',
    });
    expect(dialogRef.close).toHaveBeenCalledWith({assigned: false});
  });
});
