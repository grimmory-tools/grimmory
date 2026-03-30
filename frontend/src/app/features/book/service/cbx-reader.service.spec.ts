import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {CbxPageInfo, CbxReaderService} from './cbx-reader.service';

describe('CbxReaderService', () => {
  let service: CbxReaderService;
  let httpTestingController: HttpTestingController;
  let authService: Pick<AuthService, 'getInternalAccessToken'>;

  beforeEach(() => {
    authService = {
      getInternalAccessToken: vi.fn(() => 'token-123'),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CbxReaderService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(CbxReaderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('requests available pages and appends the auth token', () => {
    const response = [1, 2, 3];

    let result: number[] | undefined;
    service.getAvailablePages(7).subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.urlWithParams.endsWith('/api/v1/cbx/7/pages?token=token-123'));
    expect(request.request.method).toBe('GET');
    request.flush(response);

    expect(result).toEqual(response);
  });

  it('includes book type alongside the auth token when requesting page info', () => {
    const response: CbxPageInfo[] = [{pageNumber: 1, displayName: 'Cover'}];

    let result: CbxPageInfo[] | undefined;
    service.getPageInfo(9, 'CBX').subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.urlWithParams.endsWith('/api/v1/cbx/9/page-info?bookType=CBX&token=token-123'));
    expect(request.request.method).toBe('GET');
    request.flush(response);

    expect(result).toEqual(response);
  });

  it('returns page image urls with both book type and token when provided', () => {
    const url = service.getPageImageUrl(12, 4, 'CBX');

    expect(url).toBe('http://localhost:6060/api/v1/media/book/12/cbx/pages/4?bookType=CBX&token=token-123');
  });

  it('returns unmodified urls when no auth token is available', () => {
    vi.mocked(authService.getInternalAccessToken).mockReturnValue(null);

    const url = service.getPageImageUrl(12, 4);

    expect(url).toBe('http://localhost:6060/api/v1/media/book/12/cbx/pages/4');
  });
});
