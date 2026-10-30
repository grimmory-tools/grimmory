export const libraryQueryKeys = {
  all: () => ['libraries', 'query'] as const,
  libraries: () => [...libraryQueryKeys.all(), 'list'] as const,
  formatCounts: (libraryId: number) => [...libraryQueryKeys.all(), 'format-counts', libraryId] as const,
  health: () => [...libraryQueryKeys.all(), 'health'] as const,
  folders: (path: string) => [...libraryQueryKeys.all(), 'folders', path] as const,
};
