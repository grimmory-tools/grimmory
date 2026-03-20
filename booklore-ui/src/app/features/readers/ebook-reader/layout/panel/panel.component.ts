import {Component, DestroyRef, effect, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {takeUntil, debounceTime, distinctUntilChanged, filter} from 'rxjs/operators';
import {TranslocoDirective} from '@jsverse/transloco';
import {ReaderLeftSidebarService, LeftSidebarTab} from './panel.service';
import {BookNoteV2} from '../../../../../shared/service/book-note-v2.service';
import {ReaderIconComponent} from '../../shared/icon.component';

@Component({
  selector: 'app-reader-left-sidebar',
  standalone: true,
  templateUrl: './panel.component.html',
  styleUrls: ['./panel.component.scss'],
  imports: [CommonModule, FormsModule, TranslocoDirective, ReaderIconComponent]
})
export class ReaderLeftSidebarComponent {
  private readonly leftSidebarService = inject(ReaderLeftSidebarService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly destroy$ = new Subject<void>();
  private closeAnimationTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly activeTab = this.leftSidebarService.activeTab;
  readonly notes = this.leftSidebarService.notes;
  readonly notesSearchQuery = this.leftSidebarService.notesSearchQuery;
  readonly searchState = this.leftSidebarService.searchState;
  readonly filteredNotes = this.leftSidebarService.filteredNotes;

  readonly isOpen = signal(false);
  readonly closing = signal(false);
  searchQuery = '';
  private readonly searchInput$ = new Subject<string>();

  constructor() {
    effect(() => {
      if (this.leftSidebarService.isOpen()) {
        this.clearCloseAnimation();
        this.isOpen.set(true);
        this.closing.set(false);
        return;
      }

      if (this.isOpen()) {
        this.closeWithAnimation();
      }
    });

    effect(() => {
      const state = this.leftSidebarService.searchState();
      if (state.query !== this.searchQuery) {
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

    this.destroyRef.onDestroy(() => {
      this.clearCloseAnimation();
      this.destroy$.next();
      this.destroy$.complete();
    });
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
    this.leftSidebarService.setNotesSearchQuery(query);
  }

  clearNotesSearch(): void {
    this.leftSidebarService.setNotesSearchQuery('');
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
