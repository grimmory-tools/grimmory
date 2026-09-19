import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {SidecarService} from './sidecar.service';

describe('SidecarService', () => {
  let service: SidecarService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SidecarService,
      ]
    });

    service = TestBed.inject(SidecarService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
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
    service.importFromSidecar(42).subscribe(response => {
      expect(response).toEqual({message: 'done'});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42/sidecar/import`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({message: 'done'});
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
    service.bulkImport(7).subscribe(response => {
      expect(response).toEqual({message: 'imported', imported: 2});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/libraries/7/sidecar/import-all`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({message: 'imported', imported: 2});
  });
});
