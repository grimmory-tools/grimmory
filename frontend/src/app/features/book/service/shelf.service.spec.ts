import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushQueryAsync, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import type {Shelf} from '../model/shelf.model';
import {shelfDefinitionQueryKeys} from '../data/shelf-definition-query-keys';
import {bookQueryKeys} from '../data/book-query-keys';
import {AuthService} from '../../../shared/service/auth.service';
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

describe('ShelfService', () => {
  let service: ShelfService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;
  let bookService: {
    removeBooksFromShelf: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    bookService = {
      removeBooksFromShelf: vi.fn(),
    };

    vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        ShelfService,
        {provide: AuthService, useValue: authService},
        {provide: BookService, useValue: bookService},
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

  it('reloads the shelves list on demand', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    service.reloadShelves();

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['shelves'], exact: true});
  });

  it('invalidates shelf and shelf-definition caches when creating a shelf, without touching book caches', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    service.createShelf(buildShelf({name: 'New Shelf'})).subscribe();
    const createRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves'));
    expect(createRequest.request.method).toBe('POST');
    createRequest.flush(buildShelf({id: 11, name: 'New Shelf'}));

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['shelves'], exact: true});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: shelfDefinitionQueryKeys.all()});
    expect(invalidateQueriesSpy).not.toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });

  it('invalidates shelf-definition and book caches when renaming a shelf', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    service.updateShelf(buildShelf({name: 'Updated Shelf'}), 11).subscribe();
    const updateRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves/11'));
    expect(updateRequest.request.method).toBe('PUT');
    updateRequest.flush(buildShelf({id: 11, name: 'Updated Shelf'}));

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['shelves'], exact: true});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: shelfDefinitionQueryKeys.all()});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['books'], exact: true});
  });

  it('invalidates shelf caches and delegates book removal when deleting a shelf', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves')).flush([]);

    service.deleteShelf(11).subscribe();
    const deleteRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/shelves/11'));
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['shelves'], exact: true});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: shelfDefinitionQueryKeys.all()});
    expect(bookService.removeBooksFromShelf).toHaveBeenCalledWith(11);
  });

});
