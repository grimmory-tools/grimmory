import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {BookdropService} from './bookdrop.service';

describe('BookdropService', () => {
  let service: BookdropService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookdropService,
      ],
    });

    service = TestBed.inject(BookdropService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('requests pending files with default pagination', () => {
    service.getPendingFiles().subscribe();

    const request = httpTestingController.expectOne(
      'http://localhost:6060/api/v1/bookdrop/files?status=pending&page=0&size=50'
    );
    expect(request.request.method).toBe('GET');
    request.flush({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 50}});
  });

  it('posts finalize, discard, rescan, extract, and bulk-edit requests to the expected endpoints', () => {
    const finalizePayload = {selectAll: true};
    const discardPayload = {selectAll: false, selectedIds: [1, 2]};
    const extractPayload = {pattern: '{author}', preview: true};
    const bulkEditPayload = {
      fields: {title: 'Dune'},
      enabledFields: ['title'],
      mergeArrays: false,
    };

    service.finalizeImport(finalizePayload).subscribe();
    service.discardFiles(discardPayload).subscribe();
    service.rescan().subscribe();
    service.extractFromPattern(extractPayload).subscribe();
    service.bulkEditMetadata(bulkEditPayload).subscribe();

    const finalizeRequest = httpTestingController.expectOne(req => req.url.includes('/api/v1/bookdrop/imports/finalize'));
    expect(finalizeRequest.request.body).toEqual(finalizePayload);
    finalizeRequest.flush({});

    const discardRequest = httpTestingController.expectOne(req => req.url.includes('/api/v1/bookdrop/files/discard'));
    expect(discardRequest.request.body).toEqual(discardPayload);
    discardRequest.flush(null);

    const rescanRequest = httpTestingController.expectOne(req => req.url.includes('/api/v1/bookdrop/rescan'));
    expect(rescanRequest.request.body).toEqual({});
    rescanRequest.flush(null);

    const extractRequest = httpTestingController.expectOne(req => req.url.includes('/api/v1/bookdrop/files/extract-pattern'));
    expect(extractRequest.request.body).toEqual(extractPayload);
    extractRequest.flush({});

    const bulkEditRequest = httpTestingController.expectOne(req => req.url.includes('/api/v1/bookdrop/files/bulk-edit'));
    expect(bulkEditRequest.request.body).toEqual(bulkEditPayload);
    bulkEditRequest.flush({});
  });
});
