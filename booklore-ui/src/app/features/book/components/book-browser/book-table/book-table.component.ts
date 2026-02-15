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
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {MessageService} from 'primeng/api';
import {RouterLink} from '@angular/router';
import {filter, Subject} from 'rxjs';
import {UserService} from '../../../../settings/user-management/user.service';
import {take, takeUntil} from 'rxjs/operators';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

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
    NgClass,
    RouterLink,
    TranslocoDirective
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
  @Input() visibleColumns: { field: string; header: string }[] = [];
  @Input() preselectedBookIds = new Set<number>();

  protected urlHelper = inject(UrlHelperService);
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private datePipe = inject(DatePipe);
  private readStatusHelper = inject(ReadStatusHelper);
  private readonly t = inject(TranslocoService);

  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  private destroy$ = new Subject<void>();

  readonly allColumns: { field: string; header: string }[] = [
    {field: 'readStatus', header: this.t.translate('book.columnPref.columns.readStatus')},
    {field: 'title', header: this.t.translate('book.columnPref.columns.title')},
    {field: 'authors', header: this.t.translate('book.columnPref.columns.authors')},
    {field: 'publisher', header: this.t.translate('book.columnPref.columns.publisher')},
    {field: 'seriesName', header: this.t.translate('book.columnPref.columns.seriesName')},
    {field: 'seriesNumber', header: this.t.translate('book.columnPref.columns.seriesNumber')},
    {field: 'categories', header: this.t.translate('book.columnPref.columns.categories')},
    {field: 'publishedDate', header: this.t.translate('book.columnPref.columns.publishedDate')},
    {field: 'lastReadTime', header: this.t.translate('book.columnPref.columns.lastReadTime')},
    {field: 'addedOn', header: this.t.translate('book.columnPref.columns.addedOn')},
    {field: 'fileName', header: this.t.translate('book.columnPref.columns.fileName')},
    {field: 'fileSizeKb', header: this.t.translate('book.columnPref.columns.fileSizeKb')},
    {field: 'language', header: this.t.translate('book.columnPref.columns.language')},
    {field: 'isbn', header: this.t.translate('book.columnPref.columns.isbn')},
    {field: 'pageCount', header: this.t.translate('book.columnPref.columns.pageCount')},
    {field: 'amazonRating', header: this.t.translate('book.columnPref.columns.amazonRating')},
    {field: 'amazonReviewCount', header: this.t.translate('book.columnPref.columns.amazonReviewCount')},
    {field: 'goodreadsRating', header: this.t.translate('book.columnPref.columns.goodreadsRating')},
    {field: 'goodreadsReviewCount', header: this.t.translate('book.columnPref.columns.goodreadsReviewCount')},
    {field: 'hardcoverRating', header: this.t.translate('book.columnPref.columns.hardcoverRating')},
    {field: 'hardcoverReviewCount', header: this.t.translate('book.columnPref.columns.hardcoverReviewCount')},
    {field: 'ranobedbRating', header: this.t.translate('book.columnPref.columns.ranobedbRating')},
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

    this.selectedBookIds = this.preselectedBookIds;
    this.selectedBooks = this.bookService.getBooksByIdsFromState([...this.selectedBookIds]);
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

  onRowSelect(event: { data?: Book | Book[] }): void {
    if (event.data && !Array.isArray(event.data)) {
      this.selectedBookIds.add(event.data.id);
      this.selectedBooksChange.emit(this.selectedBookIds);
    }
  }

  onRowUnselect(event: { data?: Book | Book[] }): void {
    if (event.data && !Array.isArray(event.data)) {
      this.selectedBookIds.delete(event.data.id);
      this.selectedBooksChange.emit(this.selectedBookIds);
    }
  }

  onHeaderCheckboxToggle(event: { checked: boolean }): void {
    if (event.checked) {
      this.selectedBooks = [...this.books];
      this.selectedBookIds = new Set(this.books.map(book => book.id));
    } else {
      this.clearSelectedBooks();
    }
    this.selectedBooksChange.emit(this.selectedBookIds);
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

  getCellClickableValue(metadata: BookMetadata, book: Book, field: string) {
    const filterKeys: Record<string, string> = {
      'authors': 'author',
      'publisher': 'publisher',
      'categories': 'category',
      'language': 'language',
      'title': 'title',
      'isbn': 'isbn'
    } as const;

    let data: string[] = [metadata[field] as string];

    switch (field) {
      case 'title':
        return [
          {
            url: this.urlHelper.getBookUrl(book),
            anchor: metadata.title ?? book.fileName
          }
        ];

      case 'categories':
        data = metadata.categories ?? [];
        break;

      case 'authors':
        data = metadata.authors ?? [];
        break;

      case 'seriesName':
        return [
          {
            url: this.urlHelper.filterBooksBy('series', metadata.seriesName ?? ''),
            anchor: metadata.seriesName
          }
        ]
      case 'isbn':
        return [
          {
            url: '',
            anchor: this.getCellValue(metadata, book, 'isbn')
          }
        ];
    }

    return data.map(item => {
      return {
        url: this.urlHelper.filterBooksBy(filterKeys[field] ?? field, item),
        anchor: item
      }
    });
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

      case 'fileName':
        return book.primaryFile?.fileName ?? '';

      case 'fileSizeKb':
        return this.formatFileSize(book.fileSizeKb);

      case 'language':
        return metadata.language ?? '';

      case 'pageCount':
        return metadata.pageCount ?? '';

      case 'amazonRating':
      case 'goodreadsRating':
      case 'hardcoverRating':
      case 'ranobedbRating': {
        const rating = metadata[field];
        return typeof rating === 'number' ? rating.toFixed(1) : '';
      }

      case 'amazonReviewCount':
      case 'goodreadsReviewCount':
      case 'hardcoverReviewCount':
        return metadata[field] ?? '';

      case 'isbn':
        return metadata.isbn13 || metadata.isbn10 || '';

      default:
        return '';
    }
  }

  toggleMetadataLock(metadata: BookMetadata): void {
    const lockKeys = Object.keys(metadata).filter(key => key.endsWith('Locked'));
    const allLocked = lockKeys.every(key => metadata[key] === true);
    const lockAction = allLocked ? 'UNLOCK' : 'LOCK';

    this.bookMetadataManageService.toggleAllLock(new Set([metadata.bookId]), lockAction).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: lockAction === 'LOCK' ? this.t.translate('book.table.toast.metadataLockedSummary') : this.t.translate('book.table.toast.metadataUnlockedSummary'),
          detail: lockAction === 'LOCK' ? this.t.translate('book.table.toast.metadataLockedDetail') : this.t.translate('book.table.toast.metadataUnlockedDetail'),
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: lockAction === 'LOCK' ? this.t.translate('book.table.toast.lockFailedSummary') : this.t.translate('book.table.toast.unlockFailedSummary'),
          detail: lockAction === 'LOCK' ? this.t.translate('book.table.toast.lockFailedDetail') : this.t.translate('book.table.toast.unlockFailedDetail'),
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
