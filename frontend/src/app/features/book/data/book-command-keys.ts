export const bookCommandKeys = {
  all: () => ['books', 'command'] as const,
  setReadStatus: () => [...bookCommandKeys.all(), 'read-status'] as const,
  deleteBooks: () => [...bookCommandKeys.all(), 'delete'] as const,
  deleteAdditionalFile: () => [...bookCommandKeys.all(), 'delete-additional-file'] as const,
  attachBookFiles: () => [...bookCommandKeys.all(), 'attach-files'] as const,
  resetProgress: () => [...bookCommandKeys.all(), 'reset-progress'] as const,
  setAllMetadataLocks: () => [...bookCommandKeys.all(), 'metadata', 'all-locks'] as const,
  bulkRegenerateCovers: () => [...bookCommandKeys.all(), 'cover', 'bulk-regenerate'] as const,
  bulkGenerateCovers: () => [...bookCommandKeys.all(), 'cover', 'bulk-generate'] as const,
  quickSend: () => [...bookCommandKeys.all(), 'send', 'quick'] as const,
};

export const bookCommandScopes = {
  readingState: {id: 'books.command.reading-state'} as const,
  deletion: {id: 'books.command.deletion'} as const,
  metadata: {id: 'books.command.metadata'} as const,
};
