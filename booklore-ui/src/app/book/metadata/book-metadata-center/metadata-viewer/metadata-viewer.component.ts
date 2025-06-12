import {Component, DestroyRef, inject, Input, OnChanges, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {Button, ButtonDirective} from 'primeng/button';
import {AsyncPipe, DecimalPipe, NgClass} from '@angular/common';
import {first, Observable} from 'rxjs';
import {BookService} from '../../../service/book.service';
import {BookMetadataCenterService} from '../book-metadata-center.service';
import {Rating} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {Tag} from 'primeng/tag';
import {Book, BookMetadata, BookRecommendation} from '../../../model/book.model';
import {Divider} from 'primeng/divider';
import {UrlHelperService} from '../../../../utilities/service/url-helper.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {SplitButton} from 'primeng/splitbutton';
import {MenuItem, MessageService} from 'primeng/api';
import {BookSenderComponent} from '../../../components/book-sender/book-sender.component';
import {DialogService} from 'primeng/dynamicdialog';
import {EmailService} from '../../../../settings/email/email.service';
import {ShelfAssignerComponent} from '../../../components/shelf-assigner/shelf-assigner.component';
import {Tooltip} from 'primeng/tooltip';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';
import {BookCardComponent} from '../../../components/book-browser/book-card/book-card.component';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Editor} from 'primeng/editor';
import {ProgressBar} from 'primeng/progressbar';
import {ToggleButton} from 'primeng/togglebutton';
import {MetadataFetchOptionsComponent} from '../../metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshRequest} from '../../model/request/metadata-refresh-request.model';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  templateUrl: './metadata-viewer.component.html',
  styleUrl: './metadata-viewer.component.scss',
  imports: [Button, AsyncPipe, Rating, FormsModule, Tag, Divider, SplitButton, NgClass, Tooltip, DecimalPipe, InfiniteScrollDirective, BookCardComponent, ButtonDirective, Editor, ProgressBar, ToggleButton, RouterLink]
})
export class MetadataViewerComponent implements OnInit, OnChanges {

  @Input() book: Book | undefined;
  @Input() recommendedBooks: BookRecommendation[] = [];
  private originalRecommendedBooks: BookRecommendation[] = [];

  @ViewChild(Editor) quillEditor!: Editor;

