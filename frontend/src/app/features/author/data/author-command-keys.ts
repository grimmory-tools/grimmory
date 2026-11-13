export const authorCommandKeys = {
  all: () => ['authors', 'command'] as const,
  deleteAuthors: () => [...authorCommandKeys.all(), 'delete'] as const,
  quickMatch: () => [...authorCommandKeys.all(), 'quick-match'] as const,
  autoMatch: () => [...authorCommandKeys.all(), 'auto-match'] as const,
};

export const authorCommandScopes = {
  records: {id: 'authors.command.records'} as const,
};
