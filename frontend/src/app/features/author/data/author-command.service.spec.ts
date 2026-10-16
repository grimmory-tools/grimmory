import {HttpTestingController} from '@angular/common/http/testing';
import {inject, Injectable} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {injectMutation, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  createAuthServiceStub,
  createQueryClientHarness,
  flushSignalAndQueryEffects,
  sseResponse,
  sseStream,
} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {BOOKS_QUERY_KEY} from '../../book/service/book-query-keys';
import {AuthorCommandService} from './author-command.service';
import {authorQueryKeys} from './author-query-keys';

const AUTHORS_URL = `${API_CONFIG.BASE_URL}/api/v1/authors`;

@Injectable()
class AuthorCommandHost {
  private readonly commands = inject(AuthorCommandService);
  readonly deleteAuthors = injectMutation(() => this.commands.deleteAuthors());
  readonly autoMatch = injectMutation(() => this.commands.autoMatch());
  readonly update = injectMutation(() => this.commands.update());
}

describe('AuthorCommandService', () => {
  let host: AuthorCommandHost;
  let queryClient: QueryClient;
  let http: HttpTestingController;
  const authorIdsKey = authorQueryKeys.ids({facets: {}, sort: []});

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: createAuthServiceStub()},
        AuthorCommandHost,
      ],
    });
    host = TestBed.inject(AuthorCommandHost);
    http = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    http.verify();
    queryClient.clear();
    vi.unstubAllGlobals();
  });

  it('deletes the authors and refreshes book and author data', async () => {
    queryClient.setQueryData(BOOKS_QUERY_KEY, []);
    queryClient.setQueryData(authorIdsKey, [1, 2]);
    queryClient.setQueryData(authorQueryKeys.detail(1), {id: 1});

    const result = host.deleteAuthors.mutateAsync({authorIds: [1, 2]});
    await Promise.resolve();
    flushSignalAndQueryEffects();

    const request = http.expectOne(AUTHORS_URL);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual([1, 2]);
    request.flush(null);

    await result;
    expect(queryClient.getQueryState(BOOKS_QUERY_KEY)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(authorIdsKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryData(authorQueryKeys.detail(1))).toBeUndefined();
  });

  it('auto-matches the authors from the stream and refreshes the author lists', async () => {
    const fetchMock = vi.fn().mockResolvedValue(sseResponse(sseStream([
      'data:{"id":1,"name":"Ada","asin":"B1","bookCount":0,"photoUpdatedOn":"2026-01-02T03:04:05Z"}\n\n',
      'data:{"id":3,"name":"Grace","asin":"B3","bookCount":0}\n\n',
    ])));
    vi.stubGlobal('fetch', fetchMock);
    queryClient.setQueryData(authorIdsKey, [1, 2, 3]);

    const matched = await host.autoMatch.mutateAsync({authorIds: [1, 2, 3]});

    expect(fetchMock).toHaveBeenCalledWith(`${AUTHORS_URL}/auto-match`, expect.objectContaining({
      method: 'POST',
      body: '[1,2,3]',
    }));
    expect(matched.map(author => author.id)).toEqual([1, 3]);
    expect(queryClient.getQueryState(authorIdsKey)?.isInvalidated).toBe(true);
  });

  it('refreshes book data and author details after an author update', async () => {
    const detailKey = authorQueryKeys.detail(7);
    queryClient.setQueryData(BOOKS_QUERY_KEY, []);
    queryClient.setQueryData(detailKey, {id: 7});

    const result = host.update.mutateAsync({authorId: 7, request: {description: 'Mathematician'}});
    await Promise.resolve();
    flushSignalAndQueryEffects();
    http.expectOne(`${AUTHORS_URL}/7`).flush({id: 7});

    await result;
    expect(queryClient.getQueryState(BOOKS_QUERY_KEY)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(detailKey)?.isInvalidated).toBe(true);
  });
});
