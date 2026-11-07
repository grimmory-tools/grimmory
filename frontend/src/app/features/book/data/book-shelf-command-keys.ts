import {bookCommandKeys} from './book-command-keys';

export const bookShelfCommandKeys = {
  updateMembership: () => [...bookCommandKeys.all(), 'shelf', 'update-membership'] as const,
};

export const bookShelfCommandScopes = {
  regularShelves: {id: 'books.command.shelf'} as const,
};
