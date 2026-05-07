import {FormsModule} from "@angular/forms";
import {Button} from "primeng/button";
import {ActivatedRoute, Router} from "@angular/router";
import {toSignal} from '@angular/core/rxjs-interop';
import {DecimalPipe, KeyValuePipe, NgClass, NgStyle} from "@angular/common";
import {finalize, map, take} from "rxjs/operators";
import {Book, BookType, computeSeriesReadStatus, ReadStatus} from "../../model/book.model";
import {BookService} from "../../service/book.service";
import {BookMetadataManageService} from "../../service/book-metadata-manage.service";
import {BookCardComponent} from "../book-browser/book-card/book-card.component";
import {Tab, TabList, TabPanel, TabPanels, Tabs} from "primeng/tabs";
import {ProgressSpinner} from "primeng/progressspinner";
import {ProgressBar} from "primeng/progressbar";
import {DynamicDialogRef} from "primeng/dynamicdialog";
import {ConfirmationService, MenuItem, MessageService} from "primeng/api";
import {UserService} from "../../../settings/user-management/user.service";
import {BookMenuService} from "../../service/book-menu.service";
import {LoadingService} from "../../../../core/services/loading.service";
import {BookDialogHelperService} from "../book-browser/book-dialog-helper.service";
import {TaskHelperService} from "../../../settings/task-management/task-helper.service";
import {MetadataRefreshType} from "../../../metadata/model/request/metadata-refresh-type.enum";
import {TieredMenu} from "primeng/tieredmenu";
import {AppSettingsService} from "../../../../shared/service/app-settings.service";
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Tooltip} from "primeng/tooltip";
import {Divider} from "primeng/divider";
import {TagComponent} from "../../../../shared/components/tag/tag.component";
import {AfterViewChecked, Component, computed, effect, ElementRef, inject, ViewChild, viewChild} from '@angular/core';
import {BookCardOverlayPreferenceService} from '../book-browser/book-card-overlay-preference.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {CoverPlaceholderComponent} from '../../../../shared/components/cover-generator/cover-generator.component';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {AuthorService} from '../../../author-browser/service/author.service';
import {createVirtualGrid} from '../../../../shared/util/virtual-grid.util';
import {LayoutService} from '../../../../shared/layout/layout.service';

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
  imports: [
    DecimalPipe,
    KeyValuePipe,
    Button,
    FormsModule,
    NgStyle,
    NgClass,
    BookCardComponent,
    ProgressSpinner,
    ProgressBar,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    TieredMenu,
    Tooltip,
    Divider,
    TranslocoDirective,
    TagComponent,
    CoverPlaceholderComponent,
  ],
})
export class SeriesPageComponent implements AfterViewChecked {

