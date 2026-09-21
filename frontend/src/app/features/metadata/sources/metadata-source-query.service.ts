import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {computed, effect, inject, Injectable} from '@angular/core';
import {
  experimental_streamedQuery,
  injectQuery,
  queryOptions,
  QueryClient,
} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {httpGet, postSseJson, QUERY_DEFAULTS, toAbortablePromise} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import type {BookMetadata} from '../../book/model/book.model';
import {MetadataCatalogService} from '../../../shared/metadata/metadata-catalog.service';
import type {MetadataProviderDescriptor, MetadataProviderId} from '../../../shared/metadata/metadata-providers';
import type {SidecarMetadata, SidecarSyncStatus} from './sidecar.models';

interface MetadataProviderState {
  readonly name: MetadataProviderId;
  readonly enabled: boolean;
}

export interface MetadataSourceProvider extends MetadataProviderDescriptor {
  readonly enabled: boolean;
}

export interface MetadataSearchParams {
  readonly bookId: number;
  readonly providers: readonly MetadataProviderId[];
  readonly title?: string;
  readonly author?: string;
  readonly isbn?: string;
}

export type MetadataSearchResult = BookMetadata & {readonly provider: MetadataProviderId};

export interface CoverImage {
  url: string;
  width: number;
  height: number;
  index: number;
}

export interface CoverSearchParams {
  readonly bookId: number;
  readonly title?: string;
  readonly author?: string;
  readonly coverType: 'ebook' | 'audiobook';
}

export const metadataSourceKeys = {
  all: () => ['metadata-sources'] as const,
  providerList: () => [...metadataSourceKeys.all(), 'providers'] as const,
  prospective: (params: MetadataSearchParams) =>
    [...metadataSourceKeys.all(), 'prospective', params] as const,
  covers: (params: CoverSearchParams) =>
    [...metadataSourceKeys.all(), 'covers', params] as const,
  isbnLookup: (isbn: string) => [...metadataSourceKeys.all(), 'isbn', isbn] as const,
  fileMetadata: (bookId: number) => [...metadataSourceKeys.all(), 'file', bookId] as const,
  sidecar: (bookId: number) => [...metadataSourceKeys.all(), 'sidecar', bookId] as const,
  sidecarStatus: (bookId: number) => [...metadataSourceKeys.sidecar(bookId), 'status'] as const,
};

const STREAM_DEFAULTS = {
  refetchOnWindowFocus: false,
  retry: false,
} as const;

function normalizeSearchParams(params: MetadataSearchParams) {
  return {
    bookId: params.bookId,
    providers: [...new Set(params.providers)].sort((a, b) => a.localeCompare(b)),
    title: params.title?.trim() ?? '',
    author: params.author?.trim() ?? '',
    isbn: params.isbn?.trim() ?? '',
  };
}

function normalizeCoverSearchParams(params: CoverSearchParams): CoverSearchParams {
  return {
    bookId: params.bookId,
    title: params.title?.trim() || undefined,
    author: params.author?.trim() || undefined,
    coverType: params.coverType,
  };
}

@Injectable({providedIn: 'root'})
export class MetadataSourceQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly catalog = inject(MetadataCatalogService);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private readonly providerList = injectQuery(() => queryOptions({
    queryKey: metadataSourceKeys.providerList(),
    queryFn: ({signal}) => httpGet<readonly MetadataProviderState[]>(
      this.http,
      `${API_CONFIG.BASE_URL}/api/v1/metadata/providers`,
      signal,
    ),
    ...QUERY_DEFAULTS,
    staleTime: Infinity,
  }));

  readonly providersLoading = computed(() => this.providerList.isPending());

  readonly providers = computed<readonly MetadataSourceProvider[]>(() => {
    const enabled = new Map((this.providerList.data() ?? []).map(entry => [entry.name, entry.enabled]));
    return this.catalog.providers().map(provider => ({
      ...provider,
      enabled: enabled.get(provider.id) ?? false,
    }));
  });
  readonly enabledProviders = computed(() => this.providers().filter(provider => provider.enabled));

  refreshProviders(): Promise<void> {
    return this.queryClient.invalidateQueries({queryKey: metadataSourceKeys.providerList()});
  }

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: metadataSourceKeys.all()});
      }
    });
  }

  prospective(params: MetadataSearchParams) {
    const normalized = normalizeSearchParams(params);
    const body = JSON.stringify(normalized);

    return queryOptions({
      queryKey: metadataSourceKeys.prospective(normalized),
      queryFn: experimental_streamedQuery({
        streamFn: ({signal}) => postSseJson<MetadataSearchResult>(this.authService, `${this.baseUrl}/${params.bookId}/metadata/prospective`, body, signal),
        refetchMode: 'reset',
      }),
      ...STREAM_DEFAULTS,
    });
  }

  coverSearch(params: CoverSearchParams) {
    const normalized = normalizeCoverSearchParams(params);
    const {title, author, coverType} = normalized;
    const body = JSON.stringify({title, author, coverType});

    return queryOptions({
      queryKey: metadataSourceKeys.covers(normalized),
      queryFn: experimental_streamedQuery({
        streamFn: ({signal}) => postSseJson<CoverImage>(this.authService, `${this.baseUrl}/${params.bookId}/metadata/covers`, body, signal),
        refetchMode: 'reset',
      }),
      ...STREAM_DEFAULTS,
    });
  }

  isbnLookup(isbn: string) {
    const trimmed = isbn.trim();

    return queryOptions({
      queryKey: metadataSourceKeys.isbnLookup(trimmed),
      queryFn: async ({signal}): Promise<BookMetadata | null> => {
        try {
          return await toAbortablePromise(
            this.http.post<BookMetadata>(`${this.baseUrl}/metadata/isbn-lookup`, {isbn: trimmed}),
            signal,
          );
        } catch (error) {
          if (error instanceof HttpErrorResponse && error.status === 404) return null;
          throw error;
        }
      },
      ...QUERY_DEFAULTS,
    });
  }

  fileMetadata(bookId: number) {
    return queryOptions({
      queryKey: metadataSourceKeys.fileMetadata(bookId),
      queryFn: ({signal}): Promise<BookMetadata> => httpGet<BookMetadata>(
        this.http,
        `${this.baseUrl}/${bookId}/file-metadata`,
        signal,
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  sidecar(bookId: number) {
    return queryOptions({
      queryKey: metadataSourceKeys.sidecar(bookId),
      queryFn: ({signal}): Promise<SidecarMetadata> => httpGet<SidecarMetadata>(
        this.http,
        `${this.baseUrl}/${bookId}/sidecar`,
        signal,
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  sidecarStatus(bookId: number) {
    return queryOptions({
      queryKey: metadataSourceKeys.sidecarStatus(bookId),
      queryFn: ({signal}): Promise<{status: SidecarSyncStatus}> => httpGet<{status: SidecarSyncStatus}>(
        this.http,
        `${this.baseUrl}/${bookId}/sidecar/status`,
        signal,
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }
}
