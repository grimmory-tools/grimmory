import {BookType, ReadStatus} from '../../book/model/book.model';

export interface SeriesCoverBook {
  bookId: number;
  coverUpdatedOn: string | null;
  seriesNumber: number | null;
  primaryFileType: BookType | null;
}

export interface AppSeriesSummary {
  seriesName: string;
  bookCount: number;
  seriesTotal: number | null;
  authors: string[];
  booksRead: number;
  latestAddedOn: string | null;
  lastReadTime: string | null;
  seriesStatus: string | null;
  coverBooks: SeriesCoverBook[];
}

export interface SeriesSummary {
  seriesName: string;
  authors: string[];
  categories: string[];
  bookCount: number;
  readCount: number;
  progress: number;
  seriesStatus: ReadStatus;
  lastReadTime: string | null;
  coverBooks: SeriesCoverBook[];
  addedOn: string | null;
}
