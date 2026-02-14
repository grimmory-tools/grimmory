import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderSidebarService, SidebarBookInfo, SidebarTab} from './sidebar.service';
import {TocItem} from 'epubjs';
import {BookMark} from '../../../../../shared/service/book-mark.service';
import {Annotation} from '../../../../../shared/service/annotation.service';
import {ReaderIconComponent} from '../../shared/icon.component';

@Component({
  selector: 'app-reader-sidebar',
  standalone: true,
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  imports: [CommonModule, FormsModule, TranslocoDirective, ReaderIconComponent]
})
export class ReaderSidebarComponent implements OnInit, OnDestroy {
  private sidebarService = inject(ReaderSidebarService);
  private destroy$ = new Subject<void>();

  isOpen = false;
  closing = false;
  activeTab: SidebarTab = 'chapters';
  bookInfo: SidebarBookInfo = { id: null, title: '', authors: '', coverUrl: null };
  chapters: TocItem[] = [];
  bookmarks: BookMark[] = [];
  annotations: Annotation[] = [];

  ngOnInit(): void {
    this.sidebarService.isOpen$
      .pipe(takeUntil(this.destroy$))
      .subscribe(isOpen => {
        if (isOpen) {
          this.isOpen = true;
          this.closing = false;
        } else if (this.isOpen) {
          this.closeWithAnimation();
        }
      });

    this.sidebarService.activeTab$
      .pipe(takeUntil(this.destroy$))
      .subscribe(tab => this.activeTab = tab);

    this.sidebarService.bookInfo$
      .pipe(takeUntil(this.destroy$))
      .subscribe(info => this.bookInfo = info);

    this.sidebarService.chapters$
      .pipe(takeUntil(this.destroy$))
      .subscribe(chapters => this.chapters = chapters);

    this.sidebarService.bookmarks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => this.bookmarks = bookmarks);

    this.sidebarService.annotations$
      .pipe(takeUntil(this.destroy$))
      .subscribe(annotations => this.annotations = annotations);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private closeWithAnimation(): void {
    this.closing = true;
    setTimeout(() => {
      this.isOpen = false;
      this.closing = false;
    }, 250);
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
