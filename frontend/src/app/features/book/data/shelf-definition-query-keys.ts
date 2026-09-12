export const shelfDefinitionQueryKeys = {
  all: () => ['shelves', 'query'] as const,
  definitions: () => [...shelfDefinitionQueryKeys.all(), 'definitions'] as const,
};
