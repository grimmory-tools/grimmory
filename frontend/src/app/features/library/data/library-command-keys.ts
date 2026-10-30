export const libraryCommandKeys = {
  all: () => ['libraries', 'command'] as const,
  createLibrary: () => [...libraryCommandKeys.all(), 'create'] as const,
  updateLibrary: () => [...libraryCommandKeys.all(), 'update'] as const,
  deleteLibrary: () => [...libraryCommandKeys.all(), 'delete'] as const,
  rescanLibrary: () => [...libraryCommandKeys.all(), 'rescan'] as const,
  setFileNamingPattern: () => [...libraryCommandKeys.all(), 'file-naming-pattern'] as const,
  scanLibraryPaths: () => [...libraryCommandKeys.all(), 'scan-paths'] as const,
};
