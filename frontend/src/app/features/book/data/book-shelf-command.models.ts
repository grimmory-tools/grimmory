import {BookShelf} from './book-response.models';

interface UpdatedBookShelves {
  readonly bookId: number;
  readonly shelves: readonly BookShelf[];
}

export interface UpdateBookShelfMembershipVariables {
  readonly bookIds: readonly number[];
  readonly assignShelfIds: readonly number[];
  readonly unassignShelfIds: readonly number[];
}

export interface UpdateBookShelfMembershipResult {
  readonly confirmedBookIds: readonly number[];
  readonly updatedBookShelves: readonly UpdatedBookShelves[];
}