  private dialogService = inject(DialogService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private metadataCenterService = inject(BookMetadataCenterService);
  protected urlHelper = inject(UrlHelperService);
  protected userService = inject(UserService);
  private destroyRef = inject(DestroyRef);

  metadata$: Observable<BookMetadata | null> = this.metadataCenterService.currentMetadata$;
  emailMenuItems: MenuItem[] | undefined;
  readMenuItems: MenuItem[] | undefined;
  refreshMenuItems: MenuItem[] | undefined;
  bookInSeries: Book[] = [];
  isExpanded = false;
  showFilePath = false;

  ngOnInit(): void {
    this.emailMenuItems = [
      {
        label: 'Custom Send',
        command: () => {
          this.metadata$.pipe(first()).subscribe((metadata) => {
            if (metadata) {
              this.dialogService.open(BookSenderComponent, {
                header: 'Send Book to Email',
                modal: true,
                closable: true,
                style: {
                  position: 'absolute',
                  top: '15%',
                },
                data: {
                  bookId: metadata.bookId,
                }
              });
            }
          });
        }
      }
    ];

    this.refreshMenuItems = [
      {
        label: 'Granular Refresh',
        icon: 'pi pi-database',
        command: () => {
          this.dialogService.open(MetadataFetchOptionsComponent, {
            header: 'Metadata Refresh Options',
            modal: true,
            closable: true,
            data: {
              bookIds: [this.book!.id],
              metadataRefreshType: MetadataRefreshType.BOOKS,
            },
          });
        },
      }
    ];

    this.readMenuItems = [
      {
        label: 'Streaming Reader',
        command: () => {
          this.read(this.book?.id, "streaming")
        },
      }
    ];

    this.metadata$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((metadata) => {
        if (metadata) {
          this.loadBooksInSeriesAndFilterRecommended(metadata.bookId);
          if (this.quillEditor && this.quillEditor.quill) {
            this.quillEditor.quill.root.innerHTML = metadata.description;
          }
        }
      });
  }

  private loadBooksInSeriesAndFilterRecommended(bookId: number): void {
    this.bookService.getBooksInSeries(bookId).subscribe((bookInSeries) => {
      bookInSeries.sort((a, b) => (a.metadata?.seriesNumber ?? 0) - (b.metadata?.seriesNumber ?? 0));
      this.bookInSeries = bookInSeries;
      this.originalRecommendedBooks = [...this.recommendedBooks];
      this.filterRecommendations();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['recommendedBooks']) {
      this.originalRecommendedBooks = [...this.recommendedBooks];
      this.filterRecommendations();
    }
  }

  private filterRecommendations(): void {
    if (!this.originalRecommendedBooks) {
      return;
    }
    const bookInSeriesIds = new Set(this.bookInSeries.map(book => book.id));
    this.recommendedBooks = this.originalRecommendedBooks.filter(
      rec => !bookInSeriesIds.has(rec.book.id)
    );
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  read(bookId: number | undefined, reader: "ngx" | "streaming" | undefined): void {
    this.bookService.readBook(bookId!, reader);
  }

  download(bookId: number) {
    this.bookService.downloadFile(bookId);
  }

  quickRefresh(bookId: number) {
    const metadataRefreshRequest: MetadataRefreshRequest = {
      quick: true,
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [bookId],
    };
    this.bookService.autoRefreshMetadata(metadataRefreshRequest).subscribe();
  }

  quickSend(bookId: number) {
    this.emailService.emailBookQuick(bookId).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'info',
          summary: 'Success',
          detail: 'The book sending has been scheduled.',
        });
      },
      error: (err) => {
        const errorMessage = err?.error?.message || 'An error occurred while sending the book.';
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: errorMessage,
        });
      },
    });
  }

  assignShelf(bookId: number) {
    this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Book's Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {
        position: 'absolute',
        top: '15%',
      },
      data: {
        book: this.bookService.getBookByIdFromState(bookId),
      },
    });
  }

  isMetadataFullyLocked(metadata: BookMetadata): boolean {
    const lockedKeys = Object.keys(metadata).filter(key => key.endsWith('Locked'));
    if (lockedKeys.length === 0) {
      return false;
    }
    return lockedKeys.every(key => metadata[key] === true);
  }

  getFileSizeInMB(): string {
    const sizeKb = this.book?.fileSizeKb;
    if (sizeKb != null) {
      const sizeMb = sizeKb / 1024;
      return `${sizeMb.toFixed(2)} MB`;
    }
    return '-';
  }

  getProgressPercent(): number | undefined {
    if (this.book?.bookType === 'PDF') {
      return this.book.pdfProgress?.percentage;
    } else if (this.book?.bookType === 'CBX') {
      return this.book?.cbxProgress?.percentage;
    } else {
      return this.book?.epubProgress?.percentage;
    }
  }

  copyFilePath() {
    if (this.book?.filePath) {
      navigator.clipboard.writeText(this.book.filePath).then(() => {
        this.messageService.add({severity: 'success', summary: 'Copied', detail: 'File path copied to clipboard.'});
      }, () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to copy file path.'});
      });
    }
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

  getStarColor(rating?: number | null): string {
    if (rating == null) {
      return 'rgb(203, 213, 225)';
    } else if (rating >= 4.5) {
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

  getMatchScoreColorClass(score: number): string {
    if (score >= 0.95) return 'bg-green-600 border-green-700';
    if (score >= 0.90) return 'bg-green-500 border-green-600';
    if (score >= 0.80) return 'bg-green-400 border-green-500';
    if (score >= 0.70) return 'bg-yellow-500 border-yellow-600';
    if (score >= 0.50) return 'bg-yellow-400 border-yellow-500';
    if (score >= 0.30) return 'bg-red-500 border-red-600';
    return 'bg-red-600 border-red-700';
  }
}
