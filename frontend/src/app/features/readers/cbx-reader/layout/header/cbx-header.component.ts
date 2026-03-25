import {Component, inject, Input} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {CbxHeaderService} from './cbx-header.service';
import {ReaderIconComponent} from '../../../ebook-reader';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-cbx-header',
  standalone: true,
  imports: [CommonModule, TranslocoPipe, ReaderIconComponent],
  templateUrl: './cbx-header.component.html',
  styleUrls: ['./cbx-header.component.scss']
})
export class CbxHeaderComponent {
  private readonly headerService = inject(CbxHeaderService);

  @Input() isCurrentPageBookmarked = false;
  @Input() currentPageHasNotes = false;

  readonly forceVisible = this.headerService.forceVisible;
  readonly state = this.headerService.state;
  readonly bookTitle = this.headerService.bookTitle;

  overflowOpen = false;

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

  onToggleMagnifier(): void {
    this.headerService.toggleMagnifier();
  }

  onShowShortcutsHelp(): void {
    this.headerService.showShortcutsHelp();
  }

  onClose(): void {
    this.headerService.close();
  }
}
