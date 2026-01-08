import {FormsModule} from "@angular/forms";
import {Button} from "primeng/button";
import {ActivatedRoute, Router} from "@angular/router";
import {AsyncPipe, NgClass, NgStyle} from "@angular/common";
import {filter, finalize, map, switchMap, tap} from "rxjs/operators";
import {combineLatest, Observable, Subscription} from "rxjs";
import {Book, ReadStatus} from "../../model/book.model";
import {BookService} from "../../service/book.service";
import {BookCardComponent} from "../book-browser/book-card/book-card.component";
import {CoverScalePreferenceService} from "../book-browser/cover-scale-preference.service";
import {Tab, TabList, TabPanel, TabPanels, Tabs} from "primeng/tabs";
import {Tag} from "primeng/tag";
import {VirtualScrollerModule} from "@iharbeck/ngx-virtual-scroller";
import {ProgressSpinner} from "primeng/progressspinner";
import {DynamicDialogRef} from "primeng/dynamicdialog";
import {ConfirmationService, MenuItem, MessageService} from "primeng/api";
import {UserService} from "../../../settings/user-management/user.service";
import {BookMenuService} from "../../service/book-menu.service";
import {LoadingService} from "../../../../core/services/loading.service";
import {BookDialogHelperService} from "../book-browser/book-dialog-helper.service";
import {TaskHelperService} from "../../../settings/task-management/task-helper.service";
import {MetadataRefreshType} from "../../../metadata/model/request/metadata-refresh-type.enum";
import {TieredMenu} from "primeng/tieredmenu";
import {Tooltip} from "primeng/tooltip";
import {Divider} from "primeng/divider";
import {animate, state, style, transition, trigger} from "@angular/animations";
import {Component, inject, OnDestroy} from '@angular/core';

@Component({
  selector: "app-series-page",
  standalone: true,
  templateUrl: "./series-page.component.html",
  styleUrls: ["./series-page.component.scss"],
  imports: [
    AsyncPipe,
    Button,
    FormsModule,
    NgStyle,
    NgClass,
    BookCardComponent,
    ProgressSpinner,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Tag,
    VirtualScrollerModule,
    TieredMenu,
    Tooltip,
    Divider
  ],
  animations: [
    trigger('slideInOut', [
      state('void', style({transform: 'translateY(100%)'})),
      state('*', style({transform: 'translateY(0)'})),
      transition(':enter', [animate('0.1s ease-in')]),
      transition(':leave', [animate('0.1s ease-out')])
    ])
  ]
})
export class SeriesPageComponent implements OnDestroy {

