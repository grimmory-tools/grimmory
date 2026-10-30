import type {CoverSearchParams, MetadataSearchParams} from './metadata-source-query.models';

export const metadataSourceQueryKeys = {
  all: () => ['metadata-sources', 'query'] as const,
  providerList: () => [...metadataSourceQueryKeys.all(), 'providers'] as const,
  prospective: (params: MetadataSearchParams) =>
    [...metadataSourceQueryKeys.all(), 'prospective', params] as const,
  covers: (params: CoverSearchParams) =>
    [...metadataSourceQueryKeys.all(), 'covers', params] as const,
  isbnLookup: (isbn: string) => [...metadataSourceQueryKeys.all(), 'isbn', isbn] as const,
  fileMetadata: (bookId: number) => [...metadataSourceQueryKeys.all(), 'file', bookId] as const,
  sidecars: () => [...metadataSourceQueryKeys.all(), 'sidecar'] as const,
  sidecarQueries: (bookId: number) => [...metadataSourceQueryKeys.sidecars(), bookId] as const,
  sidecar: (bookId: number) => [...metadataSourceQueryKeys.sidecarQueries(bookId), 'metadata'] as const,
  sidecarStatus: (bookId: number) => [...metadataSourceQueryKeys.sidecarQueries(bookId), 'status'] as const,
  reviewTaskQueries: () => [...metadataSourceQueryKeys.all(), 'review-tasks'] as const,
  activeReviewTasks: () => [...metadataSourceQueryKeys.reviewTaskQueries(), 'active'] as const,
  reviewTask: (taskId: string) => [...metadataSourceQueryKeys.reviewTaskQueries(), 'task', taskId] as const,
};
