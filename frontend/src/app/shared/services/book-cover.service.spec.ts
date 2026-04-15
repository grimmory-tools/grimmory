import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookCoverService, CoverFetchRequest, CoverImage} from './book-cover.service';
import {AuthService} from '../service/auth.service';

describe('BookCoverService', () => {
  let service: BookCoverService;
  let authService: {getInternalAccessToken: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    authService = {
      getInternalAccessToken: vi.fn(() => 'token-123'),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookCoverService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(BookCoverService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('streams covers over SSE with auth headers', async () => {
    const requestBody: CoverFetchRequest = {
      bookId: 123,
      title: 'Dune',
      author: 'Frank Herbert',
      coverType: 'ebook',
    };
    const mockImage = {url: 'https://example.test/cover-1.jpg', index: 1};
    const encoder = new TextEncoder();
    const dataChunk = encoder.encode(`data: ${JSON.stringify(mockImage)}\n`);

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

    let result: CoverImage | undefined;
    service.fetchBookCovers(requestBody).subscribe(value => {
      result = value;
    });

    // Wait for the stream to process
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/books\/123\/metadata\/covers$/),
      expect.objectContaining({
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer token-123'
        },
        body: JSON.stringify(requestBody)
      })
    );
    expect(result).toEqual(mockImage);
  });
});
