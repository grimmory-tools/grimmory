import {Component, DestroyRef, inject, Input, OnChanges, OnInit, signal, SimpleChanges} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Router} from '@angular/router';
import {Button} from '@openng/optimus-ui/button';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {MessageService} from '@openng/optimus-ui/api';
import {TranslocoService} from '@jsverse/transloco';
import {BookMark, BookMarkService} from '../../../../shared/service/book-mark.service';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-book-bookmarks',
  standalone: true,
  imports: [
    DatePipe,
    Button,
    ProgressSpinner,
    Tooltip
  ],
  templateUrl: './book-bookmarks.component.html',
  styleUrl: './book-bookmarks.component.scss'
})
export class BookBookmarksComponent implements OnInit, OnChanges {
  @Input() bookId!: number;
  @Input() primaryBookType?: string;

  private bookMarkService = inject(BookMarkService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private readonly t = inject(TranslocoService);

  bookmarks: BookMark[] = [];
  loading = signal(false);

  ngOnInit(): void {
    if (this.bookId) {
      this.loadBookmarks();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookId'] && changes['bookId'].currentValue) {
      this.loadBookmarks();
    }
  }

  loadBookmarks(): void {
    if (!this.bookId) return;

    this.loading.set(true);
    this.bookMarkService.getBookmarksForBook(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (bookmarks) => {
          this.bookmarks = bookmarks.sort((a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          );
          this.loading.set(false);
        },
        error: (error) => {
          console.error('Failed to load bookmarks:', error);
          this.loading.set(false);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: 'Failed to load bookmarks'
          });
        }
      });
  }

  navigateToBookmark(bookmark: BookMark): void {
    const bookType = this.primaryBookType;
    let readerPath: string;

    switch (bookType) {
      case 'PDF':
        readerPath = 'pdf-reader';
        break;
      case 'EPUB':
      case 'FB2':
      case 'MOBI':
      case 'AZW3':
        readerPath = 'ebook-reader';
        break;
      case 'AUDIOBOOK':
        readerPath = 'audiobook-player';
        break;
      default:
        // If we don't know the book type, try to infer from bookmark fields
        if (bookmark.pageNumber !== undefined && bookmark.pageNumber !== null) {
          readerPath = 'pdf-reader';
        } else if (bookmark.cfi) {
          readerPath = 'ebook-reader';
        } else if (bookmark.positionMs !== undefined) {
          readerPath = 'audiobook-player';
        } else {
          return; // Can't determine reader type
        }
    }

    const queryParams: any = {};
    if (bookmark.cfi) {
      queryParams.cfi = bookmark.cfi;
    } else if (bookmark.pageNumber !== undefined && bookmark.pageNumber !== null) {
      queryParams.page = bookmark.pageNumber;
    } else if (bookmark.positionMs !== undefined) {
      queryParams.position = bookmark.positionMs;
    }

    this.router.navigate(
      [`/${readerPath}/book/${this.bookId}`],
      { queryParams: Object.keys(queryParams).length > 0 ? queryParams : undefined }
    );
  }

  deleteBookmark(event: MouseEvent, bookmark: BookMark): void {
    event.stopPropagation(); // Prevent navigation when clicking delete
    this.bookMarkService.deleteBookmark(bookmark.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.bookmarks = this.bookmarks.filter(b => b.id !== bookmark.id);
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: 'Bookmark deleted'
          });
        },
        error: (error) => {
          console.error('Failed to delete bookmark:', error);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: 'Failed to delete bookmark'
          });
        }
      });
  }

  getLocationLabel(bookmark: BookMark): string {
    if (bookmark.pageNumber !== undefined && bookmark.pageNumber !== null) {
      return `Page ${bookmark.pageNumber}`;
    }
    if (bookmark.cfi) {
      return 'EPUB Location';
    }
    if (bookmark.positionMs !== undefined) {
      const seconds = Math.floor(bookmark.positionMs / 1000);
      const minutes = Math.floor(seconds / 60);
      const hrs = Math.floor(minutes / 60);
      if (hrs > 0) {
        return `${hrs}h ${minutes % 60}m`;
      }
      return `${minutes}m ${seconds % 60}s`;
    }
    return 'Saved Position';
  }
}