  private readonly DEFAULT_SERIES_CARD_WIDTH = 135;
  private readonly DEFAULT_SERIES_CARD_HEIGHT = 220;
  private readonly SERIES_GRID_GAP = 16;
  private readonly MOBILE_GRID_COLUMNS = 2;
  private route = inject(ActivatedRoute);
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private metadataCenterViewMode: "route" | "dialog" = "route";
  private dialogRef?: DynamicDialogRef | null;
  private router = inject(Router);
  protected userService = inject(UserService);
  private bookMenuService = inject(BookMenuService);
  protected confirmationService = inject(ConfirmationService);
  private loadingService = inject(LoadingService);
  private dialogHelperService = inject(BookDialogHelperService);
  protected taskHelperService = inject(TaskHelperService);
  private messageService = inject(MessageService);
  protected bookCardOverlayPreferenceService = inject(BookCardOverlayPreferenceService);
  protected appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);
  protected urlHelper = inject(UrlHelperService);
  private authorService = inject(AuthorService);
  private layoutService = inject(LayoutService);

  @ViewChild('descriptionContent') descriptionContentRef?: ElementRef<HTMLElement>;
  private readonly seriesGridScroll = viewChild<ElementRef<HTMLElement>>('seriesGridScroll');
  tab: string = "view";
  isExpanded = false;
  isOverflowing = false;
  protected appSettings = this.appSettingsService.appSettings;
  protected currentUser = this.userService.currentUser;
  protected isBooksLoading = this.bookService.isBooksLoading;

  // Selection state
  selectedBooks = new Set<number>();
  lastSelectedIndex: number | null = null;

  // Menu items
  protected metadataMenuItems: MenuItem[] | undefined;
  protected moreActionsMenuItems: MenuItem[] | undefined;
  protected readonly seriesViewEnabled = false;
  protected readonly seriesCardsCollapsed = false;
  protected readonly seriesCardsUseSquareCovers = false;
  protected readonly onSeriesBookCardSelect = this.handleBookSelect.bind(this);

  private seriesParam = toSignal(this.route.paramMap.pipe(
    map((params) => params.get("seriesName") || ""),
  ), {initialValue: ""});

  filteredBooks = computed(() => {
    const seriesName = this.seriesParam().trim().toLowerCase();
    const inSeries = this.bookService.books().filter(
      (book) => book.metadata?.seriesName?.trim().toLowerCase() === seriesName
    );

    return [...inSeries].sort((a, b) => {
      const aNum = a.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      const bNum = b.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      return aNum - bNum;
    });
  });
  private readonly minSeriesCardWidth = computed(() => this.DEFAULT_SERIES_CARD_WIDTH);
  private readonly seriesGridColumns = computed(() =>
    this.layoutService.isDesktop() ? undefined : this.MOBILE_GRID_COLUMNS
  );
  protected readonly seriesVirtualGrid = createVirtualGrid({
    items: this.filteredBooks,
    scrollElement: this.seriesGridScroll,
    minItemWidth: this.minSeriesCardWidth,
    gap: this.SERIES_GRID_GAP,
    columns: this.seriesGridColumns,
    fillItemWidth: true,
    estimateItemHeight: itemWidth => this.seriesCardSizeForWidth(itemWidth).height,
  });

  coverBook = computed(() => this.filteredBooks()[0] ?? null);
  private firstBookId = computed(() => this.coverBook()?.id ?? null);
  private firstBookDetailQuery = injectQuery(() => ({
    ...this.bookService.bookDetailQueryOptions(this.firstBookId() ?? -1, true),
    enabled: this.firstBookId() != null,
  }));

  seriesTitle = computed(() => this.filteredBooks()[0]?.metadata?.seriesName || this.seriesParam());

  yearsRange = computed(() => {
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

  firstBookWithDesc = computed(() => this.firstBookDetailQuery.data() ?? null);
  firstDescription = computed(() => this.firstBookWithDesc()?.metadata?.description || "");

  seriesReadStatus = computed(() => computeSeriesReadStatus(this.filteredBooks()));

  seriesReadProgress = computed(() => {
    const books = this.filteredBooks();
    const readCount = books.filter((book) => book.readStatus === ReadStatus.READ).length;
    return `${readCount}/${books.length}`;
  });

  allAuthors = computed(() => {
    const authors = new Set<string>();
    for (const book of this.filteredBooks()) {
      book.metadata?.authors?.forEach((author) => authors.add(author));
    }
    return Array.from(authors);
  });

  allCategories = computed(() => {
    const categories = new Set<string>();
    for (const book of this.filteredBooks()) {
      book.metadata?.categories?.forEach((category) => categories.add(category));
    }
    return Array.from(categories);
  });

  allPublishers = computed(() => {
    const publishers = new Set<string>();
    for (const book of this.filteredBooks()) {
      if (book.metadata?.publisher) {
        publishers.add(book.metadata.publisher);
      }
    }
    return Array.from(publishers);
  });

  allLanguages = computed(() => {
    const languages = new Set<string>();
    for (const book of this.filteredBooks()) {
      if (book.metadata?.language) {
        languages.add(book.metadata.language);
      }
    }
    return Array.from(languages);
  });

  seriesCompletion = computed<SeriesCompletion | null>(() => {
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

  readStatusBreakdown = computed(() => {
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

  nextUp = computed<NextUpBook | null>(() => {
    const books = this.filteredBooks();
    const reading = books.find(book =>
      book.readStatus === ReadStatus.READING ||
      book.readStatus === ReadStatus.RE_READING ||
      book.readStatus === ReadStatus.PAUSED
    );
    const target = reading || books.find(book => book.readStatus === ReadStatus.UNREAD || !book.readStatus);
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

  seriesStats = computed<SeriesStats>(() => {
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
    effect(() => {
      const user = this.currentUser();
      if (!user) {
        return;
      }

      this.metadataMenuItems = this.bookMenuService.getMetadataMenuItems(
        () => this.autoFetchMetadata(),
        () => this.fetchMetadata(),
        () => this.bulkEditMetadata(),
        () => this.multiBookEditMetadata(),
        () => this.regenerateCoversForSelected(),
        () => this.generateCustomCoversForSelected(),
        user
      );
      this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.currentUser());
    });

  }

  private seriesCardSizeForWidth(width: number): { width: number; height: number } {
    const cardWidth = Math.round(width);
    const aspectRatio = this.DEFAULT_SERIES_CARD_HEIGHT / this.DEFAULT_SERIES_CARD_WIDTH;
    return {
      width: cardWidth,
      height: Math.round(cardWidth * aspectRatio),
    };
  }

  goToAuthorBooks(author: string): void {
    this.authorService.getAuthorByName(author).subscribe({
      next: (authorDetails) => {
        this.router.navigate(['/author', authorDetails.id]);
      },
      error: () => {
        this.handleMetadataClick('author', author);
      }
    });
  }

  goToCategory(category: string): void {
    this.handleMetadataClick("category", category);
  }

  goToPublisher(publisher: string): void {
    this.handleMetadataClick("publisher", publisher);
  }

  private navigateToFilteredBooks(
    filterKey: string,
    filterValue: string
  ): void {
    this.router.navigate(["/all-books"], {
      queryParams: {
        view: "grid",
        sort: "title",
        direction: "asc",
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`,
      },
    });
  }

  private handleMetadataClick(filterKey: string, filterValue: string): void {
    if (this.metadataCenterViewMode === "dialog") {
      this.dialogRef?.close();
      setTimeout(
        () => this.navigateToFilteredBooks(filterKey, filterValue),
        200
      );
    } else {
      this.navigateToFilteredBooks(filterKey, filterValue);
    }
  }

  ngAfterViewChecked(): void {
    if (!this.isExpanded && this.descriptionContentRef) {
      const el = this.descriptionContentRef.nativeElement;
      this.isOverflowing = el.scrollHeight > el.clientHeight;
    }
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  getStatusLabel(value: string | ReadStatus | null | undefined): string {
    const v = (value ?? '').toString().toUpperCase();
    switch (v) {
      case ReadStatus.UNREAD:
        return this.t.translate('book.seriesPage.status.unread');
      case ReadStatus.READING:
        return this.t.translate('book.seriesPage.status.reading');
      case ReadStatus.RE_READING:
        return this.t.translate('book.seriesPage.status.reReading');
      case ReadStatus.READ:
        return this.t.translate('book.seriesPage.status.read');
      case ReadStatus.PARTIALLY_READ:
        return this.t.translate('book.seriesPage.status.partiallyRead');
      case ReadStatus.PAUSED:
        return this.t.translate('book.seriesPage.status.paused');
      case ReadStatus.ABANDONED:
        return this.t.translate('book.seriesPage.status.abandoned');
      case ReadStatus.WONT_READ:
        return this.t.translate('book.seriesPage.status.wontRead');
      default:
        return this.t.translate('book.seriesPage.status.unset');
    }
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

  getBookThumbnailUrl(book: Book): string | null {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(book.id, book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn);
  }

  readBook(bookId: number): void {
    this.bookService.readBook(bookId);
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

  private getSelectableBookIds(book: Book): number[] {
    return book.seriesBooks?.length
      ? book.seriesBooks.map(seriesBook => seriesBook.id)
      : [book.id];
  }

  isBookSelected(book: Book): boolean {
    return this.getSelectableBookIds(book).every(id => this.selectedBooks.has(id));
  }

  handleBookSelection(book: Book, selected: boolean) {
    const ids = this.getSelectableBookIds(book);
    if (selected) {
      ids.forEach(id => this.selectedBooks.add(id));
    } else {
      ids.forEach(id => this.selectedBooks.delete(id));
    }
  }

  onCheckboxClicked(event: { index: number; book: Book; selected: boolean; shiftKey: boolean }) {
    const {index, book, selected, shiftKey} = event;
    if (!shiftKey || this.lastSelectedIndex === null) {
      this.handleBookSelection(book, selected);
      this.lastSelectedIndex = index;
    } else {
      const start = Math.min(this.lastSelectedIndex, index);
      const end = Math.max(this.lastSelectedIndex, index);
      const isUnselectingRange = !selected;
      const books = this.filteredBooks();
      for (let i = start; i <= end; i++) {
        const book = books[i];
        if (!book) continue;
        this.handleBookSelection(book, !isUnselectingRange);
      }
    }
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  handleBookSelect(book: Book, selected: boolean): void {
    this.handleBookSelection(book, selected);
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  selectAllBooks(): void {
    for (const book of this.filteredBooks()) {
      this.getSelectableBookIds(book).forEach(id => this.selectedBooks.add(id));
    }
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  deselectAllBooks(): void {
    this.selectedBooks.clear();
    this.moreActionsMenuItems = this.bookMenuService.getMoreActionsMenu(this.selectedBooks, this.user());
  }

  confirmDeleteBooks(): void {
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.deleteMessage', {count: this.selectedBooks.size}),
      header: this.t.translate('book.browser.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptLabel: this.t.translate('common.delete'),
      rejectLabel: this.t.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-outlined',
      accept: () => {
        const count = this.selectedBooks.size;
        const loader = this.loadingService.show(this.t.translate('book.browser.loading.deleting', {count}));

        this.bookService.deleteBooks(this.selectedBooks)
          .pipe(finalize(() => this.loadingService.hide(loader)))
          .subscribe(() => {
            this.selectedBooks.clear();
          });
      }
    });
  }

  openShelfAssigner(): void {
    this.dialogRef = this.dialogHelperService.openShelfAssignerDialog(null, this.selectedBooks);
    if (this.dialogRef) {
      this.dialogRef.onClose.pipe(take(1)).subscribe(result => {
        if (result.assigned) {
          this.selectedBooks.clear();
        }
      });
    }
  }

  lockUnlockMetadata(): void {
    this.dialogRef = this.dialogHelperService.openLockUnlockMetadataDialog(this.selectedBooks);
    if (this.dialogRef) {
      this.dialogRef.onClose.pipe(take(1)).subscribe(() => {
        this.deselectAllBooks();
      });
    }
  }

  autoFetchMetadata(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: Array.from(this.selectedBooks),
    }).subscribe();
  }

  fetchMetadata(): void {
    this.dialogHelperService.openMetadataRefreshDialog(this.selectedBooks);
  }

  bulkEditMetadata(): void {
    this.dialogRef = this.dialogHelperService.openBulkMetadataEditDialog(this.selectedBooks);
    if (this.dialogRef) {
      this.dialogRef.onClose.pipe(take(1)).subscribe(() => {
        this.deselectAllBooks();
      });
    }
  }

  multiBookEditMetadata(): void {
    this.dialogRef = this.dialogHelperService.openMultibookMetadataEditorDialog(this.selectedBooks);
    if (this.dialogRef) {
      this.dialogRef.onClose.pipe(take(1)).subscribe(() => {
        this.deselectAllBooks();
      });
    }
  }

  regenerateCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.regenCoverMessage', {count}),
      header: this.t.translate('book.browser.confirm.regenCoverHeader'),
      icon: 'pi pi-image',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {
        label: this.t.translate('common.yes'),
        severity: 'success'
      },
      rejectButtonProps: {
        label: this.t.translate('common.no'),
        severity: 'secondary'
      },
      accept: () => {
        this.bookMetadataManageService.regenerateCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.browser.toast.regenCoverStartedSummary'),
              detail: this.t.translate('book.browser.toast.regenCoverStartedDetail', {count}),
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.browser.toast.failedSummary'),
              detail: this.t.translate('book.browser.toast.regenCoverFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  generateCustomCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: this.t.translate('book.browser.confirm.customCoverMessage', {count}),
      header: this.t.translate('book.browser.confirm.customCoverHeader'),
      icon: 'pi pi-palette',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {
        label: this.t.translate('common.yes'),
        severity: 'success'
      },
      rejectButtonProps: {
        label: this.t.translate('common.no'),
        severity: 'secondary'
      },
      accept: () => {
        this.bookMetadataManageService.generateCustomCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.browser.toast.customCoverStartedSummary'),
              detail: this.t.translate('book.browser.toast.customCoverStartedDetail', {count}),
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.browser.toast.failedSummary'),
              detail: this.t.translate('book.browser.toast.customCoverFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  moveFiles() {
    this.dialogHelperService.openFileMoverDialog(this.selectedBooks);
  }

  user() {
    return this.currentUser();
  }

  get hasMetadataMenuItems(): boolean {
    return (this.metadataMenuItems?.length ?? 0) > 0;
  }

  get hasMoreActionsItems(): boolean {
    return (this.moreActionsMenuItems?.length ?? 0) > 0;
  }
}
