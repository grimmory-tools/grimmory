export const LIBRARIES_QUERY_KEY = ['libraries'] as const;

export const libraryFormatCountsQueryKey = (libraryId: number) =>
  ['libraries', 'format-counts', libraryId] as const;
