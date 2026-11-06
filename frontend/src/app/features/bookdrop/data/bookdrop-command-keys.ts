export const bookdropCommandKeys = {
  all: () => ['bookdrop', 'command'] as const,
  uploadFile: () => [...bookdropCommandKeys.all(), 'upload'] as const,
  finalizeImport: () => [...bookdropCommandKeys.all(), 'finalize'] as const,
  discardFiles: () => [...bookdropCommandKeys.all(), 'discard'] as const,
  rescan: () => [...bookdropCommandKeys.all(), 'rescan'] as const,
  previewPattern: () => [...bookdropCommandKeys.all(), 'preview-pattern'] as const,
  extractPattern: () => [...bookdropCommandKeys.all(), 'extract-pattern'] as const,
  bulkEditMetadata: () => [...bookdropCommandKeys.all(), 'bulk-edit'] as const,
};

export const bookdropCommandScopes = {
  files: {id: 'bookdrop.command.files'} as const,
};
