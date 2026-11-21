import {HttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query';
import {SseClient} from 'ngx-sse-client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {Book} from '../../book/model/book.model';
import {AUTHORS_QUERY_KEY} from './author-query-keys';
import {AuthorService} from './author.service';
import {bookQueryKeys} from '../../book/data/book-query-keys';

function makeBook(authors?: string[]): Book {
  return {
    id: 1,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {
      bookId: 1,
      title: 'Book 1',
      authors,
    },
  };
}

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
    vi.useRealTimers();
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

  it('deletes an author photo through its dedicated endpoint', () => {
    service.deleteAuthorPhoto(9);
    expect(httpClient.delete).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/v1/authors/9/photo`);
  });

  it('updates automatically when author metadata search has one unique result', () => {
    const candidate = {source: 'AUDNEXUS', asin: 'B00ADA', name: 'Ada Lovelace'};
    const updatedAuthor = {id: 9, name: 'Ada Lovelace', asin: 'B00ADA'};
    httpClient.get.mockReturnValueOnce(of([candidate]));
    httpClient.post.mockReturnValueOnce(of(updatedAuthor));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Ada Lovelace').subscribe(value => resolution = value);

    expect(httpClient.get).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`,
      {params: {q: 'Ada Lovelace'}},
    );
    expect(httpClient.post).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/match`,
      {source: 'AUDNEXUS', providerId: 'B00ADA', asin: 'B00ADA', region: undefined},
    );
    expect(resolution).toEqual({status: 'matched', author: updatedAuthor, candidate});
  });

  it('uses the provider ID to automatically match a BnF result without an ASIN', () => {
    const candidate = {
      source: 'BNF',
      providerId: 'http://data.bnf.fr/ark:/12148/cb123#about',
      isni: '000000012146438X',
      name: 'Ada Lovelace',
    };
    const updatedAuthor = {id: 9, name: 'Ada Lovelace', isni: '000000012146438X'};
    httpClient.get.mockReturnValueOnce(of([candidate]));
    httpClient.post.mockReturnValueOnce(of(updatedAuthor));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Ada Lovelace').subscribe(value => resolution = value);

    expect(httpClient.post).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/match`,
      {
        source: 'BNF',
        providerId: 'http://data.bnf.fr/ark:/12148/cb123#about',
        asin: undefined,
        region: undefined,
      },
    );
    expect(resolution).toEqual({status: 'matched', author: updatedAuthor, candidate});
  });

  it('uses an existing ASIN for direct lookup before falling back to name search', () => {
    const candidate = {source: 'AUDNEXUS', asin: 'B00ADA', name: 'Ada Lovelace'};
    const updatedAuthor = {id: 9, name: 'Ada Lovelace', asin: 'B00ADA'};
    httpClient.get.mockReturnValueOnce(of([candidate]));
    httpClient.post.mockReturnValueOnce(of(updatedAuthor));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Ada Lovelace', undefined, 'B00ADA').subscribe(value => resolution = value);

    expect(httpClient.get).toHaveBeenCalledWith(
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`,
      {params: {asin: 'B00ADA'}},
    );
    expect(httpClient.get).toHaveBeenCalledTimes(1);
    expect(resolution).toMatchObject({status: 'matched', candidate});
  });

  it('falls back to author-name search when an existing ASIN lookup returns no results', () => {
    const candidate = {source: 'AUDNEXUS', asin: 'B00ADA', name: 'Ada Lovelace'};
    httpClient.get.mockReturnValueOnce(of([])).mockReturnValueOnce(of([candidate]));
    httpClient.post.mockReturnValueOnce(of({id: 9, name: 'Ada Lovelace', asin: 'B00ADA'}));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Ada Lovelace', undefined, 'B00MISSING').subscribe(value => resolution = value);

    expect(httpClient.get).toHaveBeenNthCalledWith(
      1,
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`,
      {params: {asin: 'B00MISSING'}},
    );
    expect(httpClient.get).toHaveBeenNthCalledWith(
      2,
      `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`,
      {params: {q: 'Ada Lovelace'}},
    );
    expect(resolution).toMatchObject({status: 'matched', candidate});
  });

  it('requires manual validation and does not update when multiple unique candidates exist', () => {
    const candidates = [
      {source: 'AUDNEXUS', asin: 'B00ADA', name: 'Ada Lovelace'},
      {source: 'AUDNEXUS', asin: 'B00OTHER', name: 'A. Lovelace'},
    ];
    httpClient.get.mockReturnValueOnce(of(candidates));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Ada Lovelace').subscribe(value => resolution = value);

    expect(resolution).toEqual({status: 'ambiguous', candidates});
    expect(httpClient.post).not.toHaveBeenCalled();
  });

  it('deduplicates duplicate provider results by ASIN before deciding to apply automatically', () => {
    const candidate = {source: 'AUDNEXUS', asin: 'B00ADA', name: 'Ada Lovelace'};
    httpClient.get.mockReturnValueOnce(of([candidate, {...candidate, name: 'Augusta Ada King'}]));
    httpClient.post.mockReturnValueOnce(of({id: 9, name: 'Ada Lovelace', asin: 'B00ADA'}));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Ada Lovelace').subscribe(value => resolution = value);

    expect(resolution).toMatchObject({status: 'matched', candidate});
    expect(httpClient.post).toHaveBeenCalledOnce();
  });

  it('does not update metadata when no candidates are found', () => {
    httpClient.get.mockReturnValueOnce(of([]));

    let resolution: unknown;
    service.resolveAuthorMatch(9, 'Unknown Author').subscribe(value => resolution = value);

    expect(resolution).toEqual({status: 'not-found'});
    expect(httpClient.post).not.toHaveBeenCalled();
  });

  it('uses the configured server region by default and preserves custom regions', () => {
    service.quickMatchAuthor(11);
    service.quickMatchAuthor(11, 'gb');

    expect(httpClient.post).toHaveBeenNthCalledWith(
      1,
      `${API_CONFIG.BASE_URL}/api/v1/authors/11/quick-match`,
      null,
      {params: {}},
    );
    expect(httpClient.post).toHaveBeenNthCalledWith(
      2,
      `${API_CONFIG.BASE_URL}/api/v1/authors/11/quick-match`,
      null,
      {params: {region: 'gb'}},
    );
  });

  it('coalesces author query invalidation when newly created books introduce authors', () => {
    vi.useFakeTimers();

    service.handleNewlyCreatedBook(makeBook(['Ada Lovelace']));
    service.handleNewlyCreatedBook(makeBook(['Grace Hopper']));
    service.handleNewlyCreatedBook(makeBook(['Katherine Johnson']));

    expect(queryClient.invalidateQueries).not.toHaveBeenCalled();

    vi.advanceTimersByTime(199);
    expect(queryClient.invalidateQueries).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(1);
  });

  it('clears pending author invalidation when the service is destroyed', () => {
    vi.useFakeTimers();

    service.handleNewlyCreatedBook(makeBook(['Ada Lovelace']));
    TestBed.resetTestingModule();
    vi.advanceTimersByTime(200);

    expect(queryClient.invalidateQueries).not.toHaveBeenCalled();
  });

  it('does not invalidate authors when the newly created book has no authors', () => {
    service.handleNewlyCreatedBook(makeBook([]));
    service.handleNewlyCreatedBook(makeBook(undefined));

    expect(queryClient.invalidateQueries).not.toHaveBeenCalled();
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

  it('refreshes embedded book author data after an author rename', () => {
    service.updateAuthor(7, {name: 'Augusta Ada King'}).subscribe();

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['books'], exact: true});
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });

  it('refreshes book caches on any author update, not just renames', () => {
    service.updateAuthor(7, {description: 'Mathematician'}).subscribe();

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['books'], exact: true});
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });

  it('refreshes the author list and book caches after deleting authors', () => {
    service.deleteAuthors([7]).subscribe();

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['books'], exact: true});
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });

  it('deletes authors without books and refreshes the author list', () => {
    httpClient.delete.mockReturnValue(of(3));

    let deletedCount: number | undefined;
    service.deleteAuthorsWithoutBooks().subscribe(count => deletedCount = count);

    expect(httpClient.delete).toHaveBeenCalledWith(`${API_CONFIG.BASE_URL}/api/v1/authors/without-books`);
    expect(deletedCount).toBe(3);
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
    expect(queryClient.invalidateQueries).not.toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });

  it('builds tokenized author media urls with and without cache busters', () => {
    expect(service.getAuthorPhotoUrl(5)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/photo?token=token-123`);
    expect(service.getAuthorPhotoUrl(5, 99)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/photo?token=token-123&t=99`);
    expect(service.getAuthorThumbnailUrl(5)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/thumbnail?token=token-123`);
    expect(service.getAuthorThumbnailUrl(5, 99)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/thumbnail?token=token-123&t=99`);

    authService.getInternalAccessToken.mockReturnValue(null);
    expect(service.getAuthorPhotoUrl(5)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/photo`);
    expect(service.getAuthorPhotoUrl(5, 99)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/photo?t=99`);
    expect(service.getAuthorThumbnailUrl(5, 99)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/author/5/thumbnail?t=99`);
  });
});
