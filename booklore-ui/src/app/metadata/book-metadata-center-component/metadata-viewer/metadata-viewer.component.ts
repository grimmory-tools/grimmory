import {Component, DestroyRef, inject, Input, OnChanges, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {Button} from 'primeng/button';
import {AsyncPipe, DecimalPipe, NgClass} from '@angular/common';
import {Observable} from 'rxjs';
import {BookService} from '../../../book/service/book.service';
import {Rating, RatingRateEvent} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {Tag} from 'primeng/tag';
import {Book, BookMetadata, BookRecommendation, FileInfo, ReadStatus} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {UserService} from '../../../settings/user-management/user.service';
import {SplitButton} from 'primeng/splitbutton';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {BookSenderComponent} from '../../../book/components/book-sender/book-sender.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailService} from '../../../settings/email/email.service';
import {ShelfAssignerComponent} from '../../../book/components/shelf-assigner/shelf-assigner.component';
import {Tooltip} from 'primeng/tooltip';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Editor} from 'primeng/editor';
import {ProgressBar} from 'primeng/progressbar';
import {MetadataFetchOptionsComponent} from '../../metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshRequest} from '../../model/request/metadata-refresh-request.model';
import {Router} from '@angular/router';
import {filter, map, switchMap, take, tap} from 'rxjs/operators';
import {Menu} from 'primeng/menu';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';
import {BookCardLiteComponent} from '../../../book/components/book-card-lite/book-card-lite-component';
import {ResetProgressType, ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {DatePicker} from 'primeng/datepicker';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {BookReviewsComponent} from '../../../book/components/book-reviews/book-reviews.component';
import {BookNotesComponent} from '../../../book/components/book-notes-component/book-notes-component';
import {ProgressSpinner} from 'primeng/progressspinner';

import {TieredMenu} from 'primeng/tieredmenu';
import {AdditionalFileUploaderComponent} from '../../../book/components/additional-file-uploader/additional-file-uploader.component';
import {Image} from 'primeng/image';

@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  templateUrl: './metadata-viewer.component.html',
  styleUrl: './metadata-viewer.component.scss',
  imports: [Button, AsyncPipe, Rating, FormsModule, Tag, SplitButton, NgClass, Tooltip, DecimalPipe, Editor, ProgressBar, Menu, InfiniteScrollDirective, BookCardLiteComponent, DatePicker, Tab, TabList, TabPanel, TabPanels, Tabs, BookReviewsComponent, BookNotesComponent, ProgressSpinner, TieredMenu, Image]
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
  private confirmationService = inject(ConfirmationService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private dialogRef?: DynamicDialogRef;

  emailMenuItems$!: Observable<MenuItem[]>;
  readMenuItems$!: Observable<MenuItem[]>;
  refreshMenuItems$!: Observable<MenuItem[]>;
  otherItems$!: Observable<MenuItem[]>;
  downloadMenuItems$!: Observable<MenuItem[]>;
  bookInSeries: Book[] = [];
  isExpanded = false;
  showFilePath = false;
  isAutoFetching = false;
  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  selectedReadStatus: ReadStatus = ReadStatus.UNREAD;
  isEditingDateFinished = false;
  editDateFinished: Date | null = null;

  readStatusOptions: { value: ReadStatus, label: string }[] = [
    {value: ReadStatus.UNREAD, label: 'Unread'},
    {value: ReadStatus.PAUSED, label: 'Paused'},
    {value: ReadStatus.READING, label: 'Reading'},
    {value: ReadStatus.RE_READING, label: 'Re-reading'},
    {value: ReadStatus.READ, label: 'Read'},
    {value: ReadStatus.PARTIALLY_READ, label: 'Partially Read'},
    {value: ReadStatus.ABANDONED, label: 'Abandoned'},
    {value: ReadStatus.WONT_READ, label: 'Won\'t Read'},
    {value: ReadStatus.UNSET, label: 'Unset'},
  ];

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

    this.downloadMenuItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null &&
        ((book.alternativeFormats !== undefined && book.alternativeFormats.length > 0) ||
          (book.supplementaryFiles !== undefined && book.supplementaryFiles.length > 0))),
      map((book): MenuItem[] => {
        const items: MenuItem[] = [];

        // Add alternative formats
        if (book.alternativeFormats && book.alternativeFormats.length > 0) {
          book.alternativeFormats.forEach(format => {
            const extension = this.getFileExtension(format.filePath);
            items.push({
              label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
              icon: this.getFileIcon(extension),
              command: () => this.downloadAdditionalFile(book.id, format.id)
            });
          });
        }

        // Add separator if both types exist
        if (book.alternativeFormats && book.alternativeFormats.length > 0 &&
          book.supplementaryFiles && book.supplementaryFiles.length > 0) {
          items.push({separator: true});
        }

        // Add supplementary files
        if (book.supplementaryFiles && book.supplementaryFiles.length > 0) {
          book.supplementaryFiles.forEach(file => {
            const extension = this.getFileExtension(file.filePath);
            items.push({
              label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
              icon: this.getFileIcon(extension),
              command: () => this.downloadAdditionalFile(book.id, file.id)
            });
          });
        }

        return items;
      })
    );

    this.otherItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null),
      map((book): MenuItem[] => {
        const items: MenuItem[] = [
          {
            label: 'Upload File',
            icon: 'pi pi-upload',
            command: () => {
              this.dialogService.open(AdditionalFileUploaderComponent, {
                header: 'Upload Additional File',
                modal: true,
                closable: true,
                style: {
                  position: 'absolute',
                  top: '10%',
                },
                data: {book}
              });
            },
          },
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
          },
        ];

        // Add delete additional files menu if there are any additional files
        if ((book.alternativeFormats && book.alternativeFormats.length > 0) ||
          (book.supplementaryFiles && book.supplementaryFiles.length > 0)) {
          const deleteFileItems: MenuItem[] = [];

          // Add alternative formats
          if (book.alternativeFormats && book.alternativeFormats.length > 0) {
            book.alternativeFormats.forEach(format => {
              const extension = this.getFileExtension(format.filePath);
              deleteFileItems.push({
                label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
                icon: this.getFileIcon(extension),
                command: () => this.deleteAdditionalFile(book.id, format.id, format.fileName || 'file')
              });
            });
          }

          // Add separator if both types exist
          if (book.alternativeFormats && book.alternativeFormats.length > 0 &&
            book.supplementaryFiles && book.supplementaryFiles.length > 0) {
            deleteFileItems.push({separator: true});
          }

          // Add supplementary files
          if (book.supplementaryFiles && book.supplementaryFiles.length > 0) {
            book.supplementaryFiles.forEach(file => {
              const extension = this.getFileExtension(file.filePath);
              deleteFileItems.push({
                label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
                icon: this.getFileIcon(extension),
                command: () => this.deleteAdditionalFile(book.id, file.id, file.fileName || 'file')
              });
            });
          }

          items.push({
            label: 'Delete Additional Files',
            icon: 'pi pi-trash',
            items: deleteFileItems
          });
        }

        return items;
      })
    );

    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1)
      )
      .subscribe(userState => {
        this.metadataCenterViewMode = userState.user?.userSettings.metadataCenterViewMode ?? 'route';
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
      this.withCurrentBook(book => this.filterRecommendations(book));
    }
  }

  private withCurrentBook(callback: (book: Book | null) => void): void {
    this.book$.pipe(take(1)).subscribe(callback);
  }

  private loadBooksInSeriesAndFilterRecommended(bookId: number): void {
    this.bookService.getBooksInSeries(bookId).pipe(
      tap(series => {
        series.sort((a, b) => (a.metadata?.seriesNumber ?? 0) - (b.metadata?.seriesNumber ?? 0));
        this.bookInSeries = series;
        this.originalRecommendedBooks = [...this.recommendedBooks];
      }),
      switchMap(() => this.book$.pipe(take(1))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(book => this.filterRecommendations(book));
  }

  private filterRecommendations(book: Book | null): void {
    if (!this.originalRecommendedBooks) return;
    const bookInSeriesIds = new Set(this.bookInSeries.map(book => book.id));
    this.recommendedBooks = this.originalRecommendedBooks.filter(
      rec => !bookInSeriesIds.has(rec.book.id)
    );
  }

  get defaultTabValue(): number {
    return this.bookInSeries && this.bookInSeries.length > 0 ? 1 : 2;
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

  downloadAdditionalFile(bookId: number, fileId: number) {
    this.bookService.downloadAdditionalFile(bookId, fileId);
  }

  deleteAdditionalFile(bookId: number, fileId: number, fileName: string) {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete the additional file "${fileName}"?`,
      header: 'Confirm File Deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.deleteAdditionalFile(bookId, fileId).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Success',
              detail: `Additional file "${fileName}" deleted successfully`
            });
          },
          error: (error) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Error',
              detail: `Failed to delete additional file: ${error.message || 'Unknown error'}`
            });
          }
        });
      }
    });
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

  updateReadStatus(status: ReadStatus): void {
    if (!status) {
      return;
    }

    this.book$.pipe(take(1)).subscribe(book => {
      if (!book || !book.id) {
        return;
      }

      this.bookService.updateBookReadStatus(book.id, status).subscribe({
        next: (updatedBooks) => {
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

  resetProgress(book: Book, type: ResetProgressType): void {
    this.confirmationService.confirm({
      message: `Reset reading progress for "${book.metadata?.title}"?`,
      header: 'Confirm Reset',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Yes',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.resetProgress(book.id, type).subscribe({
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

  resetPersonalRating(book: Book): void {
    if (!book?.metadata) return;
    const updatedMetadata = {...book.metadata, personalRating: null};
    this.bookService.updateBookMetadata(book.id, {
      metadata: updatedMetadata,
      clearFlags: {personalRating: true}
    }, false).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'info',
          summary: 'Rating Reset',
          detail: 'Personal rating has been cleared.'
        });
      },
      error: err => {
        console.error('Failed to reset personal rating:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Reset Failed',
          detail: 'Could not reset personal rating'
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
    this.router.navigate(['/series', seriesName]);
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

  isMetadataFullyLocked(metadata: BookMetadata): boolean {
    const lockedKeys = Object.keys(metadata).filter(k => k.endsWith('Locked'));
    return lockedKeys.length > 0 && lockedKeys.every(k => metadata[k] === true);
  }

  getFileSizeInMB(fileInfo: FileInfo | null | undefined): string {
    const sizeKb = fileInfo?.fileSizeKb;
    return sizeKb != null ? `${(sizeKb / 1024).toFixed(2)} MB` : '-';
  }

  getProgressPercent(book: Book): number | null {
    if (book.epubProgress?.percentage != null) {
      return book.epubProgress.percentage;
    }
    if (book.pdfProgress?.percentage != null) {
      return book.pdfProgress.percentage;
    }
    if (book.cbxProgress?.percentage != null) {
      return book.cbxProgress.percentage;
    }
    return null;
  }

  getKoProgressPercent(book: Book): number | null {
    if (book.koreaderProgress?.percentage != null) {
      return book.koreaderProgress.percentage;
    }
    return null;
  }

  getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  getFileIcon(fileType: string | null): string {
    if (!fileType) return 'pi pi-file';
    switch (fileType.toLowerCase()) {
      case 'pdf':
        return 'pi pi-file-pdf';
      case 'epub':
      case 'mobi':
      case 'azw3':
        return 'pi pi-book';
      case 'cbz':
      case 'cbr':
      case 'cbx':
        return 'pi pi-image';
      default:
        return 'pi pi-file';
    }
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

  getKoProgressColorClass(progress: number | null | undefined): string {
    if (progress == null) return 'bg-gray-600';
    return 'bg-amber-500';
  }

  getKOReaderPercentage(book: Book): number | null {
    const p = book?.koreaderProgress?.percentage;
    return p != null ? Math.round(p * 10) / 10 : null;
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


  formatDate(dateString: string | undefined): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  toggleDateFinishedEdit(book: Book): void {
    if (this.isEditingDateFinished) {
      this.isEditingDateFinished = false;
      this.editDateFinished = null;
    } else {
      this.isEditingDateFinished = true;
      this.editDateFinished = book.dateFinished ? new Date(book.dateFinished) : new Date();
    }
  }

  saveDateFinished(book: Book): void {
    if (!book) return;

    const dateToSave = this.editDateFinished ? this.editDateFinished.toISOString() : null;

    this.bookService.updateDateFinished(book.id, dateToSave).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Date Updated',
          detail: 'Book finish date has been updated.',
          life: 1500
        });
        this.isEditingDateFinished = false;
        this.editDateFinished = null;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Update Failed',
          detail: 'Could not update book finish date.',
          life: 3000
        });
      }
    });
  }

  cancelDateFinishedEdit(): void {
    this.isEditingDateFinished = false;
    this.editDateFinished = null;
  }

  protected readonly ResetProgressTypes = ResetProgressTypes;
  protected readonly ReadStatus = ReadStatus;
}
