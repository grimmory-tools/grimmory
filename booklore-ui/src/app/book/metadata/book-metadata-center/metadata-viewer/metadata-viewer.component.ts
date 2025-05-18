import {Component, DestroyRef, inject, Input, OnInit, ViewChild} from '@angular/core';
import {Button, ButtonDirective} from 'primeng/button';
import {AsyncPipe, DecimalPipe, NgClass, NgForOf, NgIf} from '@angular/common';
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

@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  templateUrl: './metadata-viewer.component.html',
  styleUrl: './metadata-viewer.component.scss',
  imports: [Button, NgForOf, NgIf, AsyncPipe, Rating, FormsModule, Tag, Divider, SplitButton, NgClass, Tooltip, DecimalPipe, InfiniteScrollDirective, BookCardComponent, ButtonDirective, Editor, ProgressBar]
})
export class MetadataViewerComponent implements OnInit {

  @Input() book: Book | undefined;
  @Input() recommendedBooks: BookRecommendation[] = [];

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
  items: MenuItem[] | undefined;
  bookInSeries: Book[] = [];
  isExpanded = false;

  ngOnInit(): void {
    this.items = [
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

    this.metadata$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((metadata) => {
        if (metadata) {
          this.getBooksInSeries(metadata.bookId);
          if (this.quillEditor && this.quillEditor.quill) {
            this.quillEditor.quill.root.innerHTML = metadata.description;
          }
        }
      });
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  getBooksInSeries(bookId: number): void {
    this.bookService.getBooksInSeries(bookId).subscribe((bookInSeries) => {
      this.bookInSeries = bookInSeries;
    });
  }

  read(bookId: number): void {
    this.bookService.readBook(bookId);
  }

  getAuthorNames(authors: string[]): string {
    return authors.join(', ');
  }

  download(bookId: number) {
    this.bookService.downloadFile(bookId);
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
    } else {
      return this.book?.epubProgress?.percentage;
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
}
