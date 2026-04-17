import {computed, inject, Injectable} from '@angular/core';
import {BookService} from '../../book/service/book.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../magic-shelf/service/book-rule-evaluator.service';
import {SortService} from '../../book/service/sort.service';
import {ScrollerConfig, ScrollerType} from '../models/dashboard-config.model';
import {SortDirection, SortOption} from '../../book/model/sort.model';
import {DashboardConfigService} from './dashboard-config.service';
import {GroupRule} from '../../magic-shelf/component/magic-shelf-component';

const DEFAULT_MAX_ITEMS = 20;

@Injectable({
  providedIn: 'root'
})
export class DashboardBookService {
  private readonly bookService = inject(BookService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly ruleEvaluatorService = inject(BookRuleEvaluatorService);
  private readonly sortService = inject(SortService);
  private readonly configService = inject(DashboardConfigService);

  /**
   * Computed map of scroller ID to its filtered book list.
   * This centralizes all dashboard filtering logic and keeps it reactive.
   */
  readonly scrollerBooksMap = computed(() => {
    const config = this.configService.config();
    const books = this.bookService.books();
    const shelves = this.magicShelfService.shelves();
    const scrollerMap = new Map<string, Book[]>();

    for (const scroller of config.scrollers) {
      if (!scroller.enabled) continue;
      scrollerMap.set(scroller.id, this.getBooksForConfig(scroller, books, shelves));
    }

    return scrollerMap;
  });

  private getBooksForConfig(config: ScrollerConfig, books: Book[], magicShelves: {id?: number | null; filterJson: string}[]): Book[] {
    switch (config.type) {
      case ScrollerType.LAST_READ:
        return this.getLastReadBooks(books, config.maxItems || DEFAULT_MAX_ITEMS);
      case ScrollerType.LAST_LISTENED:
        return this.getLastListenedBooks(books, config.maxItems || DEFAULT_MAX_ITEMS);
      case ScrollerType.LATEST_ADDED:
        return this.getLatestAddedBooks(books, config.maxItems || DEFAULT_MAX_ITEMS);
      case ScrollerType.RANDOM:
        return this.getRandomBooks(books, config.maxItems || DEFAULT_MAX_ITEMS);
      case ScrollerType.MAGIC_SHELF:
        return this.getMagicShelfBooks(config, books, magicShelves);
      default:
        return [];
    }
  }

  private getLastReadBooks(books: Book[], maxItems: number): Book[] {
    const recentBooks = books.filter(book =>
      book.lastReadTime &&
      (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED) &&
      this.hasEbookProgress(book)
    );

    return recentBooks.sort((a, b) => {
      const aTime = new Date(a.lastReadTime!).getTime();
      const bTime = new Date(b.lastReadTime!).getTime();
      return bTime - aTime;
    }).slice(0, maxItems);
  }

  private getLastListenedBooks(books: Book[], maxItems: number): Book[] {
    const recentBooks = books.filter(book =>
      book.lastReadTime &&
      (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED) &&
      book.audiobookProgress
    );

    return recentBooks.sort((a, b) => {
      const aTime = new Date(a.lastReadTime!).getTime();
      const bTime = new Date(b.lastReadTime!).getTime();
      return bTime - aTime;
    }).slice(0, maxItems);
  }

  private hasEbookProgress(book: Book): boolean {
    return !!(book.epubProgress || book.pdfProgress || book.cbxProgress || book.koreaderProgress || book.koboProgress);
  }

  private getLatestAddedBooks(books: Book[], maxItems: number): Book[] {
    const addedBooks = books.filter(book => book.addedOn);

    return addedBooks.sort((a, b) => {
      const aTime = new Date(a.addedOn!).getTime();
      const bTime = new Date(b.addedOn!).getTime();
      return bTime - aTime;
    }).slice(0, maxItems);
  }

  private getRandomBooks(books: Book[], maxItems: number): Book[] {
    const excludedStatuses = new Set<ReadStatus>([
      ReadStatus.READ,
      ReadStatus.PARTIALLY_READ,
      ReadStatus.READING,
      ReadStatus.PAUSED,
      ReadStatus.WONT_READ,
      ReadStatus.ABANDONED
    ]);

    const candidates = books.filter(book =>
      !book.readStatus || !excludedStatuses.has(book.readStatus)
    );

    return this.shuffleBooks(candidates, maxItems);
  }

  private getMagicShelfBooks(
    config: ScrollerConfig,
    books: Book[],
    magicShelves: {id?: number | null; filterJson: string}[]
  ): Book[] {
    const shelf = magicShelves.find(currentShelf => currentShelf.id === config.magicShelfId);
    if (!shelf) {
      return [];
    }

    let group: GroupRule;
    try {
      group = JSON.parse(shelf.filterJson);
    } catch (e) {
      console.error('Invalid filter JSON', e);
      return [];
    }

    let filteredBooks = books.filter(book =>
      this.ruleEvaluatorService.evaluateGroup(book, group, books)
    );

    if (config.sortField && config.sortDirection) {
      const sortOption = this.createSortOption(config.sortField, config.sortDirection);
      filteredBooks = this.sortService.applySort(filteredBooks, sortOption);
    }

    if (config.maxItems) {
      filteredBooks = filteredBooks.slice(0, config.maxItems);
    }

    return filteredBooks;
  }

  private createSortOption(field: string, direction: string): SortOption {
    return {
      field,
      direction: direction === 'asc' ? SortDirection.ASCENDING : SortDirection.DESCENDING,
      label: ''
    };
  }

  private shuffleBooks(books: Book[], maxItems: number): Book[] {
    const shuffled = [...books];
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled.slice(0, maxItems);
  }
}
