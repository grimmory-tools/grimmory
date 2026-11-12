export const authorCommandKeys = {
  all: () => ['authors', 'command'] as const,
  deleteAuthors: () => [...authorCommandKeys.all(), 'delete'] as const,
  quickMatch: () => [...authorCommandKeys.all(), 'quick-match'] as const,
  autoMatch: () => [...authorCommandKeys.all(), 'auto-match'] as const,
  update: () => [...authorCommandKeys.all(), 'update'] as const,
  match: () => [...authorCommandKeys.all(), 'match'] as const,
  uploadPhoto: () => [...authorCommandKeys.all(), 'upload-photo'] as const,
  uploadPhotoFromUrl: () => [...authorCommandKeys.all(), 'upload-photo-from-url'] as const,
};

export const authorCommandScopes = {
  records: {id: 'authors.command.records'} as const,
};
