import {HttpTestingController} from '@angular/common/http/testing';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushQueryAsync, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import type {Book} from '../model/book.model';
import type {Shelf} from '../model/shelf.model';
import {AuthService} from '../../../shared/service/auth.service';
import {UserService} from '../../settings/user-management/user.service';
import {BookService} from './book.service';
import {ShelfService} from './shelf.service';

function buildShelf(overrides: Partial<Shelf> = {}): Shelf {
  return {
    id: 1,
    name: 'Favorites',
    userId: 7,
    bookCount: 0,
    ...overrides,
  };
}

function buildBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Main Library',
    ...overrides,
  };
}

describe('ShelfService', () => {
  let service: ShelfService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;
  let bookService: {
    books: ReturnType<typeof vi.fn>;
    removeBooksFromShelf: ReturnType<typeof vi.fn>;
  };
  let userService: {
    getCurrentUser: ReturnType<typeof vi.fn>;
  };
  let currentUser: ReturnType<typeof signal<{id: number} | null>>;

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    bookService = {
      books: vi.fn(() => []),
      removeBooksFromShelf: vi.fn(),
    };
    currentUser = signal<{id: number} | null>(null);
    userService = {
      getCurrentUser: vi.fn(() => currentUser()),
    };

    vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        ShelfService,
        {provide: AuthService, useValue: authService},
        {provide: BookService, useValue: bookService},
        {provide: UserService, useValue: userService},
      ],
    });

    service = TestBed.inject(ShelfService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClientHarness.queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  async function flushShelvesQueryResult(): Promise<void> {
    await flushQueryAsync();
  }

  it('eagerly fetches shelves and hydrates the computed shelves signal', async () => {
    const response = [
      buildShelf({id: 1, name: 'Reading', userId: 7}),
      buildShelf({id: 2, name: 'Archive', userId: 9, bookCount: 5}),
    ];

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves'));
    expect(request.request.method).toBe('GET');
    request.flush(response);
    await flushShelvesQueryResult();

    expect(service.shelves()).toEqual([
      expect.objectContaining({id: 1, name: 'Reading', systemKey: null}),
      expect.objectContaining({id: 2, name: 'Archive', systemKey: null}),
    ]);
    expect(service.shelvesError()).toBeNull();
  });

  it('marks the Kobo system shelf when the backend returns the known Kobo shelf shape', async () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([
      buildShelf({id: 1, name: 'Kobo', icon: 'pi pi-tablet'}),
      buildShelf({id: 2, name: 'Archive', icon: 'pi pi-folder'}),
    ]);
    await flushShelvesQueryResult();

    expect(service.shelves()).toEqual([
      expect.objectContaining({id: 1, name: 'Kobo', systemKey: 'kobo'}),
      expect.objectContaining({id: 2, name: 'Archive', systemKey: null}),
    ]);
  });

  it('removes shelf queries when the auth token is cleared', () => {
    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: ['shelves']});
  });

  it('invalidates shelf queries for reload/create/update/delete flows and removes books on delete', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    service.reloadShelves();

    service.createShelf(buildShelf({name: 'New Shelf'})).subscribe();
    const createRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves'));
    expect(createRequest.request.method).toBe('POST');
    createRequest.flush(buildShelf({id: 11, name: 'New Shelf'}));

    service.updateShelf(buildShelf({name: 'Updated Shelf'}), 11).subscribe();
    const updateRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves/11'));
    expect(updateRequest.request.method).toBe('PUT');
    updateRequest.flush(buildShelf({id: 11, name: 'Updated Shelf'}));

    service.deleteShelf(11).subscribe();
    const deleteRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves/11'));
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);

    expect(invalidateQueriesSpy).toHaveBeenCalledTimes(4);
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['shelves'], exact: true});
    expect(bookService.removeBooksFromShelf).toHaveBeenCalledWith(11);
  });

  it('uses owner-aware shelf counts and falls back to persisted counts for non-owners', async () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([
      buildShelf({id: 1, userId: 7, bookCount: 99}),
      buildShelf({id: 2, userId: 10, bookCount: 6}),
    ]);
    await flushShelvesQueryResult();

    bookService.books.mockReturnValue([
      buildBook(1, {shelves: [buildShelf({id: 1, name: 'Reading'})]}),
      buildBook(2, {shelves: [buildShelf({id: 1, name: 'Reading'})]}),
      buildBook(3, {shelves: [buildShelf({id: 2, name: 'Archive'})]}),
    ]);

    currentUser.set({id: 7});
    expect(service.getBookCountValue(1)).toBe(2);

    currentUser.set({id: 42});
    expect(service.getBookCountValue(2)).toBe(6);
    expect(service.getBookCountValue(999)).toBe(0);
  });

  it('builds shelf count maps with local counts for the owner and persisted counts for shared shelves', async () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([
      buildShelf({id: 1, userId: 7, bookCount: 99}),
      buildShelf({id: 2, userId: 10, bookCount: 6}),
    ]);
    await flushShelvesQueryResult();

    bookService.books.mockReturnValue([
      buildBook(1, {shelves: [buildShelf({id: 1, name: 'Reading'})]}),
      buildBook(2, {shelves: [buildShelf({id: 1, name: 'Reading'})]}),
      buildBook(3, {shelves: [buildShelf({id: 2, name: 'Archive'})]}),
    ]);

    currentUser.set({id: 7});
    flushSignalAndQueryEffects();

    const counts = service.bookCountByShelfId();

    expect(counts.get(1)).toBe(2);
    expect(counts.get(2)).toBe(6);
  });

  it('counts unshelved books from the current book cache snapshot', () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    bookService.books.mockReturnValue([
      buildBook(1, {shelves: [buildShelf({id: 1, name: 'Reading'})]}),
      buildBook(2, {shelves: []}),
      buildBook(3, {shelves: undefined}),
    ]);

    expect(service.getUnshelvedBookCountValue()).toBe(2);
  });
});
