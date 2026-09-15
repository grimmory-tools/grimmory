import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {bookQueryKeys} from '../../book/data/book-query-keys';
import {AUTHORS_QUERY_KEY} from '../../author-browser/service/author-query-keys';
import {SidecarService} from './sidecar.service';

describe('SidecarService', () => {
  let service: SidecarService;
  let httpTestingController: HttpTestingController;
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SidecarService,
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    service = TestBed.inject(SidecarService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClient.clear();
    TestBed.resetTestingModule();
  });

  it('fetches sidecar content for a book', () => {
    service.getSidecarContent(42).subscribe(response => {
      expect(response.metadata.title).toBe('Book');
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42/sidecar`);
    expect(request.request.method).toBe('GET');
    request.flush({
      version: '1',
      generatedAt: '2026-03-26T00:00:00Z',
      generatedBy: 'test',
      metadata: {title: 'Book'},
    });
  });

  it('fetches the sidecar sync status', () => {
    service.getSyncStatus(42).subscribe(response => {
      expect(response).toEqual({status: 'CONFLICT'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42/sidecar/status`);
    expect(request.request.method).toBe('GET');
    request.flush({status: 'CONFLICT'});
  });

  it('exports a sidecar for a book', () => {
    service.exportToSidecar(42).subscribe(response => {
      expect(response).toEqual({message: 'ok'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42/sidecar/export`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({message: 'ok'});
  });

  it('imports a sidecar for a book', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    service.importFromSidecar(42).subscribe(response => {
      expect(response).toEqual({message: 'done'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42/sidecar/import`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({message: 'done'});

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['books', 'detail', 42]});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookQueryKeys.collections()});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
  });

  it('bulk exports sidecars for a library', () => {
    service.bulkExport(7).subscribe(response => {
      expect(response).toEqual({message: 'exported', exported: 3});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/libraries/7/sidecar/export-all`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({message: 'exported', exported: 3});
  });

  it('bulk imports sidecars for a library', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    service.bulkImport(7).subscribe(response => {
      expect(response).toEqual({message: 'imported', imported: 2});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/libraries/7/sidecar/import-all`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({message: 'imported', imported: 2});

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
  });
});
