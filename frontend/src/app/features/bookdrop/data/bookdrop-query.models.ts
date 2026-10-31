import type {PagedResponse} from '../../../core/data/page.models';
import type {BookDetailMetadata} from '../../book/data/book-response.models';

export type BookdropMetadata = Omit<BookDetailMetadata, 'bookId'>;

export interface BookdropFileNotification {
  readonly pendingCount: number;
  readonly totalCount: number;
  readonly lastUpdatedAt: string;
}

export interface BookdropFile {
  readonly id: number;
  readonly fileName: string;
  readonly filePath: string;
  readonly fileSize: number;
  readonly originalMetadata: BookdropMetadata | null;
  readonly fetchedMetadata: BookdropMetadata | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export type BookdropFilePage = PagedResponse<BookdropFile>;

export interface BookdropFilesParams {
  readonly page: number;
  readonly size: number;
}
