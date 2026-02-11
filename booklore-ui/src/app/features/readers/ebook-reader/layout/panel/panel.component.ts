import {Component, EventEmitter, inject, OnDestroy, OnInit, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil, debounceTime, distinctUntilChanged, filter} from 'rxjs/operators';
import {ReaderLeftSidebarService, LeftSidebarTab} from './panel.service';
import {BookNoteV2} from '../../../../../shared/service/book-note-v2.service';
import {SearchState, SearchResult} from '../sidebar/sidebar.service';
import {ReaderIconComponent} from '../../shared/icon.component';

@Component({
  selector: 'app-reader-left-sidebar',
  standalone: true,
  templateUrl: './panel.component.html',
  styleUrls: ['./panel.component.scss'],
  imports: [CommonModule, FormsModule, ReaderIconComponent]
})
export class ReaderLeftSidebarComponent implements OnInit, OnDestroy {
  private leftSidebarService = inject(ReaderLeftSidebarService);
  private destroy$ = new Subject<void>();

  isOpen = false;
  closing = false;
  activeTab: LeftSidebarTab = 'search';
  notes: BookNoteV2[] = [];
  notesSearchQuery = '';
  searchState: SearchState = { query: '', results: [], isSearching: false, progress: 0 };
  searchQuery = '';
  private searchInput$ = new Subject<string>();

  ngOnInit(): void {
    this.leftSidebarService.isOpen$
      .pipe(takeUntil(this.destroy$))
      .subscribe(isOpen => {
        if (isOpen) {
          this.isOpen = true;
          this.closing = false;
        } else if (this.isOpen) {
          this.closeWithAnimation();
        }
      });

    this.leftSidebarService.activeTab$
      .pipe(takeUntil(this.destroy$))
      .subscribe(tab => this.activeTab = tab);

    this.leftSidebarService.notes$
      .pipe(takeUntil(this.destroy$))
      .subscribe(notes => this.notes = notes);

    this.leftSidebarService.searchState$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        this.searchState = state;
        if (state.query && state.query !== this.searchQuery) {
          this.searchQuery = state.query;
        }
      });

    this.searchInput$
      .pipe(
        debounceTime(500),
        distinctUntilChanged(),
        filter(query => query.length >= 3 || query.length === 0),
        takeUntil(this.destroy$)
      )
      .subscribe(query => this.leftSidebarService.search(query));
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
    this.leftSidebarService.close();
  }

  setActiveTab(tab: LeftSidebarTab): void {
    this.leftSidebarService.setActiveTab(tab);
  }

  onNoteClick(cfi: string): void {
    this.leftSidebarService.navigateToNote(cfi);
  }

  onEditNote(event: MouseEvent, note: BookNoteV2): void {
    event.stopPropagation();
    this.leftSidebarService.editNote(note);
  }

  onDeleteNote(event: MouseEvent, noteId: number): void {
    event.stopPropagation();
    this.leftSidebarService.deleteNote(noteId);
  }

  onNotesSearchInput(query: string): void {
    this.notesSearchQuery = query;
    this.leftSidebarService.setNotesSearchQuery(query);
  }

  clearNotesSearch(): void {
    this.notesSearchQuery = '';
    this.leftSidebarService.setNotesSearchQuery('');
  }

  get filteredNotes(): BookNoteV2[] {
    return this.leftSidebarService.getFilteredNotes();
  }

  onSearchInput(query: string): void {
    this.searchInput$.next(query);
  }

  onSearchResultClick(cfi: string): void {
    this.leftSidebarService.navigateToSearchResult(cfi);
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.leftSidebarService.clearSearch();
  }

  onCancelSearch(): void {
    this.searchQuery = '';
    this.leftSidebarService.clearSearch();
  }
}
