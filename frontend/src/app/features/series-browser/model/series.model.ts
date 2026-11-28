import {Book, BookType, computeSeriesReadStatus, ReadStatus} from '../../book/model/book.model';

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

export interface AppSeriesCoverBook {
  bookId: number;
  coverUpdatedOn?: string | null;
  audiobookCoverUpdatedOn?: string | null;
  seriesNumber?: number | null;
  primaryFileType?: BookType | null;
  title?: string | null;
  authors?: string[] | null;
  readStatus?: ReadStatus | null;
}

export interface AppSeriesSummaryResponse {
  seriesName: string;
  bookCount: number;
  seriesTotal?: number | null;
  authors?: string[] | null;
  booksRead: number;
  latestAddedOn?: string | null;
  lastReadTime?: string | null;
  seriesStatus?: ReadStatus | null;
  coverBooks?: AppSeriesCoverBook[] | null;
  nextUnread?: AppSeriesCoverBook | null;
}

export interface AppSeriesPageResponse {
  content: AppSeriesSummaryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export function mapAppSeriesSummary(summary: AppSeriesSummaryResponse): SeriesSummary {
  const coverBooks = (summary.coverBooks ?? []).map(toCardBook);
  const nextUnread = summary.nextUnread ? toCardBook(summary.nextUnread) : null;
  return {
    seriesName: summary.seriesName,
    books: [],
    authors: summary.authors ?? [],
    categories: [],
    bookCount: summary.bookCount,
    readCount: summary.booksRead,
    progress: summary.bookCount > 0 ? summary.booksRead / summary.bookCount : 0,
    seriesStatus: summary.seriesStatus ?? ReadStatus.UNREAD,
    nextUnread,
    lastReadTime: summary.lastReadTime ?? null,
    coverBooks,
    addedOn: summary.latestAddedOn ?? null,
  };
}

function toCardBook(summary: AppSeriesCoverBook): Book {
  return {
    id: summary.bookId,
    libraryId: 0,
    libraryName: '',
    primaryFile: summary.primaryFileType
      ? {id: 0, bookId: summary.bookId, bookType: summary.primaryFileType}
      : undefined,
    readStatus: summary.readStatus ?? ReadStatus.UNREAD,
    metadata: {
      bookId: summary.bookId,
      title: summary.title ?? undefined,
      authors: summary.authors ?? [],
      seriesNumber: summary.seriesNumber ?? undefined,
      coverUpdatedOn: summary.coverUpdatedOn ?? undefined,
      audiobookCoverUpdatedOn: summary.audiobookCoverUpdatedOn ?? undefined,
    },
  };
}

export function buildSeriesSummaries(books: readonly Book[]): SeriesSummary[] {
  const seriesMap = new Map<string, Book[]>();

  for (const book of books) {
    const seriesName = book.metadata?.seriesName;
    if (!seriesName) continue;

    const key = seriesName.trim().toLowerCase();
    const seriesBooks = seriesMap.get(key);
    if (seriesBooks) {
      seriesBooks.push(book);
    } else {
      seriesMap.set(key, [book]);
    }
  }

  const summaries: SeriesSummary[] = [];

  for (const seriesBooks of seriesMap.values()) {
    const sorted = seriesBooks.sort((a, b) => {
      const aNum = a.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      const bNum = b.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      return aNum - bNum;
    });

    const displayName = sorted[0].metadata?.seriesName?.trim() || '';
    const authorSet = new Set<string>();
    const categorySet = new Set<string>();

    for (const book of sorted) {
      book.metadata?.authors?.forEach(a => authorSet.add(a));
      book.metadata?.categories?.forEach(c => categorySet.add(c));
    }

    const readCount = sorted.filter(b => b.readStatus === ReadStatus.READ).length;
    const bookCount = sorted.length;

    const lastReadTime = sorted
      .map(b => b.lastReadTime)
      .filter((t): t is string => !!t)
      .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0] || null;

    const addedOn = sorted
      .map(b => b.addedOn)
      .filter((t): t is string => !!t)
      .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0] || null;

    const nextUnread = sorted.find(b => b.readStatus !== ReadStatus.READ) ?? null;

    summaries.push({
      seriesName: displayName,
      books: sorted,
      authors: Array.from(authorSet),
      categories: Array.from(categorySet),
      bookCount,
      readCount,
      progress: bookCount > 0 ? readCount / bookCount : 0,
      seriesStatus: computeSeriesReadStatus(sorted),
      nextUnread,
      lastReadTime,
      coverBooks: sorted.slice(0, 7),
      addedOn
    });
  }

  return summaries;
}
