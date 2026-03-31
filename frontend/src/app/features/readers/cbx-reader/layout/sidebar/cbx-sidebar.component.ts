import {Component, DestroyRef, effect, inject, signal} from '@angular/core';
import {CommonModule, DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoPipe} from '@jsverse/transloco';
import {CbxSidebarService, CbxSidebarTab} from './cbx-sidebar.service';
import {BookNoteV2} from '../../../../../shared/service/book-note-v2.service';
import {ReaderIconComponent} from '../../../ebook-reader';
import {CoverPlaceholderComponent} from '../../../../../shared/components/cover-generator/cover-generator.component';

@Component({
  selector: 'app-cbx-sidebar',
  standalone: true,
  templateUrl: './cbx-sidebar.component.html',
  styleUrls: ['./cbx-sidebar.component.scss'],
  imports: [CommonModule, FormsModule, TranslocoPipe, ReaderIconComponent, DatePipe, CoverPlaceholderComponent]
})
export class CbxSidebarComponent {
  private readonly sidebarService = inject(CbxSidebarService);
  private readonly destroyRef = inject(DestroyRef);
  private closeAnimationTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly activeTab = this.sidebarService.activeTab;
  readonly bookInfo = this.sidebarService.bookInfo;
  readonly pages = this.sidebarService.pages;
  readonly currentPage = this.sidebarService.currentPage;
  readonly bookmarks = this.sidebarService.bookmarks;
  readonly notes = this.sidebarService.notes;
  readonly notesSearchQuery = this.sidebarService.notesSearchQuery;
  readonly filteredNotes = this.sidebarService.filteredNotes;

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

  setActiveTab(tab: CbxSidebarTab): void {
    this.sidebarService.setActiveTab(tab);
  }

  onPageClick(pageNumber: number): void {
    this.sidebarService.navigateToPage(pageNumber);
  }

  isPageActive(pageNumber: number): boolean {
    return this.currentPage() === pageNumber;
  }

  onBookmarkClick(cfi: string): void {
    this.sidebarService.navigateToBookmark(cfi);
  }

  onDeleteBookmark(event: MouseEvent, bookmarkId: number): void {
    event.stopPropagation();
    this.sidebarService.deleteBookmark(bookmarkId);
  }

  onNoteClick(cfi: string): void {
    this.sidebarService.navigateToNote(cfi);
  }

  onEditNote(event: MouseEvent, note: BookNoteV2): void {
    event.stopPropagation();
    this.sidebarService.editNote(note);
  }

  onDeleteNote(event: MouseEvent, noteId: number): void {
    event.stopPropagation();
    this.sidebarService.deleteNote(noteId);
  }

  onNotesSearchInput(query: string): void {
    this.sidebarService.setNotesSearchQuery(query);
  }

  clearNotesSearch(): void {
    this.sidebarService.setNotesSearchQuery('');
  }
}