  private route = inject(ActivatedRoute);
  private bookService = inject(BookService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
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

  tab: string = "view";
  isExpanded = false;

  // Selection state
  selectedBooks = new Set<number>();
  lastSelectedIndex: number | null = null;
  currentBooks: Book[] = [];
  private userSub: Subscription;

  // Menu items
  protected metadataMenuItems: MenuItem[] | undefined;
  protected bulkReadActionsMenuItems: MenuItem[] | undefined;

  seriesParam$: Observable<string> = this.route.paramMap.pipe(
    map((params) => params.get("seriesName") || ""),
    map((name) => decodeURIComponent(name))
  );

  booksInSeries$: Observable<Book[]> = this.bookService.bookState$.pipe(
    filter((state) => state.loaded && !!state.books),
    map((state) => state.books || [])
  );

  filteredBooks$: Observable<Book[]> = combineLatest([
    this.seriesParam$.pipe(map((n) => n.trim().toLowerCase())),
    this.booksInSeries$,
  ]).pipe(
    map(([seriesName, books]) => {
      const inSeries = books.filter(
        (b) => b.metadata?.seriesName?.toLowerCase() === seriesName
      );
      return inSeries.sort((a, b) => {
        const aNum = a.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
        const bNum = b.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
        return aNum - bNum;
      });
    }),
    tap(books => this.currentBooks = books)
  );

  seriesTitle$: Observable<string> = combineLatest([
    this.seriesParam$,
    this.filteredBooks$,
  ]).pipe(map(([param, books]) => books[0]?.metadata?.seriesName || param));

  yearsRange$: Observable<string | null> = this.filteredBooks$.pipe(
    map((books) => {
      const years = books
        .map((b) => b.metadata?.publishedDate)
        .filter((d): d is string => !!d)
        .map((d) => {
          const match = d.match(/\d{4}/);
          return match ? parseInt(match[0], 10) : null;
        })
        .filter((y): y is number => y !== null);

      if (years.length === 0) return null;
      const min = Math.min(...years);
      const max = Math.max(...years);
      return min === max ? String(min) : `${min}-${max}`;
    })
  );

  firstBookWithDesc$: Observable<Book> = this.filteredBooks$.pipe(
    map((books) => books[0]),
    filter((b): b is Book => !!b),
    switchMap((b) => this.bookService.getBookByIdFromAPI(b.id, true))
  );

  firstDescription$: Observable<string> = this.firstBookWithDesc$.pipe(
    map((b) => b.metadata?.description || "")
  );

  seriesReadStatus$: Observable<ReadStatus> = this.filteredBooks$.pipe(
    map((books) => {
      if (!books || books.length === 0) return ReadStatus.UNREAD;
      const statuses = books.map((b) => (b.readStatus as ReadStatus) ?? ReadStatus.UNREAD);

      const hasWontRead = statuses.includes(ReadStatus.WONT_READ);
      if (hasWontRead) return ReadStatus.WONT_READ;

      const hasAbandoned = statuses.includes(ReadStatus.ABANDONED);
      if (hasAbandoned) return ReadStatus.ABANDONED;

      const allRead = statuses.every((s) => s === ReadStatus.READ);
      if (allRead) return ReadStatus.READ;

      // If any book is currently being read, surface series status as READING
      const isAnyReading = statuses.some(
        (s) => s === ReadStatus.READING || s === ReadStatus.RE_READING || s === ReadStatus.PAUSED
      );
      if (isAnyReading) return ReadStatus.READING;

      // If some are read and some are unread, surface as PARTIALLY_READ
      const someRead = statuses.some((s) => s === ReadStatus.READ);
      if (someRead) return ReadStatus.PARTIALLY_READ;

      const allUnread = statuses.every((s) => s === ReadStatus.UNREAD);
      if (allUnread) return ReadStatus.UNREAD;

      return ReadStatus.PARTIALLY_READ;
    })
  );

  // Progress like "12/20" (read/total)
  seriesReadProgress$: Observable<string> = this.filteredBooks$.pipe(
    map((books) => {
      const total = books?.length ?? 0;
      const readCount = (books || []).filter((b) => b.readStatus === ReadStatus.READ).length;
      return `${readCount}/${total}`;
    })
  );

  constructor() {
    this.userSub = this.userService.userState$.pipe(filter(u => !!u?.user && u.loaded))
      .subscribe(userState => {
        this.metadataMenuItems = this.bookMenuService.getMetadataMenuItems(
          () => this.autoFetchMetadata(),
          () => this.fetchMetadata(),
          () => this.bulkEditMetadata(),
          () => this.multiBookEditMetadata(),
          () => this.regenerateCoversForSelected(),
          userState.user
        );
        this.bulkReadActionsMenuItems = this.bookMenuService.getBulkReadActionsMenu(this.selectedBooks, this.user());
      });
  }

  ngOnDestroy(): void {
    this.userSub.unsubscribe();
  }

  get currentCardSize() {
    return this.coverScalePreferenceService.currentCardSize;
  }

  get gridColumnMinWidth(): string {
    return this.coverScalePreferenceService.gridColumnMinWidth;
  }

  goToAuthorBooks(author: string): void {
    this.handleMetadataClick("author", author);
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
        filter: `${filterKey}:${filterValue}`,
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

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  getStatusLabel(value: string | ReadStatus | null | undefined): string {
    const v = (value ?? '').toString().toUpperCase();
    switch (v) {
      case ReadStatus.UNREAD:
        return 'UNREAD';
      case ReadStatus.READING:
        return 'READING';
      case ReadStatus.RE_READING:
        return 'RE-READING';
      case ReadStatus.READ:
        return 'READ';
      case ReadStatus.PARTIALLY_READ:
        return 'PARTIALLY READ';
      case ReadStatus.PAUSED:
        return 'PAUSED';
      case ReadStatus.ABANDONED:
        return 'ABANDONED';
      case ReadStatus.WONT_READ:
        return "WON'T READ";
      default:
        return 'UNSET';
    }
  }

  getStatusSeverityClass(status: string): string {
    const normalized = status?.toUpperCase();
    switch (normalized) {
      case "UNREAD":
        return "bg-gray-500";
      case "READING":
        return "bg-blue-600";
      case "READ":
        return "bg-green-600";
      case "PARTIALLY_READ":
        return "bg-yellow-600";
      case "PAUSED":
        return "bg-slate-600";
      case "RE-READING":
      case "RE_READING":
        return "bg-purple-600";
      case "ABANDONED":
        return "bg-red-600";
      case "WONT_READ":
        return "bg-pink-700";
      default:
        return "bg-gray-600";
    }
  }

  handleBookSelection(book: Book, selected: boolean) {
    if (selected) {
      if (book.seriesBooks) {
        //it is a series
        this.selectedBooks = new Set([...this.selectedBooks, ...book.seriesBooks.map(book => book.id)]);
      } else {
        this.selectedBooks.add(book.id);
      }
    } else {
      if (book.seriesBooks) {
        //it is a series
        book.seriesBooks.forEach(book => {
          this.selectedBooks.delete(book.id);
        });
      } else {
        this.selectedBooks.delete(book.id);
      }
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
      for (let i = start; i <= end; i++) {
        const book = this.currentBooks[i];
        if (!book) continue;
        this.handleBookSelection(book, !isUnselectingRange);
      }
    }
    this.bulkReadActionsMenuItems = this.bookMenuService.getBulkReadActionsMenu(this.selectedBooks, this.user());
  }

  handleBookSelect(book: Book, selected: boolean): void {
    this.handleBookSelection(book, selected);
    this.bulkReadActionsMenuItems = this.bookMenuService.getBulkReadActionsMenu(this.selectedBooks, this.user());
  }

  selectAllBooks(): void {
    if (!this.currentBooks) return;
    for (const book of this.currentBooks) {
      this.selectedBooks.add(book.id);
    }
    this.bulkReadActionsMenuItems = this.bookMenuService.getBulkReadActionsMenu(this.selectedBooks, this.user());
  }

  deselectAllBooks(): void {
    this.selectedBooks.clear();
    this.bulkReadActionsMenuItems = this.bookMenuService.getBulkReadActionsMenu(this.selectedBooks, this.user());
  }

  confirmDeleteBooks(): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete ${this.selectedBooks.size} book(s)?`,
      header: 'Confirm Deletion',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        const count = this.selectedBooks.size;
        const loader = this.loadingService.show(`Deleting ${count} book(s)...`);

        this.bookService.deleteBooks(this.selectedBooks)
          .pipe(finalize(() => this.loadingService.hide(loader)))
          .subscribe(() => {
            this.selectedBooks.clear();
          });
      },
      reject: () => {
      }
    });
  }

  openShelfAssigner(): void {
    this.dialogRef = this.dialogHelperService.openShelfAssignerDialog(null, this.selectedBooks);
    if (this.dialogRef) {
      this.dialogRef.onClose.subscribe(result => {
        if (result.assigned) {
          this.selectedBooks.clear();
        }
      });
    }
  }

  lockUnlockMetadata(): void {
    this.dialogRef = this.dialogHelperService.openLockUnlockMetadataDialog(this.selectedBooks);
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
    this.dialogHelperService.openBulkMetadataEditDialog(this.selectedBooks);
  }

  multiBookEditMetadata(): void {
    this.dialogHelperService.openMultibookMetadataEditorDialog(this.selectedBooks);
  }

  regenerateCoversForSelected(): void {
    if (!this.selectedBooks || this.selectedBooks.size === 0) return;
    const count = this.selectedBooks.size;
    this.confirmationService.confirm({
      message: `Are you sure you want to regenerate covers for ${count} book(s)?`,
      header: 'Confirm Cover Regeneration',
      icon: 'pi pi-image',
      acceptLabel: 'Yes',
      rejectLabel: 'No',
      accept: () => {
        this.bookService.regenerateCoversForBooks(Array.from(this.selectedBooks)).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Cover Regeneration Started',
              detail: `Regenerating covers for ${count} book(s). Refresh the page when complete.`,
              life: 3000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Failed',
              detail: 'Could not start cover regeneration.',
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
    return this.userService.getCurrentUser();
  }

  get hasMetadataMenuItems(): boolean {
    return (this.metadataMenuItems?.length ?? 0) > 0;
  }

  get hasBulkReadActionsItems(): boolean {
    return (this.bulkReadActionsMenuItems?.length ?? 0) > 0;
  }
}
