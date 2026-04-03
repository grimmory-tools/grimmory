import {inject, Injectable, signal} from '@angular/core';
import {finalize} from 'rxjs';
import {MessageService} from 'primeng/api';

import {Book} from '../../model/book.model';
import {BookService} from '../../service/book.service';
import {LoadingService} from '../../../../core/services/loading.service';
import {TranslocoService} from '@jsverse/transloco';

@Injectable({
  providedIn: 'root'
})
export class BookShelfDragDropService {
  private static readonly ACTIVE_DROP_TARGET_CLASS = 'drop-target-active';

  private readonly bookService = inject(BookService);
  private readonly loadingService = inject(LoadingService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  readonly draggedBook = signal<Book | null>(null);
  private activeDropTarget: HTMLElement | null = null;

  startDrag(event: DragEvent, book: Book): void {
    this.draggedBook.set(book);

    if (!event.dataTransfer) {
      return;
    }

    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(book.id));
  }

  endDrag(): void {
    this.clearActiveDropTarget();
    this.draggedBook.set(null);
  }

  canDropOnShelf(shelfId?: number | null): boolean {
    const book = this.draggedBook();
    if (!book || shelfId == null) {
      return false;
    }

    const currentShelfIds = (book.shelves ?? [])
      .map(shelf => shelf.id)
      .filter((id): id is number => id != null);

    return !(currentShelfIds.length === 1 && currentShelfIds[0] === shelfId);
  }

  onShelfDragEnter(element: HTMLElement, shelfId?: number | null): void {
    if (!this.canDropOnShelf(shelfId)) {
      return;
    }

    if (this.activeDropTarget === element) {
      return;
    }

    this.clearActiveDropTarget();
    this.activeDropTarget = element;
    this.activeDropTarget.classList.add(BookShelfDragDropService.ACTIVE_DROP_TARGET_CLASS);
  }

  onShelfDragOver(event: DragEvent, shelfId?: number | null): void {
    if (!this.canDropOnShelf(shelfId)) {
      return;
    }

    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
  }

  onShelfDragLeave(): void {
    // Intentionally a no-op. dragleave is noisy while moving across children in the same row.
  }

  dropOnShelf(event: DragEvent, shelfId?: number | null): void {
    if (!this.canDropOnShelf(shelfId)) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    const book = this.draggedBook();
    if (!book || shelfId == null) {
      return;
    }

    const idsToAssign = new Set<number>([shelfId]);
    const idsToUnassign = new Set<number>(
      (book.shelves ?? [])
        .map(shelf => shelf.id)
        .filter((id): id is number => id != null && id !== shelfId)
    );

    const loader = this.loadingService.show(
      this.t.translate('book.shelfAssigner.loading.updatingShelves', {count: 1})
    );

    this.bookService.updateBookShelves(new Set([book.id]), idsToAssign, idsToUnassign)
      .pipe(finalize(() => this.loadingService.hide(loader)))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'info',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('book.shelfAssigner.toast.updateSuccessDetail')
          });
          this.endDrag();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('book.shelfAssigner.toast.updateFailedDetail')
          });
          this.endDrag();
        }
      });
  }

  private clearActiveDropTarget(): void {
    this.activeDropTarget?.classList.remove(BookShelfDragDropService.ACTIVE_DROP_TARGET_CLASS);
    this.activeDropTarget = null;
  }
}
