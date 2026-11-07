import {BrowsePage} from '../../../core/data/browse.models';
import {BookDetail, BookSummary} from './book-response.models';

export type BookPage = BrowsePage<BookSummary>;

export interface BookDuplicateRequest {
  readonly libraryId: number;
  readonly matchByIsbn: boolean;
  readonly matchByExternalId: boolean;
  readonly matchByTitleAuthor: boolean;
  readonly matchByDirectory: boolean;
  readonly matchByFilename: boolean;
}

export interface BookDuplicateGroup {
  readonly suggestedTargetBookId: number;
  readonly matchReason: string;
  readonly books: readonly BookDetail[];
}
