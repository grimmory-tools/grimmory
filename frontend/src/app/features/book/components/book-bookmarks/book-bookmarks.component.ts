import {Component, DestroyRef, inject, Input, OnChanges, OnInit, signal, SimpleChanges} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
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

  private bookMarkService = inject(BookMarkService);
  private messageService = inject(MessageService);
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

  deleteBookmark(bookmark: BookMark): void {
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
      return `CFI: ${bookmark.cfi}`;
    }
    return 'Saved Position';
  }
}
