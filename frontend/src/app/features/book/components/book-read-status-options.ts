import {
  BOOK_READ_STATUSES,
  type KnownBookReadStatus,
} from '../data/book-response.models';

export type BookReadStatusTarget = Exclude<KnownBookReadStatus, 'UNSET'>;

export const CLEAR_BOOK_READ_STATUS = 'UNSET' satisfies KnownBookReadStatus;
export const CLEAR_BOOK_READ_STATUS_LABEL_KEY = 'book.filter.readStatus.unset';

export const BOOK_READ_STATUS_LABEL_KEYS: Readonly<Record<BookReadStatusTarget, string>> = {
  UNREAD: 'book.filter.readStatus.unread',
  READING: 'book.filter.readStatus.reading',
  RE_READING: 'book.filter.readStatus.reReading',
  PARTIALLY_READ: 'book.filter.readStatus.partiallyRead',
  PAUSED: 'book.filter.readStatus.paused',
  READ: 'book.filter.readStatus.read',
  WONT_READ: 'book.filter.readStatus.wontRead',
  ABANDONED: 'book.filter.readStatus.abandoned',
};

export const BOOK_READ_STATUS_TARGETS: readonly BookReadStatusTarget[] =
  BOOK_READ_STATUSES.filter(status => status !== 'UNSET');
