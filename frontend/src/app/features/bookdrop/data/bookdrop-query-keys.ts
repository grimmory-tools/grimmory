import type {BookdropFilesParams} from './bookdrop-query.models';

export const bookdropQueryKeys = {
  all: () => ['bookdrop', 'query'] as const,
  summary: () => [...bookdropQueryKeys.all(), 'summary'] as const,
  pendingFiles: (params: BookdropFilesParams) => [...bookdropQueryKeys.all(), 'pending-files', params] as const,
};
