import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {BookMetadataService} from './book-metadata.service';

describe('BookMetadataService', () => {
  let service: BookMetadataService;
  let httpTestingController: HttpTestingController;
  let authService: {getInternalAccessToken: ReturnType<typeof vi.fn>};
  
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

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookMetadataService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(BookMetadataService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('throws when fetching metadata without an auth token', () => {
    authService.getInternalAccessToken.mockReturnValueOnce(null);

    expect(() => service.fetchBookMetadata(7, fetchRequest)).toThrowError(
      'No authentication token available'
    );
  });

  it('streams metadata over SSE with auth headers and maps message payloads', async () => {
    const mockMetadata = {title: 'Dune'};
    const encoder = new TextEncoder();
    const dataChunk = encoder.encode(`data: ${JSON.stringify(mockMetadata)}\n`);
    
    // Mocking reader instead of using global ReadableStream which might be missing in jsdom
    const mockReader = {
      read: vi.fn()
        .mockResolvedValueOnce({done: false, value: dataChunk})
        .mockResolvedValueOnce({done: true, value: undefined}),
      releaseLock: vi.fn()
    };

    const mockResponse = {
      ok: true,
      body: {
        getReader: () => mockReader
      }
    };

    const fetchSpy = vi.fn().mockResolvedValue(mockResponse);
    vi.stubGlobal('fetch', fetchSpy);

    let result: unknown;
    service.fetchBookMetadata(7, fetchRequest).subscribe(value => {
      result = value;
    });

    // Wait for the stream to process
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/books\/7\/metadata\/prospective$/),
      expect.objectContaining({
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer token-123'
        },
        body: JSON.stringify(fetchRequest)
      })
    );
    expect(result).toEqual(mockMetadata);
  });

  it('turns SSE error events into thrown errors', async () => {
    const mockResponse = {
      ok: false,
      status: 500
    };

    const fetchSpy = vi.fn().mockResolvedValue(mockResponse);
    vi.stubGlobal('fetch', fetchSpy);

    let error: Error | undefined;
    service.fetchBookMetadata(7, fetchRequest).subscribe({
      error: err => {
        error = err;
      }
    });

    // Wait for the fetch to resolve
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(error).toBeInstanceOf(Error);
    expect(error?.message).toContain('HTTP error! status: 500');
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
