import {BookDetail} from './book-response.models';

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
