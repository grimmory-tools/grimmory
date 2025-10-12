import {Component, EventEmitter, inject, Input, OnChanges, OnDestroy, OnInit, Output} from '@angular/core';
import {TableModule} from 'primeng/table';
import {DatePipe, NgClass} from '@angular/common';
import {Rating} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {TooltipModule} from "primeng/tooltip";
import {Book, BookMetadata, ReadStatus} from '../../../model/book.model';
import {SortOption} from '../../../model/sort.model';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {Button} from 'primeng/button';
import {BookService} from '../../../service/book.service';
import {MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {filter, Subject} from 'rxjs';
import {UserService} from '../../../../settings/user-management/user.service';
import {BookMetadataCenterComponent} from '../../../../metadata/component/book-metadata-center/book-metadata-center.component';
import {DialogService} from 'primeng/dynamicdialog';
import {take, takeUntil} from 'rxjs/operators';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';

@Component({
  selector: 'app-book-table',
  standalone: true,
  templateUrl: './book-table.component.html',
  imports: [
    TableModule,
    Rating,
    FormsModule,
    Button,
    TooltipModule,
    NgClass
  ],
  styleUrls: ['./book-table.component.scss'],
  providers: [DatePipe]
})
export class BookTableComponent implements OnInit, OnDestroy, OnChanges {
  selectedBooks: Book[] = [];
  selectedBookIds = new Set<number>();

  @Output() selectedBooksChange = new EventEmitter<Set<number>>();
  @Input() books: Book[] = [];
  @Input() sortOption: SortOption | null = null;
  @Input() visibleColumns: any[] = [];

  protected urlHelper = inject(UrlHelperService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private dialogService = inject(DialogService);
  private router = inject(Router);
  private datePipe = inject(DatePipe);
  private readStatusHelper = inject(ReadStatusHelper);

  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  private destroy$ = new Subject<void>();

  readonly allColumns = [
    {field: 'readStatus', header: '📖'},
    {field: 'title', header: 'Title'},
    {field: 'authors', header: 'Authors'},
    {field: 'publisher', header: 'Publisher'},
    {field: 'seriesName', header: 'Series'},
    {field: 'seriesNumber', header: 'Series #'},
    {field: 'categories', header: 'Genres'},
    {field: 'publishedDate', header: 'Published'},
    {field: 'lastReadTime', header: 'Last Read'},
    {field: 'addedOn', header: 'Added'},
    {field: 'fileSizeKb', header: 'File Size'},
    {field: 'language', header: 'Language'},
    {field: 'pageCount', header: 'Pages'},
    {field: 'amazonRating', header: 'Amazon'},
    {field: 'amazonReviewCount', header: 'AZ #'},
    {field: 'goodreadsRating', header: 'Goodreads'},
    {field: 'goodreadsReviewCount', header: 'GR #'},
    {field: 'hardcoverRating', header: 'Hardcover'},
    {field: 'hardcoverReviewCount', header: 'HC #'}
  ];

  scrollHeight = 'calc(100dvh - 160px)';

  ngOnInit(): void {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(userState => {
        this.metadataCenterViewMode = userState?.user?.userSettings.metadataCenterViewMode ?? 'route';
      });

    this.setScrollHeight();
    window.addEventListener('resize', this.setScrollHeight.bind(this));
  }

  setScrollHeight() {
    const isMobile = window.innerWidth <= 768;
    this.scrollHeight = isMobile
      ? 'calc(100dvh - 125px)'
      : 'calc(100dvh - 150px)';
  }

  ngOnChanges() {
    const wrapperElements: HTMLCollection = document.getElementsByClassName('p-virtualscroller');
    Array.prototype.forEach.call(wrapperElements, function (wrapperElement) {
      wrapperElement.style["height"] = 'calc(100dvh - 160px)';
    });
  }

  selectAllBooks(): void {
    this.selectedBookIds = new Set(this.books.map(book => book.id));
    this.selectedBooks = [...this.books];
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  clearSelectedBooks(): void {
    this.selectedBookIds.clear();
    this.selectedBooks = [];
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  onRowSelect(event: any): void {
    this.selectedBookIds.add(event.data.id);
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  onRowUnselect(event: any): void {
    this.selectedBookIds.delete(event.data.id);
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  onHeaderCheckboxToggle(event: any): void {
    if (event.checked) {
      this.selectedBooks = [...this.books];
      this.selectedBookIds = new Set(this.books.map(book => book.id));
    } else {
      this.clearSelectedBooks();
    }
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  openMetadataCenter(id: number): void {
    if (this.metadataCenterViewMode === 'route') {
      this.router.navigate(['/book', id], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.dialogService.open(BookMetadataCenterComponent, {
        width: '95%',
        data: {bookId: id},
        modal: true,
        dismissableMask: true,
        showHeader: false
      });
    }
  }

  getStarColor(rating: number): string {
    if (rating >= 4.5) {
      return 'rgb(34, 197, 94)';
    } else if (rating >= 4) {
      return 'rgb(52, 211, 153)';
    } else if (rating >= 3.5) {
      return 'rgb(234, 179, 8)';
    } else if (rating >= 2.5) {
      return 'rgb(249, 115, 22)';
    } else {
      return 'rgb(239, 68, 68)';
    }
  }

  getAuthorNames(authors: string[]): string {
    return authors?.join(', ') || '';
  }

  getGenres(genres: string[]) {
    return genres?.join(', ') || '';
  }

  trackByBookId(index: number, book: Book): number {
    return book.id;
  }

  isMetadataFullyLocked(metadata: BookMetadata): boolean {
    const lockedKeys = Object.keys(metadata).filter(key => key.endsWith('Locked'));
    if (lockedKeys.length === 0) return false;
    return lockedKeys.every(key => metadata[key] === true);
  }

  formatFileSize(kb?: number): string {
    if (kb == null || isNaN(kb)) return '-';
    const mb = kb / 1024;
    return mb >= 1 ? `${mb.toFixed(1)} MB` : `${mb.toFixed(2)} MB`;
  }

  getReadStatusIcon(readStatus: ReadStatus | undefined): string {
    return this.readStatusHelper.getReadStatusIcon(readStatus);
  }

  getReadStatusClass(readStatus: ReadStatus | undefined): string {
    return this.readStatusHelper.getReadStatusClass(readStatus);
  }

  getReadStatusTooltip(readStatus: ReadStatus | undefined): string {
    return this.readStatusHelper.getReadStatusTooltip(readStatus);
  }

  shouldShowStatusIcon(readStatus: ReadStatus | undefined): boolean {
    return this.readStatusHelper.shouldShowStatusIcon(readStatus);
  }

  getCellValue(metadata: BookMetadata, book: Book, field: string): string | number {
    switch (field) {
      case 'readStatus':
        return this.readStatusHelper.getReadStatusTooltip(book?.readStatus);

      case 'title':
        return metadata.title ?? '';

      case 'authors':
        return this.getAuthorNames(metadata.authors!);

      case 'publisher':
        return metadata.publisher ?? '';

      case 'seriesName':
        return metadata.seriesName ?? '';

      case 'seriesNumber':
        return metadata.seriesNumber ?? '';

      case 'categories':
        return this.getGenres(metadata.categories!);

      case 'publishedDate':
        return metadata.publishedDate ? this.datePipe.transform(metadata.publishedDate, 'dd-MMM-yyyy') ?? '' : '';

      case 'lastReadTime':
        return book.lastReadTime ? this.datePipe.transform(book.lastReadTime, 'dd-MMM-yyyy') ?? '' : '';

      case 'addedOn':
        return book.addedOn ? this.datePipe.transform(book.addedOn, 'dd-MMM-yyyy') ?? '' : '';

      case 'fileSizeKb':
        return this.formatFileSize(book.fileSizeKb);

      case 'language':
        return metadata.language ?? '';

      case 'pageCount':
        return metadata.pageCount ?? '';

      case 'amazonRating':
      case 'goodreadsRating':
      case 'hardcoverRating': {
        const rating = metadata[field];
        return typeof rating === 'number' ? rating.toFixed(1) : '';
      }

      case 'amazonReviewCount':
      case 'goodreadsReviewCount':
      case 'hardcoverReviewCount':
        return metadata[field] ?? '';

      default:
        return '';
    }
  }

  toggleMetadataLock(metadata: BookMetadata): void {
    const lockKeys = Object.keys(metadata).filter(key => key.endsWith('Locked'));
    const allLocked = lockKeys.every(key => metadata[key] === true);
    const lockAction = allLocked ? 'UNLOCK' : 'LOCK';

    this.bookService.toggleAllLock(new Set([metadata.bookId]), lockAction).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: `Metadata ${lockAction === 'LOCK' ? 'Locked' : 'Unlocked'}`,
          detail: `Book metadata has been ${lockAction === 'LOCK' ? 'locked' : 'unlocked'} successfully.`,
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: `Failed to ${lockAction === 'LOCK' ? 'Lock' : 'Unlock'}`,
          detail: `An error occurred while ${lockAction === 'LOCK' ? 'locking' : 'unlocking'} the metadata.`,
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    window.removeEventListener('resize', this.setScrollHeight.bind(this));
  }
}
