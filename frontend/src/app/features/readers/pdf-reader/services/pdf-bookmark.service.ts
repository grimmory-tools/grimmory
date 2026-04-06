import {Injectable, inject} from '@angular/core';
import {Observable, of} from 'rxjs';
import {catchError, map, tap} from 'rxjs/operators';
import {BookMark, BookMarkService, CreateBookMarkRequest} from '../../../../shared/service/book-mark.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

@Injectable()
export class PdfBookmarkService {
  private bookMarkService = inject(BookMarkService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  private bookmarks = new Map<number, BookMark>();
  private bookId!: number;

  initialize(bookId: number): void {
    this.bookId = bookId;
    this.bookmarks.clear();
  }

  loadBookmarks(): Observable<BookMark[]> {
    return this.bookMarkService.getBookmarksForBook(this.bookId).pipe(
      tap(bookmarks => {
        this.bookmarks.clear();
        for (const bm of bookmarks) {
          if (bm.pageNumber != null) {
            this.bookmarks.set(bm.pageNumber, bm);
          }
        }
      }),
      map(bookmarks => bookmarks.filter(bm => bm.pageNumber != null)),
      catchError(() => {
        this.bookmarks.clear();
        return of([]);
      })
    );
  }

  isPageBookmarked(pageNumber: number): boolean {
    return this.bookmarks.has(pageNumber);
  }

  getBookmarkForPage(pageNumber: number): BookMark | undefined {
    return this.bookmarks.get(pageNumber);
  }

  getAllBookmarks(): BookMark[] {
    return Array.from(this.bookmarks.values()).sort((a, b) => (a.pageNumber ?? 0) - (b.pageNumber ?? 0));
  }

  toggleBookmark(pageNumber: number, title?: string): Observable<boolean> {
    const existing = this.bookmarks.get(pageNumber);
    if (existing) {
      return this.deleteBookmark(existing.id);
    }
    return this.createBookmark(pageNumber, title);
  }

  createBookmark(pageNumber: number, title?: string): Observable<boolean> {
    const request: CreateBookMarkRequest = {
      bookId: this.bookId,
      pageNumber,
      title: title || `Page ${pageNumber}`,
    };

    return this.bookMarkService.createBookmark(request).pipe(
      map(bookmark => {
        this.bookmarks.set(pageNumber, bookmark);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('readerPdf.toast.bookmarkAdded'),
        });
        return true;
      }),
      catchError(error => {
        const isDuplicate = error?.status === 409;
        if (isDuplicate) {
          // Hydrate cache: server already has this bookmark
          this.loadBookmarks().subscribe();
        }
        this.messageService.add(
          isDuplicate
            ? {severity: 'warn', summary: this.t.translate('readerPdf.toast.bookmarkExists')}
            : {severity: 'error', summary: this.t.translate('common.error')}
        );
        return of(false);
      })
    );
  }

  deleteBookmark(bookmarkId: number): Observable<boolean> {
    return this.bookMarkService.deleteBookmark(bookmarkId).pipe(
      map(() => {
        for (const [page, bm] of this.bookmarks) {
          if (bm.id === bookmarkId) {
            this.bookmarks.delete(page);
            break;
          }
        }
        this.messageService.add({
          severity: 'info',
          summary: this.t.translate('readerPdf.toast.bookmarkRemoved'),
        });
        return true;
      }),
      catchError(() => {
        this.messageService.add({severity: 'error', summary: this.t.translate('common.error')});
        return of(false);
      })
    );
  }

  reset(): void {
    this.bookmarks.clear();
  }
}
