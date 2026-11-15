export const shelfCommandKeys = {
  all: () => ['shelves', 'command'] as const,
  createShelf: () => [...shelfCommandKeys.all(), 'regular', 'create'] as const,
  updateShelf: () => [...shelfCommandKeys.all(), 'regular', 'update'] as const,
  deleteShelf: () => [...shelfCommandKeys.all(), 'regular', 'delete'] as const,
  createMagicShelf: () => [...shelfCommandKeys.all(), 'magic', 'create'] as const,
  updateMagicShelf: () => [...shelfCommandKeys.all(), 'magic', 'update'] as const,
  deleteMagicShelf: () => [...shelfCommandKeys.all(), 'magic', 'delete'] as const,
  updateMembership: () => [...shelfCommandKeys.all(), 'membership'] as const,
};

export const shelfCommandScopes = {
  membership: {id: 'shelves.command.membership'} as const,
};
