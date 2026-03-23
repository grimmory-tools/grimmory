export const BOOKS_QUERY_KEY = ['books'] as const;

export const bookDetailQueryKey = (bookId: number, withDescription: boolean) =>
  ['books', 'detail', bookId, withDescription] as const;

export const bookDetailQueryPrefix = (bookId: number) =>
  ['books', 'detail', bookId] as const;

export const bookRecommendationsQueryKey = (bookId: number, limit: number) =>
  ['books', 'recommendations', bookId, limit] as const;

export const bookRecommendationsQueryPrefix = (bookId: number) =>
  ['books', 'recommendations', bookId] as const;
