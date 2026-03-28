import {Component, computed, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {BookService} from '../../../book/service/book.service';
import {Book, ReadStatus} from '../../../book/model/book.model';
import {UserService} from '../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {TooltipModule} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../magic-shelf/component/magic-shelf-component';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {SortService} from '../../../book/service/sort.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {SortDirection, SortOption} from '../../../book/model/sort.model';
import {LibraryService} from '../../../book/service/library.service';

const DEFAULT_MAX_ITEMS = 20;

@Component({
  selector: 'app-main-dashboard',
  templateUrl: './main-dashboard.component.html',
  styleUrls: ['./main-dashboard.component.scss'],
  imports: [
    Button,
    DashboardScrollerComponent,
    ProgressSpinner,
    TooltipModule,
    TranslocoDirective
  ],
  standalone: true
})
export class MainDashboardComponent implements OnInit {

  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private dialogLauncher = inject(DialogLauncherService);
  protected userService = inject(UserService);
  private dashboardConfigService = inject(DashboardConfigService);
  private magicShelfService = inject(MagicShelfService);
  private ruleEvaluatorService = inject(BookRuleEvaluatorService);
  private sortService = inject(SortService);
  private pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  readonly dashboardConfig = this.dashboardConfigService.config;
  readonly isBooksLoading = this.bookService.isBooksLoading;
  readonly isLibrariesEmpty = computed(() =>
    !this.libraryService.isLibrariesLoading() && this.libraryService.libraries().length === 0
  );
  readonly scrollerBooks = computed(() => {
    const config = this.dashboardConfig();
    const books = this.bookService.books();
    const scrollerMap = new Map<string, Book[]>();

    for (const scroller of config.scrollers) {
      scrollerMap.set(scroller.id, this.getBooksForConfig(scroller, books, this.magicShelfService.shelves()));
    }

    return scrollerMap;
  });

  ScrollerType = ScrollerType;

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('dashboard.main.pageTitle'));
  }

  getBooksForScroller(config: ScrollerConfig): Book[] {
    return this.scrollerBooks().get(config.id) ?? [];
  }

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

    if (config.maxItems) {
      filteredBooks = filteredBooks.slice(0, config.maxItems);
    }

    if (config.sortField && config.sortDirection) {
      const sortOption = this.createSortOption(config.sortField, config.sortDirection);
      return this.sortService.applySort(filteredBooks, sortOption);
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

  openDashboardSettings(): void {
    this.dialogLauncher.openDashboardSettingsDialog();
  }

  createNewLibrary() {
    this.dialogLauncher.openLibraryCreateDialog();
  }
}
