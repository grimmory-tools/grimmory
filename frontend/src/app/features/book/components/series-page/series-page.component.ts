import {FormsModule} from "@angular/forms";
import {ActivatedRoute, Router} from "@angular/router";
import {toSignal} from '@angular/core/rxjs-interop';
import {DecimalPipe, KeyValuePipe} from "@angular/common";
import {map} from "rxjs/operators";
import {Book, BookType, computeSeriesReadStatus, ReadStatus} from "../../model/book.model";
import {BookService} from "../../service/book.service";
import {Tab, TabList, TabPanel, TabPanels, Tabs} from "@openng/optimus-ui/tabs";
import {ProgressSpinner} from "@openng/optimus-ui/progressspinner";
import {ProgressBar} from "@openng/optimus-ui/progressbar";
import {BookNavigationService, type ReadableBook} from "../../service/book-navigation.service";
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Tooltip} from "@openng/optimus-ui/tooltip";
import {TagComponent} from "../../../../shared/components/tag/tag.component";
import {AfterViewChecked, ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, signal, untracked, viewChild} from '@angular/core';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {LanguageResolverService} from '../../../../shared/service/language-resolver.service';
import {CoverComponent} from '../../../../shared/components/cover/cover.component';
import {injectInfiniteQuery, injectQuery} from '@tanstack/angular-query';
import {AuthorService} from '../../../author-browser/service/author.service';
import {BookQueryService} from '../../data/book-query.service';
import {type BookPageParams} from '../../data/book-query-params';
import {type BookSummary} from '../../data/book-response.models';
import {createBrowseResults} from '../../../../shared/browse/results';
import {createVirtualGrid} from '../../../../shared/util/virtual-grid.util';
import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {BookCardComponent} from '../cards/book-card.component';
import {bookCardHeightForWidth} from '../cards/book-card.layout';
import {BookMenuComponent} from '../book-menu/book-menu.component';
import {BOOK_READ_STATUS_LABEL_KEYS, bookReadStatusLabelKey, CLEAR_BOOK_READ_STATUS} from '../../data/book-values';

const SERIES_BOOKS_PAGE_SIZE = 60;
const SERIES_GRID_GAP = 16;

interface ReadStatusSegment {
  status: ReadStatus;
  count: number;
  percent: number;
}

interface SeriesCompletion {
  owned: number;
  total: number;
  percent: number;
  missingNumbers: number[];
}

interface NextUpBook {
  book: Book;
  thumbnailUrl: string | null;
  isReading: boolean;
  progressPercent: number | null;
}

interface SeriesStats {
  totalPages: number;
  avgPersonalRating: number | null;
  avgGoodreads: number | null;
  avgAmazon: number | null;
  avgHardcover: number | null;
  formatCounts: Map<BookType, number>;
  totalAudioDurationSeconds: number;
}

@Component({
  selector: "app-series-page",
  standalone: true,
  templateUrl: "./series-page.component.html",
  styleUrls: ["./series-page.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    KeyValuePipe,
    FormsModule,
    ProgressSpinner,
    ProgressBar,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Tooltip,
    TranslocoDirective,
    TagComponent,
    CoverComponent,
    BookCardComponent,
    BookMenuComponent,
  ],
})
export class SeriesPageComponent implements AfterViewChecked {
  private readonly route = inject(ActivatedRoute);
  private readonly bookService = inject(BookService);
  private readonly languageResolver = inject(LanguageResolverService);
  private readonly router = inject(Router);
  private readonly t = inject(TranslocoService);
  protected urlHelper = inject(UrlHelperService);
  private readonly authorService = inject(AuthorService);
  private readonly bookNavigation = inject(BookNavigationService);
  private readonly bookQuery = inject(BookQueryService);
  private readonly coverScale = inject(CoverScalePreferenceService);


  private readonly descriptionContentRef = viewChild<ElementRef<HTMLElement>>('descriptionContent');
  tab: string = "view";
  readonly isExpanded = signal(false);
  readonly isOverflowing = signal(false);
  private readonly booksQuery = injectQuery(() => this.bookService.booksQueryOptions());
  protected readonly isBooksLoading = computed(() => this.booksQuery.isPending());

  private readonly seriesParam = toSignal(this.route.paramMap.pipe(
    map((params) => params.get("seriesName") || ""),
  ), {initialValue: ""});

