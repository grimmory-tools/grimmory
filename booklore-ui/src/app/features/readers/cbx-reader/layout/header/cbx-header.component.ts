import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {TranslocoPipe} from '@jsverse/transloco';
import {CbxHeaderService, CbxHeaderState} from './cbx-header.service';
import {ReaderIconComponent} from '../../../ebook-reader';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-cbx-header',
  standalone: true,
  imports: [CommonModule, TranslocoPipe, ReaderIconComponent],
  templateUrl: './cbx-header.component.html',
  styleUrls: ['./cbx-header.component.scss']
})
export class CbxHeaderComponent implements OnInit, OnDestroy {
  private headerService = inject(CbxHeaderService);
  private destroy$ = new Subject<void>();

  @Input() isCurrentPageBookmarked = false;
  @Input() currentPageHasNotes = false;

  isVisible = true;
  overflowOpen = false;
  state: CbxHeaderState = {
    isFullscreen: false,
    isSlideshowActive: false
  };

  get bookTitle(): string {
    return this.headerService.title;
  }

  ngOnInit(): void {
    this.headerService.forceVisible$
      .pipe(takeUntil(this.destroy$))
      .subscribe(visible => this.isVisible = visible);

    this.headerService.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => this.state = state);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onOpenSidebar(): void {
    this.headerService.openSidebar();
  }

  onOpenSettings(): void {
    this.headerService.openQuickSettings();
  }

  onToggleBookmark(): void {
    this.headerService.toggleBookmark();
  }

  onOpenNoteDialog(): void {
    this.headerService.openNoteDialog();
  }

  onToggleFullscreen(): void {
    this.headerService.toggleFullscreen();
  }

  onToggleSlideshow(): void {
    this.headerService.toggleSlideshow();
  }

  onShowShortcutsHelp(): void {
    this.headerService.showShortcutsHelp();
  }

  onClose(): void {
    this.headerService.close();
  }
}
