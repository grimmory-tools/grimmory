import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule, DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoPipe} from '@jsverse/transloco';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {CbxSidebarService, CbxSidebarTab, SidebarBookInfo} from './cbx-sidebar.service';
import {CbxPageInfo} from '../../../../book/service/cbx-reader.service';
import {BookMark} from '../../../../../shared/service/book-mark.service';
import {BookNoteV2} from '../../../../../shared/service/book-note-v2.service';
import {ReaderIconComponent} from '../../../ebook-reader';

@Component({
  selector: 'app-cbx-sidebar',
  standalone: true,
  templateUrl: './cbx-sidebar.component.html',
  styleUrls: ['./cbx-sidebar.component.scss'],
  imports: [CommonModule, FormsModule, TranslocoPipe, ReaderIconComponent, DatePipe]
})
export class CbxSidebarComponent implements OnInit, OnDestroy {
  private sidebarService = inject(CbxSidebarService);
  private destroy$ = new Subject<void>();

  isOpen = false;
  closing = false;
  activeTab: CbxSidebarTab = 'pages';
  bookInfo: SidebarBookInfo = { id: null, title: '', authors: '', coverUrl: null };
  pages: CbxPageInfo[] = [];
  currentPage = 1;
  bookmarks: BookMark[] = [];
  notes: BookNoteV2[] = [];
  notesSearchQuery = '';

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

    this.sidebarService.pages$
      .pipe(takeUntil(this.destroy$))
      .subscribe(pages => this.pages = pages);

    this.sidebarService.currentPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => this.currentPage = page);

    this.sidebarService.bookmarks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => this.bookmarks = bookmarks);

    this.sidebarService.notes$
      .pipe(takeUntil(this.destroy$))
      .subscribe(notes => this.notes = notes);
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

  setActiveTab(tab: CbxSidebarTab): void {
    this.sidebarService.setActiveTab(tab);
  }

  onPageClick(pageNumber: number): void {
    this.sidebarService.navigateToPage(pageNumber);
  }

  isPageActive(pageNumber: number): boolean {
    return this.currentPage === pageNumber;
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
    this.notesSearchQuery = query;
    this.sidebarService.setNotesSearchQuery(query);
  }

  clearNotesSearch(): void {
    this.notesSearchQuery = '';
    this.sidebarService.setNotesSearchQuery('');
  }

  get filteredNotes(): BookNoteV2[] {
    return this.sidebarService.getFilteredNotes();
  }
}
