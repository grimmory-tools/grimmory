import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../../core/config/api-config';
import {AuthService} from '../../../../shared/service/auth.service';
import {EpubStreamingService} from './epub-streaming.service';

describe('EpubStreamingService', () => {
  let service: EpubStreamingService;
  let httpTestingController: HttpTestingController;
  const authService = {
    getInternalAccessToken: vi.fn(() => 'internal-token'),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EpubStreamingService,
        {provide: AuthService, useValue: authService},
      ]
    });

    service = TestBed.inject(EpubStreamingService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('returns the epub api base url and auth token', () => {
    expect(service.getBaseUrl()).toBe(`${API_CONFIG.BASE_URL}/api/v1/epub`);
    expect(service.getAuthToken()).toBe('internal-token');
  });

  it('loads book info without a book type query parameter', () => {
    service.getBookInfo(42).subscribe(info => {
      expect(info).toEqual({containerPath: 'container', rootPath: 'root', spine: [], manifest: [], toc: {label: 'toc', href: 'toc'}, metadata: {}});
    });

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/epub/42/info`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({containerPath: 'container', rootPath: 'root', spine: [], manifest: [], toc: {label: 'toc', href: 'toc'}, metadata: {}});
  });

  it('loads book info with a book type query parameter', () => {
    service.getBookInfo(7, 'EPUB').subscribe(info => {
      expect(info.rootPath).toBe('ops');
    });

    const request = httpTestingController.expectOne(req =>
      req.url === `${API_CONFIG.BASE_URL}/api/v1/epub/7/info` &&
      req.params.get('bookType') === 'EPUB'
    );
    expect(request.request.method).toBe('GET');
    request.flush({containerPath: 'container', rootPath: 'ops', spine: [], manifest: [], toc: {label: 'toc', href: 'toc'}, metadata: {}});
  });
});
