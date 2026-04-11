import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subject} from 'rxjs';
import {BookNoteV2, BookNoteV2Service} from '../../../../../shared/service/book-note-v2.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {SearchResult, SearchState} from '../sidebar/sidebar.service';

export type LeftSidebarTab = 'search' | 'notes';

interface ReaderSearchSubitem {
  cfi: string;
  excerpt: SearchResult['excerpt'];
}

@Injectable()
export class ReaderLeftSidebarService {
  private viewManager = inject(ReaderViewManagerService);
  private bookNoteV2Service = inject(BookNoteV2Service);

  private readonly destroyRef = inject(DestroyRef);
  private bookId!: number;

  private readonly _isOpen = signal(false);
  readonly isOpen = this._isOpen.asReadonly();

  private readonly _activeTab = signal<LeftSidebarTab>('search');
  readonly activeTab = this._activeTab.asReadonly();

  private readonly _notes = signal<BookNoteV2[]>([]);
  readonly notes = this._notes.asReadonly();

  private readonly _notesSearchQuery = signal('');
  readonly notesSearchQuery = this._notesSearchQuery.asReadonly();
  readonly filteredNotes = computed(() => {
    const query = this._notesSearchQuery().toLowerCase().trim();
    const notes = this._notes();

    if (!query) {
      return notes;
    }

    return notes.filter(note =>
      note.noteContent.toLowerCase().includes(query) ||
      note.selectedText?.toLowerCase().includes(query) ||
      note.chapterTitle?.toLowerCase().includes(query)
    );
  });

  private readonly _searchState = signal<SearchState>({
    query: '',
    results: [],
    isSearching: false,
    progress: 0
  });
  readonly searchState = this._searchState.asReadonly();
  private searchAbortController: AbortController | null = null;

  private _editNote = new Subject<BookNoteV2>();
  editNote$ = this._editNote.asObservable();

  initialize(bookId: number): void {
    this.bookId = bookId;

    this.loadNotes();
  }

  private loadNotes(): void {
    this.bookNoteV2Service.getNotesForBook(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(notes => this._notes.set(notes));
  }

  refreshNotes(): void {
    this.loadNotes();
  }

  open(tab?: LeftSidebarTab): void {
    if (tab) {
      this._activeTab.set(tab);
    }
    this._isOpen.set(true);
  }

  close(): void {
    this._isOpen.set(false);
  }

  toggle(tab?: LeftSidebarTab): void {
    if (this._isOpen() && (!tab || this._activeTab() === tab)) {
      this.close();
    } else {
      this.open(tab);
    }
  }

  setActiveTab(tab: LeftSidebarTab): void {
    this._activeTab.set(tab);
  }

  openWithSearch(query: string): void {
    this._activeTab.set('search');
    this._isOpen.set(true);
    setTimeout(() => this.search(query), 100);
  }

  navigateToNote(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.close());
  }

  deleteNote(noteId: number): void {
    this.bookNoteV2Service.deleteNote(noteId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this._notes.update(notes => notes.filter(note => note.id !== noteId));
      });
  }

  editNote(note: BookNoteV2): void {
    this.close();
    this._editNote.next(note);
  }

  setNotesSearchQuery(query: string): void {
    this._notesSearchQuery.set(query);
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

    this._searchState.set({
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
          this._searchState.update(current => ({
            ...current,
            progress: result.progress
          }));
        }

        if ('subitems' in result && result.subitems) {
          const sectionResults = (result.subitems as ReaderSearchSubitem[]).map((item) => ({
            cfi: item.cfi,
            excerpt: item.excerpt,
            sectionLabel: result.label
          }));
          results.push(...sectionResults);
          this._searchState.update(current => ({
            ...current,
            results: [...results]
          }));
        }
      }

      this._searchState.set({
        query,
        results,
        isSearching: false,
        progress: 1
      });
    } catch {
      this._searchState.update(current => ({...current, isSearching: false}));
    }
  }

  clearSearch(): void {
    if (this.searchAbortController) {
      this.searchAbortController.abort();
      this.searchAbortController = null;
    }
    this.viewManager.clearSearch();
    this._searchState.set({
      query: '',
      results: [],
      isSearching: false,
      progress: 0
    });
  }

  navigateToSearchResult(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.close());
  }

  reset(): void {
    this._isOpen.set(false);
    this._activeTab.set('search');
    this._notes.set([]);
    this._notesSearchQuery.set('');
    this.clearSearch();
  }
}
