import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {BookdropFileApiService} from './bookdrop-file-api.service';

describe('BookdropFileApiService', () => {
  let service: BookdropFileApiService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookdropFileApiService,
      ],
    });

    service = TestBed.inject(BookdropFileApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('requests the current bookdrop notification summary', () => {
    let result: unknown;
    service.getNotification().subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/bookdrop/notification')
    );
    expect(request.request.method).toBe('GET');
    request.flush({pendingCount: 3, totalCount: 8, lastUpdatedAt: '2026-03-26T00:00:00Z'});

    expect(result).toEqual({
      pendingCount: 3,
      totalCount: 8,
      lastUpdatedAt: '2026-03-26T00:00:00Z',
    });
  });
});
