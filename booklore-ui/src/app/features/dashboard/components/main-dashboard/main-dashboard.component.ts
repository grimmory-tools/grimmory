import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {LibraryService} from '../../../book/service/library.service';
import {Observable} from 'rxjs';
import {map, shareReplay, switchMap} from 'rxjs/operators';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {BookService} from '../../../book/service/book.service';
import {BookState} from '../../../book/model/state/book-state.model';
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
import {PageTitleService} from "../../../../shared/service/page-title.service";
import {SortDirection, SortOption} from '../../../book/model/sort.model';

const DEFAULT_MAX_ITEMS = 20;

@Component({
  selector: 'app-main-dashboard',
  templateUrl: './main-dashboard.component.html',
  styleUrls: ['./main-dashboard.component.scss'],
  imports: [
    Button,
    DashboardScrollerComponent,
    AsyncPipe,
    ProgressSpinner,
    TooltipModule,
    TranslocoDirective
  ],
  standalone: true
})
export class MainDashboardComponent implements OnInit {

  private bookService = inject(BookService);
  private dialogLauncher = inject(DialogLauncherService);
  protected userService = inject(UserService);
  private dashboardConfigService = inject(DashboardConfigService);
  private magicShelfService = inject(MagicShelfService);
  private ruleEvaluatorService = inject(BookRuleEvaluatorService);
  private sortService = inject(SortService);
  private pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  bookState$ = this.bookService.bookState$;
  dashboardConfig$ = this.dashboardConfigService.config$;

  private scrollerBooksCache = new Map<string, Observable<Book[]>>();

  isLibrariesEmpty$: Observable<boolean> = inject(LibraryService).libraryState$.pipe(
    map(state => !state.libraries || state.libraries.length === 0)
  );

  ScrollerType = ScrollerType;

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('dashboard.main.pageTitle'));

    this.dashboardConfig$.subscribe(() => {
      this.scrollerBooksCache.clear();
    });

    this.magicShelfService.shelvesState$.subscribe(() => {
      this.scrollerBooksCache.clear();
    });
  }

  private getLastReadBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let books = (state.books || []).filter(book =>
          book.lastReadTime &&
          (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED) &&
          this.hasEbookProgress(book)
        );
        books = books.sort((a, b) => {
          const aTime = new Date(a.lastReadTime!).getTime();
          const bTime = new Date(b.lastReadTime!).getTime();
          return bTime - aTime;
        });
        return books.slice(0, maxItems);
      })
    );
  }

  private getLastListenedBooks(maxItems: number): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let books = (state.books || []).filter(book =>
          book.lastReadTime &&
          (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED) &&
          book.audiobookProgress
        );
        books = books.sort((a, b) => {
          const aTime = new Date(a.lastReadTime!).getTime();
          const bTime = new Date(b.lastReadTime!).getTime();
          return bTime - aTime;
        });
        return books.slice(0, maxItems);
      })
    );
  }

  private hasEbookProgress(book: Book): boolean {
    return !!(book.epubProgress || book.pdfProgress || book.cbxProgress || book.koreaderProgress || book.koboProgress);
  }

  private getLatestAddedBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        let books = (state.books || []).filter(book => book.addedOn);

        books = books.sort((a, b) => {
          const aTime = new Date(a.addedOn!).getTime();
          const bTime = new Date(b.addedOn!).getTime();
          return bTime - aTime;
        });

        return books.slice(0, maxItems);
      })
    );
  }

  private getRandomBooks(maxItems: number, sortBy?: string): Observable<Book[]> {
    return this.bookService.bookState$.pipe(
      map((state: BookState) => {
        const excludedStatuses = new Set<ReadStatus>([
          ReadStatus.READ,
          ReadStatus.PARTIALLY_READ,
          ReadStatus.READING,
          ReadStatus.PAUSED,
          ReadStatus.WONT_READ,
          ReadStatus.ABANDONED
        ]);

        const candidates = (state.books || []).filter(book =>
          !book.readStatus || !excludedStatuses.has(book.readStatus)
        );

        return this.shuffleBooks(candidates, maxItems);
      })
    );
  }

  private getMagicShelfBooks(shelfId: number, maxItems?: number, sortBy?: string): Observable<Book[]> {
    return this.magicShelfService.getShelf(shelfId).pipe(
      switchMap((shelf) => {
        if (!shelf) return this.bookService.bookState$.pipe(map(() => []));

        let group: GroupRule;
        try {
          group = JSON.parse(shelf.filterJson);
        } catch (e) {
          console.error('Invalid filter JSON', e);
          return this.bookService.bookState$.pipe(map(() => []));
        }

        return this.bookService.bookState$.pipe(
          map((state: BookState) => {
            const allBooks = state.books || [];
            const filteredBooks = allBooks.filter((book) =>
              this.ruleEvaluatorService.evaluateGroup(book, group, allBooks)
            );

            return maxItems ? filteredBooks.slice(0, maxItems) : filteredBooks;
          })
        );
      })
    );
  }

  getBooksForScroller(config: ScrollerConfig): Observable<Book[]> {
    if (!this.scrollerBooksCache.has(config.id)) {
      let books$: Observable<Book[]>;

      switch (config.type) {
        case ScrollerType.LAST_READ:
          books$ = this.getLastReadBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.LAST_LISTENED:
          books$ = this.getLastListenedBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.LATEST_ADDED:
          books$ = this.getLatestAddedBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.RANDOM:
          books$ = this.getRandomBooks(config.maxItems || DEFAULT_MAX_ITEMS);
          break;
        case ScrollerType.MAGIC_SHELF:
          books$ = this.getMagicShelfBooks(config.magicShelfId!, config.maxItems).pipe(
            map(books => {
              if (config.sortField && config.sortDirection) {
                const sortOption = this.createSortOption(config.sortField, config.sortDirection);
                return this.sortService.applySort(books, sortOption);
              }
              return books;
            })
          );
          break;
        default:
          books$ = this.bookService.bookState$.pipe(map(() => []));
      }

      this.scrollerBooksCache.set(config.id, books$.pipe(shareReplay(1)));
    }

    return this.scrollerBooksCache.get(config.id)!;
  }

  private createSortOption(field: string, direction: string): SortOption {
    return {
      field: field,
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