  readonly filteredBooks = computed(() => {
    const seriesName = this.seriesParam().trim().toLowerCase();
    const inSeries = (this.booksQuery.data() ?? []).filter(
      (book) => book.metadata?.seriesName?.trim().toLowerCase() === seriesName
    );

    return [...inSeries].sort((a, b) => {
      const aNum = a.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      const bNum = b.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      return aNum - bNum;
    });
  });

  private readonly seriesBooksQuery = injectInfiniteQuery(() => ({
    ...this.bookQuery.infinitePage({
      size: SERIES_BOOKS_PAGE_SIZE,
      sort: [{key: 'seriesNumber', direction: 'asc'}],
      facets: {series: {include: [this.seriesParam()], exclude: []}},
    } satisfies BookPageParams),
    enabled: this.seriesParam() !== '',
  }));
  protected readonly seriesBooks = createBrowseResults<BookSummary>({
    query: this.seriesBooksQuery,
    listKey: this.seriesParam,
    artworkUrls: () => [],
    scrollToTop: () => undefined,
  });
  protected readonly bookMenu = viewChild(BookMenuComponent);
  protected readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);

  private readonly tabPanels = viewChild<TabPanels, ElementRef<HTMLElement>>(TabPanels, {read: ElementRef});
  private readonly seriesDetails = viewChild<ElementRef<HTMLElement>>('seriesDetails');
  private readonly seriesGridContent = viewChild<ElementRef<HTMLElement>>('seriesGridContent');
  private readonly seriesGridOffset = signal(0);
  protected readonly seriesVirtualGrid = createVirtualGrid({
    items: this.seriesBooks.items,
    itemKey: book => book.id,
    scrollElement: this.tabPanels,
    scrollMargin: this.seriesGridOffset,
    minItemWidth: computed(() => this.coverScale.currentCardSize().width),
    estimateItemHeight: width => bookCardHeightForWidth(width, {square: false, metaLines: 2}),
    gap: SERIES_GRID_GAP,
    fillItemWidth: true,
  });

  readonly coverBook = computed(() => this.filteredBooks()[0] ?? null);
  private readonly firstBookId = computed(() => this.coverBook()?.id ?? null);
  private readonly firstBookDetailQuery = injectQuery(() => ({
    ...this.bookService.bookDetailQueryOptions(this.firstBookId() ?? -1, true),
    enabled: this.firstBookId() != null,
  }));

  readonly seriesTitle = computed(() => this.filteredBooks()[0]?.metadata?.seriesName || this.seriesParam());

  readonly yearsRange = computed(() => {
    const years = this.filteredBooks()
      .map((book) => book.metadata?.publishedDate)
      .filter((date): date is string => !!date)
      .map((date) => {
        const match = date.match(/\d{4}/);
        return match ? parseInt(match[0], 10) : null;
      })
      .filter((year): year is number => year !== null);

    if (years.length === 0) return null;
    const min = Math.min(...years);
    const max = Math.max(...years);
    return min === max ? String(min) : `${min}-${max}`;
  });

  readonly firstBookWithDesc = computed(() => this.firstBookDetailQuery.data() ?? null);
  readonly firstDescription = computed(() => this.firstBookWithDesc()?.metadata?.description || "");

  readonly seriesReadStatus = computed(() => computeSeriesReadStatus(this.filteredBooks()));

  readonly seriesReadProgress = computed(() => {
    const books = this.filteredBooks();
    const readCount = books.filter((book) => book.readStatus === ReadStatus.READ).length;
    return `${readCount}/${books.length}`;
  });

  readonly allAuthors = computed(() => {
    const authors = new Set<string>();
    for (const book of this.filteredBooks()) {
      book.metadata?.authors?.forEach((author) => authors.add(author));
    }
    return Array.from(authors);
  });

  readonly allCategories = computed(() => {
    const categories = new Set<string>();
    for (const book of this.filteredBooks()) {
      book.metadata?.categories?.forEach((category) => categories.add(category));
    }
    return Array.from(categories);
  });

  readonly allPublishers = computed(() => {
    const publishers = new Set<string>();
    for (const book of this.filteredBooks()) {
      if (book.metadata?.publisher) {
        publishers.add(book.metadata.publisher);
      }
    }
    return Array.from(publishers);
  });

  readonly allLanguages = computed(() => {
    const languages = new Set<string>();
    for (const book of this.filteredBooks()) {
      if (book.metadata?.language) {
        languages.add(this.languageResolver.displayName(book.metadata.language));
      }
    }
    return Array.from(languages);
  });

  readonly seriesCompletion = computed<SeriesCompletion | null>(() => {
    const totals = this.filteredBooks()
      .map((book) => book.metadata?.seriesTotal)
      .filter((total): total is number => total != null && total > 0);
    if (totals.length === 0) return null;

    const seriesTotal = Math.max(...totals);
    const ownedNumbers = new Set(
      this.filteredBooks()
        .map((book) => book.metadata?.seriesNumber)
        .filter((number): number is number => number != null)
    );

    const missingNumbers: number[] = [];
    for (let i = 1; i <= seriesTotal; i++) {
      if (!ownedNumbers.has(i)) missingNumbers.push(i);
    }

    const owned = ownedNumbers.size;
    return {
      owned,
      total: seriesTotal,
      percent: seriesTotal > 0 ? Math.round((owned / seriesTotal) * 100) : 0,
      missingNumbers
    };
  });

  readonly readStatusBreakdown = computed(() => {
    const books = this.filteredBooks();
    if (books.length === 0) return [];

    const counts = new Map<ReadStatus, number>();
    for (const book of books) {
      const status = (book.readStatus as ReadStatus) ?? ReadStatus.UNREAD;
      counts.set(status, (counts.get(status) || 0) + 1);
    }

    const total = books.length;
    const segments: ReadStatusSegment[] = [];
    for (const [status, count] of counts) {
      segments.push({status, count, percent: (count / total) * 100});
    }
    return segments;
  });

  readonly nextUp = computed<NextUpBook | null>(() => {
    const books = this.filteredBooks();
    const reading = books.find(book =>
      book.readStatus === ReadStatus.READING ||
      book.readStatus === ReadStatus.RE_READING ||
      book.readStatus === ReadStatus.PAUSED
    );
    const target = reading ?? books.find(book => book.readStatus === ReadStatus.UNREAD || !book.readStatus);
    if (!target) return null;

    const isAudiobook = target.primaryFile?.bookType === 'AUDIOBOOK';
    const thumbnailUrl = isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(target.id, target.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(target.id, target.metadata?.coverUpdatedOn);

    const isReading = target.readStatus === ReadStatus.READING ||
      target.readStatus === ReadStatus.RE_READING ||
      target.readStatus === ReadStatus.PAUSED;

    let progressPercent: number | null = null;
    if (isReading) {
      progressPercent = target.epubProgress?.percentage
        ?? target.pdfProgress?.percentage
        ?? target.cbxProgress?.percentage
        ?? target.audiobookProgress?.percentage
        ?? target.koreaderProgress?.percentage
        ?? target.koboProgress?.percentage
        ?? null;
      if (progressPercent != null) {
        progressPercent = Math.round(progressPercent);
      }
    }

    return {book: target, thumbnailUrl, isReading, progressPercent};
  });

  readonly seriesStats = computed<SeriesStats>(() => {
    let totalPages = 0;
    const personalRatings: number[] = [];
    const goodreadsRatings: number[] = [];
    const amazonRatings: number[] = [];
    const hardcoverRatings: number[] = [];
    const formatCounts = new Map<BookType, number>();
    let totalAudioDurationSeconds = 0;

    for (const book of this.filteredBooks()) {
      if (book.metadata?.pageCount) totalPages += book.metadata.pageCount;
      if (book.personalRating != null) personalRatings.push(book.personalRating);
      if (book.metadata?.goodreadsRating != null) goodreadsRatings.push(book.metadata.goodreadsRating);
      if (book.metadata?.amazonRating != null) amazonRatings.push(book.metadata.amazonRating);
      if (book.metadata?.hardcoverRating != null) hardcoverRatings.push(book.metadata.hardcoverRating);

      const bookType = book.primaryFile?.bookType;
      if (bookType) {
        formatCounts.set(bookType, (formatCounts.get(bookType) || 0) + 1);
      }

      if (book.metadata?.audiobookMetadata?.durationSeconds) {
        totalAudioDurationSeconds += book.metadata.audiobookMetadata.durationSeconds;
      }
    }

    const avg = (ratings: number[]) => ratings.length > 0
      ? ratings.reduce((total, rating) => total + rating, 0) / ratings.length
      : null;

    return {
      totalPages,
      avgPersonalRating: avg(personalRatings),
      avgGoodreads: avg(goodreadsRatings),
      avgAmazon: avg(amazonRatings),
      avgHardcover: avg(hardcoverRatings),
      formatCounts,
      totalAudioDurationSeconds
    };
  });

  constructor() {
    effect(onCleanup => {
      const scroller = this.tabPanels()?.nativeElement;
      const details = this.seriesDetails()?.nativeElement;
      const grid = this.seriesGridContent()?.nativeElement;
      if (!scroller || !details || !grid) {
        return;
      }
      const observer = new ResizeObserver(() => this.seriesGridOffset.set(Math.round(
        grid.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop
      )));
      observer.observe(details);
      onCleanup(() => observer.disconnect());
    });

    effect(() => {
      const virtualItems = this.seriesVirtualGrid.virtualizer.getVirtualItems();
      const first = virtualItems.at(0);
      const last = virtualItems.at(-1);
      if (first && last) {
        untracked(() => this.seriesBooks.onRenderedRange({start: first.index, end: last.index}));
      }
    });
  }

  goToAuthorBooks(author: string): void {
    this.authorService.getAuthorByName(author).subscribe({
      next: (authorDetails) => {
        this.router.navigate(['/author', authorDetails.id]);
      },
      error: () => {
        this.navigateToFilteredBooks('author', author);
      }
    });
  }

  goToCategory(category: string): void {
    this.navigateToFilteredBooks("genre", category);
  }

  goToPublisher(publisher: string): void {
    this.navigateToFilteredBooks("publisher", publisher);
  }

  private navigateToFilteredBooks(
    filterKey: string,
    filterValue: string
  ): void {
    this.router.navigate(["/all-books"], {
      queryParams: {
        facet: `${filterKey}:${filterValue}`,
      },
    });
  }

  ngAfterViewChecked(): void {
    if (!this.isExpanded()) {
      const el = this.descriptionContentRef()?.nativeElement;
      if (!el) return;

      this.isOverflowing.set(el.scrollHeight > el.clientHeight);
    }
  }

  toggleExpand(): void {
    this.isExpanded.update(isExpanded => !isExpanded);
  }

  getStatusLabel(status: ReadStatus | undefined): string {
    return this.t.translate(bookReadStatusLabelKey(status) ?? BOOK_READ_STATUS_LABEL_KEYS[CLEAR_BOOK_READ_STATUS]);
  }

  getStatusColor(status: string | ReadStatus | null | undefined): string {
    const normalized = (status ?? '').toString().toUpperCase();
    switch (normalized) {
      case "UNREAD":
        return "#6b7280";
      case "READING":
        return "#2563eb";
      case "READ":
        return "#16a34a";
      case "PARTIALLY_READ":
        return "#ca8a04";
      case "PAUSED":
        return "#475569";
      case "RE-READING":
      case "RE_READING":
        return "#9333ea";
      case "ABANDONED":
        return "#dc2626";
      case "WONT_READ":
        return "#be185d";
      default:
        return "#4b5563";
    }
  }

  getBookCoverUrl(book: Book): string | null {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookCoverUrl(book.id, book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getCoverUrl(book.id, book.metadata?.coverUpdatedOn);
  }

  getBookThumbnailUrl(book: Book): string | null {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(book.id, book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn);
  }

  protected readBook(book: ReadableBook): void {
    this.bookNavigation.readBook(book);
  }

  viewBookDetails(bookId: number): void {
    this.router.navigate(['/book', bookId], {
      queryParams: {tab: 'view'}
    });
  }

  getBookProgress(book: Book): number | null {
    const progress = book.epubProgress?.percentage
      ?? book.pdfProgress?.percentage
      ?? book.cbxProgress?.percentage
      ?? book.audiobookProgress?.percentage
      ?? book.koreaderProgress?.percentage
      ?? book.koboProgress?.percentage;
    if (progress == null || progress <= 0) return null;
    const pct = progress > 1 ? progress : Math.round(progress * 100);
    return Math.min(Math.round(pct), 100);
  }

  formatDuration(totalSeconds: number): string {
    if (!totalSeconds || !isFinite(totalSeconds)) return '0:00';
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    if (h > 0) {
      return `${h}h ${m}m`;
    }
    return `${m}m`;
  }

  getRatingPercent(rating: number | null | undefined): number {
    if (rating == null) return 0;
    return Math.round((rating / 5) * 100);
  }
}
