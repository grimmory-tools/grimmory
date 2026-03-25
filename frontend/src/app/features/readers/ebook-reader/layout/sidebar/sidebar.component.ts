import {Component, DestroyRef, effect, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective} from '@jsverse/transloco';
import {TocItem} from 'epubjs';
import {ReaderSidebarService, SidebarTab} from './sidebar.service';
import {ReaderIconComponent} from '../../shared/icon.component';

@Component({
  selector: 'app-reader-sidebar',
  standalone: true,
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  imports: [CommonModule, FormsModule, TranslocoDirective, ReaderIconComponent]
})
export class ReaderSidebarComponent {
  private readonly sidebarService = inject(ReaderSidebarService);
  private readonly destroyRef = inject(DestroyRef);
  private closeAnimationTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly activeTab = this.sidebarService.activeTab;
  readonly bookInfo = this.sidebarService.bookInfo;
  readonly chapters = this.sidebarService.chapters;
  readonly bookmarks = this.sidebarService.bookmarks;
  readonly annotations = this.sidebarService.annotations;

  readonly isOpen = signal(false);
  readonly closing = signal(false);

  constructor() {
    effect(() => {
      if (this.sidebarService.isOpen()) {
        this.clearCloseAnimation();
        this.isOpen.set(true);
        this.closing.set(false);
        return;
      }

      if (this.isOpen()) {
        this.closeWithAnimation();
      }
    });

    this.destroyRef.onDestroy(() => this.clearCloseAnimation());
  }

  private closeWithAnimation(): void {
    this.closing.set(true);
    this.closeAnimationTimeout = setTimeout(() => {
      this.isOpen.set(false);
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
    this.sidebarService.close();
  }

  setActiveTab(tab: SidebarTab): void {
    this.sidebarService.setActiveTab(tab);
  }

  onChapterClick(chapter: TocItem): void {
    if (chapter.subitems?.length) {
      this.sidebarService.toggleChapterExpand(chapter.href);
    } else {
      this.sidebarService.navigateToChapter(chapter.href);
    }
  }

  isChapterExpanded(href: string): boolean {
    return this.sidebarService.isChapterExpanded(href);
  }

  isChapterActive(href: string): boolean {
    return this.sidebarService.isChapterActive(href);
  }

  onBookmarkClick(cfi: string): void {
    this.sidebarService.navigateToBookmark(cfi);
  }

  onDeleteBookmark(event: MouseEvent, bookmarkId: number): void {
    event.stopPropagation();
    this.sidebarService.deleteBookmark(bookmarkId);
  }

  onAnnotationClick(cfi: string): void {
    this.sidebarService.navigateToAnnotation(cfi);
  }

  onDeleteAnnotation(event: MouseEvent, annotationId: number): void {
    event.stopPropagation();
    this.sidebarService.deleteAnnotation(annotationId);
  }

  onCoverClick(): void {
    this.sidebarService.openMetadata();
  }
}
