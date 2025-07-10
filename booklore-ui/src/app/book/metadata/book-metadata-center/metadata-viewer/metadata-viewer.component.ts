import {Component, DestroyRef, inject, Input, OnChanges, OnInit, Optional, SimpleChanges, ViewChild} from '@angular/core';
import {Button} from 'primeng/button';
import {AsyncPipe, DecimalPipe, NgClass} from '@angular/common';
import {Observable} from 'rxjs';
import {BookService} from '../../../service/book.service';
import {Rating, RatingRateEvent} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {Tag} from 'primeng/tag';
import {Book, BookMetadata, BookRecommendation, ReadStatus} from '../../../model/book.model';
import {Divider} from 'primeng/divider';
import {UrlHelperService} from '../../../../utilities/service/url-helper.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {SplitButton} from 'primeng/splitbutton';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {BookSenderComponent} from '../../../components/book-sender/book-sender.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailService} from '../../../../settings/email/email.service';
import {ShelfAssignerComponent} from '../../../components/shelf-assigner/shelf-assigner.component';
import {Tooltip} from 'primeng/tooltip';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Editor} from 'primeng/editor';
import {ProgressBar} from 'primeng/progressbar';
import {MetadataFetchOptionsComponent} from '../../metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshRequest} from '../../model/request/metadata-refresh-request.model';
import {Router} from '@angular/router';
import {filter, map, take} from 'rxjs/operators';
import {Menu} from 'primeng/menu';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';
import {BookCardLiteComponent} from '../../../components/book-card-lite/book-card-lite-component';

@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  templateUrl: './metadata-viewer.component.html',
  styleUrl: './metadata-viewer.component.scss',
  imports: [Button, AsyncPipe, Rating, FormsModule, Tag, Divider, SplitButton, NgClass, Tooltip, DecimalPipe, Editor, ProgressBar, Menu, InfiniteScrollDirective, BookCardLiteComponent]
})
export class MetadataViewerComponent implements OnInit, OnChanges {

  @Input() book$!: Observable<Book | null>;
  @Input() recommendedBooks: BookRecommendation[] = [];
  @ViewChild(Editor) quillEditor!: Editor;
  private originalRecommendedBooks: BookRecommendation[] = [];

  private dialogService = inject(DialogService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  protected urlHelper = inject(UrlHelperService);
  protected userService = inject(UserService);
  private destroyRef = inject(DestroyRef);
  private confirmationService = inject(ConfirmationService);
  private router = inject(Router);

  private dialogRef?: DynamicDialogRef;

  constructor(@Optional() dialogRef?: DynamicDialogRef) {
    this.dialogRef = dialogRef;
  }

  emailMenuItems$!: Observable<MenuItem[]>;
  readMenuItems$!: Observable<MenuItem[]>;
  refreshMenuItems$!: Observable<MenuItem[]>;
  otherItems$!: Observable<MenuItem[]>;
  bookInSeries: Book[] = [];

  isExpanded = false;
  showFilePath = false;
  isAutoFetching = false;
  private metadataCenterViewMode: 'route' | 'dialog' = 'route';

  readStatusOptions = [
    {label: 'Unread', value: ReadStatus.UNREAD},
    {label: 'Reading', value: ReadStatus.READING},
    {label: 'Re-reading', value: ReadStatus.RE_READING},
    {label: 'Partially Read', value: ReadStatus.PARTIALLY_READ},
    {label: 'Paused', value: ReadStatus.PAUSED},
    {label: 'Read', value: ReadStatus.READ},
    {label: 'Won’t Read', value: ReadStatus.WONT_READ},
    {label: 'Abandoned', value: ReadStatus.ABANDONED}
  ];

  selectedReadStatus: ReadStatus = ReadStatus.UNREAD;

  ngOnInit(): void {
    this.emailMenuItems$ = this.book$.pipe(
      map(book => book?.metadata ?? null),
      filter((metadata): metadata is BookMetadata => metadata != null),
      map((metadata): MenuItem[] => [
        {
          label: 'Custom Send',
          command: () => {
            this.dialogService.open(BookSenderComponent, {
              header: 'Send Book to Email',
              modal: true,
              closable: true,
              style: {position: 'absolute', top: '20%'},
              data: {bookId: metadata.bookId}
            });
          }
        }
      ])
    );

    this.refreshMenuItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null),
      map((book): MenuItem[] => [
        {
          label: 'Granular Refresh',
          icon: 'pi pi-database',
          command: () => {
            this.dialogService.open(MetadataFetchOptionsComponent, {
              header: 'Metadata Refresh Options',
              modal: true,
              closable: true,
              data: {
                bookIds: [book.id],
                metadataRefreshType: MetadataRefreshType.BOOKS,
              },
            });
          }
        }
      ])
    );

