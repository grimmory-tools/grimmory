import {Component, DestroyRef, inject, input, output, signal, effect} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderIconComponent} from '../../ebook-reader/shared/icon.component';
import {BookMark} from '../../../../shared/service/book-mark.service';
import {PdfOutlineItem} from '../services/embedpdf-book.service';

export type PdfSidebarTab = 'contents' | 'bookmarks' | 'annotations';

export interface PdfAnnotationListItem {
  id: string;
  pageIndex: number;
  type: string;
  color?: string;
  text?: string;
}

@Component({
  selector: 'app-pdf-sidebar',
  standalone: true,
  imports: [TranslocoDirective, NgTemplateOutlet, ReaderIconComponent],
  templateUrl: './pdf-sidebar.component.html',
  styleUrl: './pdf-sidebar.component.scss',
})
export class PdfSidebarComponent {
  private readonly destroyRef = inject(DestroyRef);
  private closeAnimationTimeout: ReturnType<typeof setTimeout> | null = null;

  isOpen = input.required<boolean>();
  outline = input<PdfOutlineItem[]>([]);
  bookmarks = input<BookMark[]>([]);
  annotations = input<PdfAnnotationListItem[]>([]);

  closed = output<void>();
  navigateToPage = output<number>();
  deleteBookmark = output<number>();
  deleteAnnotation = output<string>();

  readonly activeTab = signal<PdfSidebarTab>('contents');
  readonly visible = signal(false);
  readonly closing = signal(false);

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.clearCloseAnimation();
        this.visible.set(true);
        this.closing.set(false);
      } else if (this.visible()) {
        this.closeWithAnimation();
      }
    });

    this.destroyRef.onDestroy(() => this.clearCloseAnimation());
  }

  private closeWithAnimation(): void {
    this.closing.set(true);
    this.closeAnimationTimeout = setTimeout(() => {
      this.visible.set(false);
      this.closing.set(false);
      this.closeAnimationTimeout = null;
    }, 250);
  }

  private clearCloseAnimation(): void {
    if (this.closeAnimationTimeout) {
      clearTimeout(this.closeAnimationTimeout);
      this.closeAnimationTimeout = null;
    }
  }

  onOverlayClick(): void {
    this.closed.emit();
  }

  setActiveTab(tab: PdfSidebarTab): void {
    this.activeTab.set(tab);
  }

  onOutlineClick(item: PdfOutlineItem): void {
    this.navigateToPage.emit(item.pageIndex + 1);
  }

  onBookmarkClick(bookmark: BookMark): void {
    if (bookmark.pageNumber != null) {
      this.navigateToPage.emit(bookmark.pageNumber);
    }
  }

  onDeleteBookmark(event: MouseEvent, bookmarkId: number): void {
    event.stopPropagation();
    this.deleteBookmark.emit(bookmarkId);
  }

  onAnnotationClick(annotation: PdfAnnotationListItem): void {
    this.navigateToPage.emit(annotation.pageIndex + 1);
  }

  onDeleteAnnotation(event: MouseEvent, annotationId: string): void {
    event.stopPropagation();
    this.deleteAnnotation.emit(annotationId);
  }
}
