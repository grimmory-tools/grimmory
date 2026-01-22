import {inject, Injectable} from '@angular/core';
import {combineLatest, map, Observable} from 'rxjs';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../../../book/service/book.service';

@Injectable({
  providedIn: 'root'
})
export class LibrariesSummaryService {
  private bookService = inject(BookService);
  private libraryFilterService = inject(LibraryFilterService);

  selectedLibrary$ = this.libraryFilterService.selectedLibrary$;

  getBooksSummary(): Observable<{
    totalBooks: number;
    totalSizeKb: number;
    totalAuthors: number;
    totalSeries: number;
    totalPublishers: number;
  }> {
    return combineLatest([
      this.bookService.bookState$,
      this.selectedLibrary$
    ]).pipe(
      map(([state, selectedLibraryId]) => {
        if (!state.loaded || !state.books || state.books.length === 0) {
          return {totalBooks: 0, totalSizeKb: 0, totalAuthors: 0, totalSeries: 0, totalPublishers: 0};
        }

        const filteredBooks = selectedLibraryId
          ? state.books.filter(book => book.libraryId === selectedLibraryId)
          : state.books;

        const totalBooks = filteredBooks.length;
        const totalSizeKb = filteredBooks.reduce((sum, book) => sum + (book.fileSizeKb || 0), 0);

        const authorSet = new Set<string>();
        const seriesSet = new Set<string>();
        const publisherSet = new Set<string>();

        filteredBooks.forEach(book => {
          if (Array.isArray(book.metadata?.authors)) {
            book.metadata.authors.forEach(a => {
              const name = a?.trim();
              if (name) authorSet.add(name);
            });
          }

          const seriesName = book.metadata?.seriesName?.trim();
          if (seriesName) seriesSet.add(seriesName);

          const publisher = book.metadata?.publisher?.trim();
          if (publisher) publisherSet.add(publisher);
        });

        return {
          totalBooks,
          totalSizeKb,
          totalAuthors: authorSet.size,
          totalSeries: seriesSet.size,
          totalPublishers: publisherSet.size
        };
      })
    );
  }

  getFormattedSize(): Observable<string> {
    return this.getBooksSummary().pipe(
      map(summary => this.formatSizeKb(summary.totalSizeKb))
    );
  }

  private formatSizeKb(kb: number): string {
    if (!kb) return '0 KB';
    const kilo = 1024;
    const megaKb = kilo; // 1 MB = 1024 KB
    const gigaKb = kilo * megaKb; // 1 GB = 1024 * 1024 KB
    if (kb >= gigaKb) {
      return (kb / gigaKb).toFixed(2) + ' GB';
    }
    if (kb >= megaKb) {
      return (kb / megaKb).toFixed(2) + ' MB';
    }
    return kb + ' KB';
  }
}
