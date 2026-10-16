export const BOOKS_QUERY_KEY = ['books'] as const;

export const BOOK_DETAIL_QUERY_PREFIX = ['books', 'detail'] as const;

export const bookDetailQueryKey = (bookId: number, withDescription: boolean) =>
  [...BOOK_DETAIL_QUERY_PREFIX, bookId, withDescription] as const;

export const bookDetailQueryPrefix = (bookId: number) =>
  [...BOOK_DETAIL_QUERY_PREFIX, bookId] as const;
