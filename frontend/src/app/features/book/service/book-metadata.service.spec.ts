import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {BookMetadataService} from './book-metadata.service';
import {SseClient} from 'ngx-sse-client';

describe('BookMetadataService', () => {
  let service: BookMetadataService;
  let httpTestingController: HttpTestingController;
  let authService: {getInternalAccessToken: ReturnType<typeof vi.fn>};
  let sseClient: {stream: ReturnType<typeof vi.fn>};
  const fetchRequest = {
    bookId: 7,
    providers: ['google'],
    title: 'Dune',
    author: 'Frank Herbert',
    isbn: '9780441172719',
  };

  beforeEach(() => {
    authService = {
      getInternalAccessToken: vi.fn(() => 'token-123'),
    };
    sseClient = {
      stream: vi.fn(() => of({type: 'message', data: JSON.stringify({title: 'Dune'})})),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookMetadataService,
        {provide: AuthService, useValue: authService},
        {provide: SseClient, useValue: sseClient},
      ],
    });

    service = TestBed.inject(BookMetadataService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('throws when fetching metadata without an auth token', () => {
    authService.getInternalAccessToken.mockReturnValueOnce(null);

    expect(() => service.fetchBookMetadata(7, fetchRequest)).toThrowError(
      'No authentication token available'
    );
  });

  it('streams metadata over SSE with auth headers and maps message payloads', () => {
    let result: unknown;

    service.fetchBookMetadata(7, fetchRequest).subscribe(value => {
      result = value;
    });

    const [url, clientConfig, requestOptions, method] = sseClient.stream.mock.calls[0] ?? [];
    expect(url).toMatch(/\/api\/v1\/books\/7\/metadata\/prospective$/);
    expect(clientConfig).toEqual({
      keepAlive: false,
      reconnectionDelay: 1000,
      responseType: 'event'
    });
    expect(requestOptions.body).toEqual(fetchRequest);
    expect(requestOptions.withCredentials).toBe(true);
    expect(requestOptions.headers.get('Authorization')).toBe('Bearer token-123');
    expect(requestOptions.headers.get('Content-Type')).toBe('application/json');
    expect(method).toBe('POST');
    expect(result).toEqual({title: 'Dune'});
  });

  it('turns SSE error events into thrown errors', () => {
    sseClient.stream.mockReturnValueOnce(of({type: 'error', message: 'bad stream'}));

    let error: unknown;
    service.fetchBookMetadata(7, fetchRequest).subscribe({
      error: err => {
        error = err;
      }
    });

    expect(error).toBeInstanceOf(Error);
    expect((error as Error).message).toBe('bad stream');
  });

  it('requests provider detail and ISBN lookup through HTTP endpoints', () => {
    service.fetchMetadataDetail('google', 'abc123').subscribe();
    service.lookupByIsbn('9780441172719').subscribe();

    const detailRequest = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/books/metadata/detail/google/abc123')
    );
    expect(detailRequest.request.method).toBe('GET');
    detailRequest.flush({});

    const lookupRequest = httpTestingController.expectOne(req =>
      req.url.endsWith('/api/v1/books/metadata/isbn-lookup')
    );
    expect(lookupRequest.request.method).toBe('POST');
    expect(lookupRequest.request.body).toEqual({isbn: '9780441172719'});
    lookupRequest.flush({});
  });
});
