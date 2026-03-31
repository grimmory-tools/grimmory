import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import type {Library} from '../model/library.model';
import {BookService} from './book.service';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {LIBRARIES_QUERY_KEY, libraryFormatCountsQueryKey} from './library-query-keys';
import {LibraryService} from './library.service';

function buildLibrary(overrides: Partial<Library> = {}): Library {
  return {
    id: 1,
    name: 'Zeta Library',
    watch: true,
    paths: [{path: '/books'}],
    ...overrides,
  };
}

describe('LibraryService', () => {
  let service: LibraryService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;
  let bookService: {
    books: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    bookService = {
      books: vi.fn(() => []),
    };

    vi.spyOn(queryClientHarness.queryClient, 'invalidateQueries').mockResolvedValue(undefined);
    vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        LibraryService,
        {provide: AuthService, useValue: authService},
        {provide: BookService, useValue: bookService},
      ],
    });

    service = TestBed.inject(LibraryService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClientHarness.queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('refreshes and patches library metadata endpoints while invalidating the library cache', () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries')).flush([]);

    service.refreshLibrary(9).subscribe();
    const refreshRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries/9/refresh'));
    expect(refreshRequest.request.method).toBe('PUT');
    refreshRequest.flush(null);

    service.updateLibraryFileNamingPattern(9, '{Authors}/{Title}').subscribe();
    const patternRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries/9/file-naming-pattern'));
    expect(patternRequest.request.method).toBe('PATCH');
    expect(patternRequest.request.body).toEqual({fileNamingPattern: '{Authors}/{Title}'});
    patternRequest.flush(buildLibrary({id: 9, fileNamingPattern: '{Authors}/{Title}'}));

    expect(queryClientHarness.queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: LIBRARIES_QUERY_KEY, exact: true});
  });

  it('invalidates library and book caches after update and delete flows', () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries')).flush([]);

    service.updateLibrary(buildLibrary({name: 'Updated'}), 4).subscribe();
    const updateRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries/4'));
    expect(updateRequest.request.method).toBe('PUT');
    updateRequest.flush(buildLibrary({id: 4, name: 'Updated'}));

    service.deleteLibrary(4).subscribe();
    const deleteRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries/4'));
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);

    expect(queryClientHarness.queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: LIBRARIES_QUERY_KEY, exact: true});
    expect(queryClientHarness.queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
    expect(queryClientHarness.queryClient.removeQueries).toHaveBeenCalledWith({queryKey: libraryFormatCountsQueryKey(4), exact: true});
  });

  it('hydrates format counts through ensureQueryData and tracks large-library loading state', async () => {
    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries')).flush([]);

    const resultPromise = new Promise<Record<string, number>>((resolve, reject) => {
      service.getBookCountsByFormat(8).subscribe({next: resolve, error: reject});
    });
    await Promise.resolve();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries/8/format-counts'));
    expect(request.request.method).toBe('GET');
    request.flush({EPUB: 7, PDF: 2});

    await expect(resultPromise).resolves.toEqual({EPUB: 7, PDF: 2});

    service.setLargeLibraryLoading(true, 2500);
    expect(service.largeLibraryLoading()).toEqual({isLoading: true, expectedCount: 2500});

    bookService.books.mockReturnValue([
      {libraryId: 8, shelves: []},
      {libraryId: 8, shelves: []},
      {libraryId: 9, shelves: []},
    ]);
    expect(service.getBookCountValue(8)).toBe(2);
  });

  it('removes library queries when the auth token becomes null', () => {
    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/libraries')).flush([]);
    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: LIBRARIES_QUERY_KEY});
  });
});
