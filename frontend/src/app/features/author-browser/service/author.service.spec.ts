import {HttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {SseClient} from 'ngx-sse-client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {AuthorService} from './author.service';

describe('AuthorService', () => {
  const httpClient = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  };

  const authService = {
    token: vi.fn(),
    getInternalAccessToken: vi.fn(),
  };

  const sseClient = {
    stream: vi.fn(),
  };

  const queryClient = {
    removeQueries: vi.fn(),
    invalidateQueries: vi.fn(),
  };

  let service: AuthorService;

  beforeEach(() => {
    httpClient.get.mockReset();
    httpClient.post.mockReset();
    httpClient.put.mockReset();
    httpClient.delete.mockReset();
    authService.token.mockReset();
    authService.getInternalAccessToken.mockReset();
    sseClient.stream.mockReset();
    queryClient.removeQueries.mockReset();
    queryClient.invalidateQueries.mockReset();

    authService.token.mockReturnValue('token-123');
    authService.getInternalAccessToken.mockReturnValue('token-123');
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of(undefined));
    sseClient.stream.mockReturnValue(of({type: 'message', data: '{"id":7,"name":"Ada"}'}));

    TestBed.configureTestingModule({
      providers: [
        AuthorService,
        {provide: HttpClient, useValue: httpClient},
        {provide: AuthService, useValue: authService},
        {provide: SseClient, useValue: sseClient},
        {provide: QueryClient, useValue: queryClient},
      ],
    });

    service = TestBed.inject(AuthorService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('builds metadata search params with q or asin depending on the request', () => {
    service.searchAuthorMetadata(9, 'Ada', 'us');

    expect(httpClient.get).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`,
      {params: {region: 'us', q: 'Ada'}},
    );

    service.searchAuthorMetadata(9, 'Ada', 'us', 'B000123');

    expect(httpClient.get).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`,
      {params: {region: 'us', asin: 'B000123'}},
    );
  });

  it('uses the default region for quick matches and preserves custom regions', () => {
    service.quickMatchAuthor(11);
    service.quickMatchAuthor(11, 'gb');

    expect(httpClient.post).toHaveBeenNthCalledWith(
      1,
      `${API_CONFIG.BASE_URL}/api/v1/authors/11/quick-match`,
      null,
      {params: {region: 'us'}},
    );
    expect(httpClient.post).toHaveBeenNthCalledWith(
      2,
      `${API_CONFIG.BASE_URL}/api/v1/authors/11/quick-match`,
      null,
      {params: {region: 'gb'}},
    );
  });

  it('maps SSE auto-match responses and throws on error events', () => {
    sseClient.stream.mockReturnValueOnce(of({type: 'message', data: '{"id":3,"name":"Grace"}'}));

    const results: unknown[] = [];
    service.autoMatchAuthors([1, 2]).subscribe(value => results.push(value));

    expect(sseClient.stream).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/auto-match`,
      expect.objectContaining({keepAlive: false, reconnectionDelay: 1000, responseType: 'event'}),
      expect.objectContaining({
        body: [1, 2],
        withCredentials: true,
      }),
      'POST',
    );
    expect(results).toEqual([{id: 3, name: 'Grace'}]);

    sseClient.stream.mockReturnValueOnce(of({type: 'error', message: 'boom'}));

    const errors: Error[] = [];
    service.autoMatchAuthors([7]).subscribe({
      next: () => undefined,
      error: error => errors.push(error),
    });

    expect(errors).toHaveLength(1);
    expect(errors[0]).toEqual(new Error('boom'));
  });

  it('builds tokenized author media urls with and without cache busters', () => {
    expect(service.getAuthorPhotoUrl(5)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/photo?token=token-123`);
    expect(service.getAuthorThumbnailUrl(5)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/thumbnail?token=token-123`);
    expect(service.getAuthorThumbnailUrl(5, 99)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/thumbnail?token=token-123&t=99`);

    authService.getInternalAccessToken.mockReturnValue(null);
    expect(service.getAuthorPhotoUrl(5)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/photo`);
    expect(service.getAuthorThumbnailUrl(5, 99)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/thumbnail?t=99`);
  });
});
