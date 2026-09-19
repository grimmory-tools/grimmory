import type {AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {HttpTestingController} from '@angular/common/http/testing';
import {inject, Injectable, signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {createAuthServiceStub, createQueryClientHarness, flushQueryAsync, sseResponse, sseStream} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {
  metadataSourceKeys,
  type CoverSearchParams,
  type MetadataSearchParams,
  MetadataSourceQueryService,
} from './metadata-source-query.service';

const BOOKS_URL = `${API_CONFIG.BASE_URL}/api/v1/books`;
const PROVIDERS_URL = `${API_CONFIG.BASE_URL}/api/v1/metadata/providers`;

const SEARCH: MetadataSearchParams = {
  bookId: 7,
  providers: ['GoodReads', 'Amazon', 'GoodReads'],
  title: '  Dune  ',
  author: '',
  isbn: '   ',
};

interface Deferred {
  readonly promise: Promise<void>;
  readonly resolve: () => void;
}

function deferred(): Deferred {
  let resolve!: () => void;
  const promise = new Promise<void>(settle => { resolve = () => { settle(); }; });
  return {promise, resolve};
}

function event(title: string): string {
  return `data:{"bookId":7,"title":"${title}"}\n\n`;
}

function coverEvent(index: number): string {
  return `data:${JSON.stringify({url: `https://example.test/${index}.jpg`, width: index * 100, height: index * 150, index})}\n\n`;
}

@Injectable()
class ProspectiveHost {
  private readonly metadata = inject(MetadataSourceQueryService);
  readonly query = injectQuery(() => this.metadata.prospective(SEARCH));
}

const COVERS: CoverSearchParams = {
  bookId: 7,
  title: '  Dune  ',
  author: '  Frank Herbert  ',
  coverType: 'ebook',
};

@Injectable()
class CoversHost {
  private readonly metadata = inject(MetadataSourceQueryService);
  readonly query = injectQuery(() => this.metadata.coverSearch(COVERS));
}

describe('MetadataSourceQueryService', () => {
  const appSettings = signal<{
    metadataProviderSettings: Partial<Record<keyof AppSettings['metadataProviderSettings'], {enabled: boolean; apiKey?: string} | null>>;
  } | null>(null);
  let service: MetadataSourceQueryService;
  let queryClient: QueryClient;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let http: HttpTestingController;

  beforeEach(() => {
    appSettings.set(null);
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    queryClient.setDefaultOptions({queries: {retry: false}});
    authService = createAuthServiceStub();

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: {appSettings}},
        MetadataSourceQueryService,
        ProspectiveHost,
        CoversHost,
      ],
    });

    service = TestBed.inject(MetadataSourceQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(PROVIDERS_URL)
      .filter(request => !request.cancelled)
      .forEach(request => request.flush([]));
    http.verify();
    vi.restoreAllMocks();
  });

  async function answerProviders(entries: readonly {name: string; enabled: boolean}[]): Promise<void> {
    await flushQueryAsync();
    http.match(PROVIDERS_URL).forEach(request => request.flush(entries));
    await flushQueryAsync();
  }

  it('takes the enabled flag from the backend, in catalogue order', async () => {
    expect(service.enabledProviders()).toEqual([]);

    await answerProviders([
      {name: 'Amazon', enabled: true},
      {name: 'GoodReads', enabled: false},
      {name: 'OpenLibrary', enabled: true},
    ]);

    expect(service.enabledProviders().map(provider => provider.id)).toEqual(['OpenLibrary', 'Amazon']);
  });

  it('ignores a provider the catalogue lacks and treats an omitted one as disabled', async () => {
    await answerProviders([
      {name: 'Amazon', enabled: true},
      {name: 'Bookwyrm', enabled: true},
    ]);

    expect(service.providers()).toHaveLength(11);
    expect(service.enabledProviders().map(provider => provider.id)).toEqual(['Amazon']);
  });

  it('removes its cached metadata queries when the authenticated session ends', () => {
    queryClient.setQueryData(metadataSourceKeys.fileMetadata(7), {bookId: 7});

    authService.token.set(null);
    TestBed.flushEffects();

    expect(queryClient.getQueryData(metadataSourceKeys.fileMetadata(7))).toBeUndefined();
  });

  it('looks an isbn up with a POST carrying the trimmed isbn', async () => {
    const result = queryClient.fetchQuery(service.isbnLookup(' 9780441013593 '));

    const request = http.expectOne(`${BOOKS_URL}/metadata/isbn-lookup`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({isbn: '9780441013593'});
    request.flush({bookId: 7, title: 'Dune'});

    await expect(result).resolves.toEqual({bookId: 7, title: 'Dune'});
  });

  it('treats an ISBN lookup 404 as no match', async () => {
    const result = queryClient.fetchQuery(service.isbnLookup('9780441013593'));
    http.expectOne(`${BOOKS_URL}/metadata/isbn-lookup`).flush(null, {status: 404, statusText: 'Not Found'});

    await expect(result).resolves.toBeNull();
  });

  it('keeps other ISBN lookup failures as errors', async () => {
    const result = queryClient.fetchQuery({...service.isbnLookup('9780441013593'), retry: false});
    http.expectOne(`${BOOKS_URL}/metadata/isbn-lookup`).flush(null, {status: 503, statusText: 'Unavailable'});

    await expect(result).rejects.toMatchObject({status: 503});
  });

  describe('prospective', () => {
    it('posts the normalised search', async () => {
      const fetchStub = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        sseResponse(sseStream([event('One')])),
      );

      TestBed.inject(ProspectiveHost);
      await flushQueryAsync();

      expect(fetchStub).toHaveBeenCalledOnce();
      expect(service.prospective(SEARCH).queryKey).toEqual(
        service.prospective({bookId: 7, providers: ['Amazon', 'GoodReads'], title: 'Dune'}).queryKey,
      );
      const [url, init] = fetchStub.mock.calls[0];
      expect(url).toBe(`${BOOKS_URL}/7/metadata/prospective`);
      expect(init?.method).toBe('POST');
      expect(init?.body).toBe(JSON.stringify({
        bookId: 7,
        providers: ['Amazon', 'GoodReads'],
        title: 'Dune',
        author: '',
        isbn: '',
      }));
    });

    it('grows the result list as the stream delivers events', async () => {
      const encoder = new TextEncoder();
      const secondEvent = deferred();
      const endOfStream = deferred();

      const stream = new ReadableStream<Uint8Array>({
        async start(controller) {
          controller.enqueue(encoder.encode(event('One')));
          await secondEvent.promise;
          controller.enqueue(encoder.encode(event('Two')));
          await endOfStream.promise;
          controller.close();
        },
      });

      vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(stream));

      const host = TestBed.inject(ProspectiveHost);
      await flushQueryAsync();

      expect(host.query.data()).toEqual([{bookId: 7, title: 'One'}]);
      expect(host.query.isFetching()).toBe(true);

      secondEvent.resolve();
      await flushQueryAsync();

      expect(host.query.data()).toEqual([{bookId: 7, title: 'One'}, {bookId: 7, title: 'Two'}]);
      expect(host.query.isFetching()).toBe(true);

      endOfStream.resolve();
      await flushQueryAsync();

      expect(host.query.isFetching()).toBe(false);
    });

    it('fails the query with the http status when the stream request is rejected', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 503}));

      const host = TestBed.inject(ProspectiveHost);
      await flushQueryAsync();

      expect(host.query.isError()).toBe(true);
      expect(host.query.error()).toMatchObject({status: 503});
    });

  });

  describe('covers', () => {
    it('posts the normalised cover search', async () => {
      const fetchStub = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        sseResponse(sseStream([coverEvent(1)])),
      );

      TestBed.inject(CoversHost);
      await flushQueryAsync();

      expect(fetchStub).toHaveBeenCalledOnce();
      const [url, init] = fetchStub.mock.calls[0];
      expect(url).toBe(`${BOOKS_URL}/7/metadata/covers`);
      expect(init?.method).toBe('POST');
      expect(init?.body).toBe(JSON.stringify({
        title: 'Dune',
        author: 'Frank Herbert',
        coverType: 'ebook',
      }));
    });

  });
});
