import {computed, inject, Injectable} from '@angular/core';
import {BookService} from '../../book/service/book.service';
import {Book, computeSeriesReadStatus, ReadStatus} from '../../book/model/book.model';
import {SeriesSummary} from '../model/series.model';

@Injectable({
  providedIn: 'root'
})
export class SeriesDataService {

  private bookService = inject(BookService);

  allSeries = computed(() => this.buildSeriesSummaries(this.bookService.books()));

  private buildSeriesSummaries(books: Book[]): SeriesSummary[] {
    const seriesMap = new Map<string, Book[]>();

    for (const book of books) {
      const seriesName = book.metadata?.seriesName;
      if (!seriesName) continue;

      const key = seriesName.trim().toLowerCase();
      if (!seriesMap.has(key)) {
        seriesMap.set(key, []);
      }
      seriesMap.get(key)!.push(book);
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

      const nextUnread = sorted.find(b => b.readStatus !== ReadStatus.READ) || null;

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

}
