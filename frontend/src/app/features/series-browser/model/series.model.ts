import {Book, ReadStatus} from '../../book/model/book.model';

export interface SeriesSummary {
  seriesName: string;
  books: Book[];
  authors: string[];
  categories: string[];
  bookCount: number;
  readCount: number;
  progress: number;
  seriesStatus: ReadStatus;
  nextUnread: Book | null;
  lastReadTime: string | null;
  coverBooks: Book[];
  addedOn: string | null;
}
