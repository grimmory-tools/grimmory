import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {computed, effect, inject, Service} from '@angular/core';
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
import type {MetadataProviderId} from '../../../shared/metadata/metadata-providers';
import {metadataSourceQueryKeys} from './metadata-source-query-keys';
import type {
  CoverImage,
  CoverSearchParams,
  MetadataSearchParams,
  MetadataSearchResult,
  MetadataReviewTask,
  MetadataSourceProvider,
} from './metadata-source-query.models';
import type {MetadataBatchProgressNotification} from '../../../shared/model/metadata-batch-progress.model';
import type {SidecarMetadata, SidecarSyncStatus} from './sidecar.models';

interface MetadataProviderState {
  readonly name: MetadataProviderId;
  readonly enabled: boolean;
}

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

@Service()
export class MetadataSourceQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly catalog = inject(MetadataCatalogService);
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly reviewTasksUrl = `${API_CONFIG.BASE_URL}/api/metadata/tasks`;

  private readonly providerList = injectQuery(() => queryOptions({
    queryKey: metadataSourceQueryKeys.providerList(),
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
    return this.queryClient.invalidateQueries({queryKey: metadataSourceQueryKeys.providerList()});
  }

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: metadataSourceQueryKeys.all()});
      }
    });
  }

  prospective(params: MetadataSearchParams) {
    const normalized = normalizeSearchParams(params);
    const body = JSON.stringify(normalized);

    return queryOptions({
      queryKey: metadataSourceQueryKeys.prospective(normalized),
      queryFn: experimental_streamedQuery({
        streamFn: ({signal}) => postSseJson<MetadataSearchResult>(this.authService, `${this.booksUrl}/${params.bookId}/metadata/prospective`, body, signal),
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
      queryKey: metadataSourceQueryKeys.covers(normalized),
      queryFn: experimental_streamedQuery({
        streamFn: ({signal}) => postSseJson<CoverImage>(this.authService, `${this.booksUrl}/${params.bookId}/metadata/covers`, body, signal),
        refetchMode: 'reset',
      }),
      ...STREAM_DEFAULTS,
    });
  }

  isbnLookup(isbn: string) {
    const trimmed = isbn.trim();

    return queryOptions({
      queryKey: metadataSourceQueryKeys.isbnLookup(trimmed),
      queryFn: async ({signal}): Promise<BookMetadata | null> => {
        try {
          return await toAbortablePromise(
            this.http.post<BookMetadata>(`${this.booksUrl}/metadata/isbn-lookup`, {isbn: trimmed}),
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
      queryKey: metadataSourceQueryKeys.fileMetadata(bookId),
      queryFn: ({signal}): Promise<BookMetadata> => httpGet<BookMetadata>(
        this.http,
        `${this.booksUrl}/${bookId}/file-metadata`,
        signal,
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  sidecar(bookId: number) {
    return queryOptions({
      queryKey: metadataSourceQueryKeys.sidecar(bookId),
      queryFn: ({signal}): Promise<SidecarMetadata> => httpGet<SidecarMetadata>(
        this.http,
        `${this.booksUrl}/${bookId}/sidecar`,
        signal,
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  sidecarStatus(bookId: number) {
    return queryOptions({
      queryKey: metadataSourceQueryKeys.sidecarStatus(bookId),
      queryFn: ({signal}): Promise<{status: SidecarSyncStatus}> => httpGet<{status: SidecarSyncStatus}>(
        this.http,
        `${this.booksUrl}/${bookId}/sidecar/status`,
        signal,
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  activeReviewTasks() {
    return queryOptions({
      queryKey: metadataSourceQueryKeys.activeReviewTasks(),
      queryFn: ({signal}) => httpGet<readonly MetadataBatchProgressNotification[]>(
        this.http,
        `${this.reviewTasksUrl}/active`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  reviewTask(taskId: string) {
    return queryOptions({
      queryKey: metadataSourceQueryKeys.reviewTask(taskId),
      queryFn: async ({signal}) => (await httpGet<{readonly task: MetadataReviewTask}>(
        this.http,
        `${this.reviewTasksUrl}/${taskId}`,
        signal,
      )).task,
      ...QUERY_DEFAULTS,
    });
  }
}
