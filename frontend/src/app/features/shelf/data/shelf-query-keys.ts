export const shelfQueryKeys = {
  all: () => ['shelves', 'query'] as const,
  shelves: () => [...shelfQueryKeys.all(), 'regular'] as const,
  magicShelves: () => [...shelfQueryKeys.all(), 'magic'] as const,
};
