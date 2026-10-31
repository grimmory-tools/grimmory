import type {BookFileType} from '../../book/data/book-response.models';
import type {NotebookFilterParams, NotebookPageParams, ReadingSessionPageParams} from './reading-query.models';

export const readingQueryKeys = {
  all: () => ['reading', 'query'] as const,
  books: () => [...readingQueryKeys.all(), 'book'] as const,
  book: (bookId: number) => [...readingQueryKeys.books(), bookId] as const,
  annotations: (bookId: number) => [...readingQueryKeys.book(bookId), 'annotations'] as const,
  bookmarks: (bookId: number) => [...readingQueryKeys.book(bookId), 'bookmarks'] as const,
  notes: (bookId: number) => [...readingQueryKeys.book(bookId), 'notes'] as const,
  bookNotes: (bookId: number) => [...readingQueryKeys.book(bookId), 'book-notes'] as const,
  pdfAnnotations: (bookId: number) => [...readingQueryKeys.book(bookId), 'pdf-annotations'] as const,
  viewerSettings: (bookId: number, bookFileId: number) =>
    [...readingQueryKeys.book(bookId), 'viewer-settings', bookFileId] as const,
  sessions: (bookId: number, params: ReadingSessionPageParams) =>
    [...readingQueryKeys.book(bookId), 'sessions', params] as const,
  notebook: () => [...readingQueryKeys.all(), 'notebook'] as const,
  notebookPage: (params: NotebookPageParams) => [...readingQueryKeys.notebook(), 'page', params] as const,
  notebookExport: (params: NotebookFilterParams) => [...readingQueryKeys.notebook(), 'export', params] as const,
  notebookBooks: (search: string) => [...readingQueryKeys.notebook(), 'books', search] as const,
  files: () => [...readingQueryKeys.all(), 'file'] as const,
  file: (bookId: number) => [...readingQueryKeys.files(), bookId] as const,
  epubInfo: (bookId: number, bookType: BookFileType | null) =>
    [...readingQueryKeys.file(bookId), 'epub-info', bookType] as const,
  comicPages: (bookId: number, bookType: BookFileType | null) =>
    [...readingQueryKeys.file(bookId), 'comic-pages', bookType] as const,
  comicPageInfo: (bookId: number, bookType: BookFileType | null) =>
    [...readingQueryKeys.file(bookId), 'comic-page-info', bookType] as const,
  comicPageDimensions: (bookId: number, bookType: BookFileType | null) =>
    [...readingQueryKeys.file(bookId), 'comic-page-dimensions', bookType] as const,
  audiobookInfo: (bookId: number, bookType: BookFileType | null) =>
    [...readingQueryKeys.file(bookId), 'audiobook-info', bookType] as const,
};
