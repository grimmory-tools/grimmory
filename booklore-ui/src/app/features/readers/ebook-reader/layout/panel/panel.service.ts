import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {BookNoteV2, BookNoteV2Service} from '../../../../../shared/service/book-note-v2.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {SearchResult, SearchState} from '../sidebar/sidebar.service';

export type LeftSidebarTab = 'search' | 'notes';

@Injectable()
export class ReaderLeftSidebarService {
  private viewManager = inject(ReaderViewManagerService);
  private bookNoteV2Service = inject(BookNoteV2Service);

  private destroy$ = new Subject<void>();
  private bookId!: number;

  private _isOpen = new BehaviorSubject<boolean>(false);
  private _activeTab = new BehaviorSubject<LeftSidebarTab>('search');
  private _notes = new BehaviorSubject<BookNoteV2[]>([]);
  private _notesSearchQuery = new BehaviorSubject<string>('');
  private _searchState = new BehaviorSubject<SearchState>({
    query: '',
    results: [],
    isSearching: false,
    progress: 0
  });
  private searchAbortController: AbortController | null = null;

  isOpen$ = this._isOpen.asObservable();
  activeTab$ = this._activeTab.asObservable();
  notes$ = this._notes.asObservable();
  notesSearchQuery$ = this._notesSearchQuery.asObservable();
  searchState$ = this._searchState.asObservable();

  private _editNote = new Subject<BookNoteV2>();
  editNote$ = this._editNote.asObservable();

  initialize(bookId: number, destroy$: Subject<void>): void {
    this.bookId = bookId;
    this.destroy$ = destroy$;

    this.loadNotes();
  }

  private loadNotes(): void {
    this.bookNoteV2Service.getNotesForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(notes => this._notes.next(notes));
  }

  refreshNotes(): void {
    this.loadNotes();
  }

  open(tab?: LeftSidebarTab): void {
    if (tab) {
      this._activeTab.next(tab);
    }
    this._isOpen.next(true);
  }

  close(): void {
    this._isOpen.next(false);
  }

  toggle(tab?: LeftSidebarTab): void {
    if (this._isOpen.value && (!tab || this._activeTab.value === tab)) {
      this.close();
    } else {
      this.open(tab);
    }
  }

  setActiveTab(tab: LeftSidebarTab): void {
    this._activeTab.next(tab);
  }

  openWithSearch(query: string): void {
    this._activeTab.next('search');
    this._isOpen.next(true);
    setTimeout(() => this.search(query), 100);
  }

  navigateToNote(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.close());
  }

  deleteNote(noteId: number): void {
    this.bookNoteV2Service.deleteNote(noteId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        const updatedNotes = this._notes.value.filter(n => n.id !== noteId);
        this._notes.next(updatedNotes);
      });
  }

  editNote(note: BookNoteV2): void {
    this.close();
    this._editNote.next(note);
  }

  setNotesSearchQuery(query: string): void {
    this._notesSearchQuery.next(query);
  }

  getFilteredNotes(): BookNoteV2[] {
    const query = this._notesSearchQuery.value.toLowerCase().trim();
    if (!query) {
      return this._notes.value;
    }
    return this._notes.value.filter(note =>
      note.noteContent.toLowerCase().includes(query) ||
      (note.selectedText?.toLowerCase().includes(query)) ||
      (note.chapterTitle?.toLowerCase().includes(query))
    );
  }

  async search(query: string): Promise<void> {
    if (!query.trim()) {
      this.clearSearch();
      return;
    }

    if (this.searchAbortController) {
      this.searchAbortController.abort();
    }
    this.searchAbortController = new AbortController();

    this._searchState.next({
      query,
      results: [],
      isSearching: true,
      progress: 0
    });

    try {
      const results: SearchResult[] = [];
      const searchGenerator = this.viewManager.search({ query });

      for await (const result of searchGenerator) {
        if (this.searchAbortController?.signal.aborted) break;

        if (typeof result === 'string' && result === 'done') {
          break;
        }

        if ('progress' in result) {
          this._searchState.next({
            ...this._searchState.value,
            progress: result.progress
          });
        }

        if ('subitems' in result && result.subitems) {
          const sectionResults = result.subitems.map((item: any) => ({
            cfi: item.cfi,
            excerpt: item.excerpt,
            sectionLabel: result.label
          }));
          results.push(...sectionResults);
          this._searchState.next({
            ...this._searchState.value,
            results: [...results]
          });
        }
      }

      this._searchState.next({
        query,
        results,
        isSearching: false,
        progress: 1
      });
    } catch (error) {
      this._searchState.next({
        ...this._searchState.value,
        isSearching: false
      });
    }
  }

  clearSearch(): void {
    if (this.searchAbortController) {
      this.searchAbortController.abort();
      this.searchAbortController = null;
    }
    this.viewManager.clearSearch();
    this._searchState.next({
      query: '',
      results: [],
      isSearching: false,
      progress: 0
    });
  }

  navigateToSearchResult(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.close());
  }

  reset(): void {
    this._isOpen.next(false);
    this._activeTab.next('search');
    this._notes.next([]);
    this._notesSearchQuery.next('');
    this.clearSearch();
  }
}