    this.readMenuItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null),
      map((book): MenuItem[] => [
        {
          label: 'Streaming Reader',
          command: () => this.read(book.id, 'streaming')
        }
      ])
    );

    this.otherItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null),
      map((book): MenuItem[] => [
        {
          label: 'Delete Book',
          icon: 'pi pi-trash',
          command: () => {
            this.confirmationService.confirm({
              message: `Are you sure you want to delete "${book.metadata?.title}"?`,
              header: 'Confirm Deletion',
              icon: 'pi pi-exclamation-triangle',
              acceptIcon: 'pi pi-trash',
              rejectIcon: 'pi pi-times',
              acceptButtonStyleClass: 'p-button-danger',
              accept: () => {
                this.bookService.deleteBooks(new Set([book.id])).subscribe({
                  next: () => {
                    if (this.metadataCenterViewMode === 'route') {
                      this.router.navigate(['/dashboard']);
                    } else {
                      this.dialogRef?.close();
                    }
                  },
                  error: () => {
                  }
                });
              }
            });
          },
        }
      ])
    );

    this.userService.userState$
      .pipe(
        filter(user => !!user),
        take(1)
      )
      .subscribe(user => {
        this.metadataCenterViewMode = user?.userSettings.metadataCenterViewMode ?? 'route';
      });

    this.book$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        filter((book): book is Book => book != null && book.metadata != null)
      )
      .subscribe(book => {
        const metadata = book.metadata;
        this.isAutoFetching = false;
        this.loadBooksInSeriesAndFilterRecommended(metadata!.bookId);
        if (this.quillEditor?.quill) {
          this.quillEditor.quill.root.innerHTML = metadata!.description;
        }
        this.selectedReadStatus = book.readStatus ?? ReadStatus.UNREAD;
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['recommendedBooks']) {
      this.originalRecommendedBooks = [...this.recommendedBooks];
      this.book$.pipe(take(1)).subscribe(book => this.filterRecommendations(book));
    }
  }

  private loadBooksInSeriesAndFilterRecommended(bookId: number): void {
    this.bookService.getBooksInSeries(bookId).subscribe(bookInSeries => {
      bookInSeries.sort((a, b) => (a.metadata?.seriesNumber ?? 0) - (b.metadata?.seriesNumber ?? 0));
      this.bookInSeries = bookInSeries;
      this.originalRecommendedBooks = [...this.recommendedBooks];
      this.book$.pipe(take(1)).subscribe(book => this.filterRecommendations(book));
    });
  }

  private filterRecommendations(book: Book | null): void {
    if (!this.originalRecommendedBooks) return;
    const bookInSeriesIds = new Set(this.bookInSeries.map(book => book.id));
    this.recommendedBooks = this.originalRecommendedBooks.filter(
      rec => !bookInSeriesIds.has(rec.book.id)
    );
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  read(bookId: number | undefined, reader: "ngx" | "streaming" | undefined): void {
    if (bookId) this.bookService.readBook(bookId, reader);
  }

  download(bookId: number) {
    this.bookService.downloadFile(bookId);
  }

  quickRefresh(bookId: number) {
    this.isAutoFetching = true;
    const request: MetadataRefreshRequest = {
      quick: true,
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [bookId],
    };
    this.bookService.autoRefreshMetadata(request).subscribe();
    setTimeout(() => {
      this.isAutoFetching = false;
    }, 15000);
  }

  quickSend(bookId: number) {
    this.emailService.emailBookQuick(bookId).subscribe({
      next: () => this.messageService.add({
        severity: 'info',
        summary: 'Success',
        detail: 'The book sending has been scheduled.',
      }),
      error: (err) => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: err?.error?.message || 'An error occurred while sending the book.',
      })
    });
  }

  assignShelf(bookId: number) {
    this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Book's Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {position: 'absolute', top: '15%'},
      data: {book: this.bookService.getBookByIdFromState(bookId)}
    });
  }

  isMetadataFullyLocked(metadata: BookMetadata): boolean {
    const lockedKeys = Object.keys(metadata).filter(k => k.endsWith('Locked'));
    return lockedKeys.length > 0 && lockedKeys.every(k => metadata[k] === true);
  }

  getFileSizeInMB(book: Book | null): string {
    const sizeKb = book?.fileSizeKb;
    return sizeKb != null ? `${(sizeKb / 1024).toFixed(2)} MB` : '-';
  }

  getProgressPercent(book: Book | null): number | undefined {
    if (!book) return;
    return book.bookType === 'PDF' ? book.pdfProgress?.percentage
      : book.bookType === 'CBX' ? book.cbxProgress?.percentage
        : book.epubProgress?.percentage;
  }

  getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  getFileTypeColorClass(fileType: string | null | undefined): string {
    if (!fileType) return 'bg-gray-600 text-white';
    switch (fileType.toLowerCase()) {
      case 'pdf':
        return 'bg-pink-700 text-white';
      case 'epub':
        return 'bg-indigo-600 text-white';
      case 'cbz':
        return 'bg-teal-600 text-white';
      case 'cbr':
        return 'bg-purple-700 text-white';
      case 'cb7':
        return 'bg-blue-700 text-white';
      default:
        return 'bg-gray-600 text-white';
    }
  }

  getStarColorScaled(rating?: number | null, maxScale: number = 5): string {
    if (rating == null) {
      return 'rgb(203, 213, 225)';
    }
    const normalized = rating / maxScale;
    if (normalized >= 0.9) {
      return 'rgb(34, 197, 94)';
    } else if (normalized >= 0.75) {
      return 'rgb(52, 211, 153)';
    } else if (normalized >= 0.6) {
      return 'rgb(234, 179, 8)';
    } else if (normalized >= 0.4) {
      return 'rgb(249, 115, 22)';
    } else {
      return 'rgb(239, 68, 68)';
    }
  }

  getMatchScoreColorClass(score: number): string {
    if (score >= 0.95) return 'bg-green-800 border-green-900';
    if (score >= 0.90) return 'bg-green-700 border-green-800';
    if (score >= 0.80) return 'bg-green-600 border-green-700';
    if (score >= 0.70) return 'bg-yellow-600 border-yellow-700';
    if (score >= 0.60) return 'bg-yellow-500 border-yellow-600';
    if (score >= 0.50) return 'bg-yellow-400 border-yellow-500';
    if (score >= 0.40) return 'bg-red-400 border-red-500';
    if (score >= 0.30) return 'bg-red-500 border-red-600';
    return 'bg-red-600 border-red-700';
  }

  getStatusSeverityClass(status: string): string {
    const normalized = status?.toUpperCase();
    switch (normalized) {
      case 'UNREAD':
        return 'bg-gray-500';
      case 'PAUSED':
        return 'bg-zinc-600';
      case 'READING':
        return 'bg-blue-600';
      case 'RE_READING':
        return 'bg-indigo-600';
      case 'READ':
        return 'bg-green-600';
      case 'PARTIALLY_READ':
        return 'bg-yellow-600';
      case 'ABANDONED':
        return 'bg-red-600';
      case 'WONT_READ':
        return 'bg-pink-700';
      default:
        return 'bg-gray-600';
    }
  }

  getProgressColorClass(progress: number | null | undefined): string {
    if (progress == null) return 'bg-gray-600';
    return 'bg-blue-500';
  }

  onPersonalRatingChange(book: Book, {value: personalRating}: RatingRateEvent): void {
    if (!book?.metadata) return;

    const updatedMetadata = {...book.metadata, personalRating};

    this.bookService.updateBookMetadata(book.id, {
      metadata: updatedMetadata,
      clearFlags: {personalRating: false}
    }, false).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Rating Saved',
          detail: 'Personal rating updated successfully'
        });
      },
      error: err => {
        console.error('Failed to update personal rating:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Update Failed',
          detail: 'Could not update personal rating'
        });
      }
    });
  }

  goToAuthorBooks(author: string): void {
    this.handleMetadataClick('author', author);
  }

  goToCategory(category: string): void {
    this.handleMetadataClick('category', category);
  }

  goToSeries(seriesName: string): void {
    this.handleMetadataClick('series', seriesName);
  }

  goToPublisher(publisher: string): void {
    this.handleMetadataClick('publisher', publisher);
  }

  private navigateToFilteredBooks(filterKey: string, filterValue: string): void {
    this.router.navigate(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `${filterKey}:${filterValue}`
      }
    });
  }

  private handleMetadataClick(filterKey: string, filterValue: string): void {
    if (this.metadataCenterViewMode === 'dialog') {
      this.dialogRef?.close();
      setTimeout(() => this.navigateToFilteredBooks(filterKey, filterValue), 200);
    } else {
      this.navigateToFilteredBooks(filterKey, filterValue);
    }
  }

  getRatingTooltip(book: Book, source: 'amazon' | 'goodreads' | 'hardcover'): string {
    const meta = book?.metadata;
    if (!meta) return '';

    switch (source) {
      case 'amazon':
        return meta.amazonRating != null
          ? `★ ${meta.amazonRating} | ${meta.amazonReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      case 'goodreads':
        return meta.goodreadsRating != null
          ? `★ ${meta.goodreadsRating} | ${meta.goodreadsReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      case 'hardcover':
        return meta.hardcoverRating != null
          ? `★ ${meta.hardcoverRating} | ${meta.hardcoverReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      default:
        return '';
    }
  }

  getRatingPercent(rating: number | null | undefined): number {
    if (rating == null) return 0;
    return Math.round((rating / 5) * 100);
  }

  readStatusMenuItems = this.readStatusOptions.map(option => ({
    label: option.label,
    command: () => this.updateReadStatus(option.value)
  }));

  getStatusLabel(value: string): string {
    return this.readStatusOptions.find(o => o.value === value)?.label.toUpperCase() ?? 'UNSET';
  }

  updateReadStatus(status: ReadStatus): void {
    if (!status) {
      return;
    }

    this.book$.pipe(take(1)).subscribe(book => {
      if (!book || !book.id) {
        return;
      }

      this.bookService.updateBookReadStatus(book.id, status).subscribe({
        next: () => {
          this.selectedReadStatus = status;
          this.messageService.add({
            severity: 'success',
            summary: 'Read Status Updated',
            detail: `Marked as "${this.getStatusLabel(status)}"`,
            life: 2000
          });
        },
        error: (err) => {
          console.error('Failed to update read status:', err);
          this.messageService.add({
            severity: 'error',
            summary: 'Update Failed',
            detail: 'Could not update read status.',
            life: 3000
          });
        }
      });
    });
  }

  resetProgress(book: Book): void {
    this.confirmationService.confirm({
      message: `Reset reading progress for "${book.metadata?.title}"?`,
      header: 'Confirm Reset',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Yes',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.resetProgress(book.id).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Progress Reset',
              detail: 'Reading progress has been reset.',
              life: 1500
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: 'Failed',
              detail: 'Could not reset progress.',
              life: 1500
            });
          }
        });
      }
    });
  }
}
