import {computed, DestroyRef, inject, Injectable, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subject} from 'rxjs';
import {TranslocoService} from '@jsverse/transloco';
import {CbxPageInfo, CbxReaderService} from '../../../../book/service/cbx-reader.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {Book} from '../../../../book/model/book.model';
import {BookMark, BookMarkService, CreateBookMarkRequest} from '../../../../../shared/service/book-mark.service';
import {BookNoteV2, BookNoteV2Service, CreateBookNoteV2Request, UpdateBookNoteV2Request} from '../../../../../shared/service/book-note-v2.service';

export interface SidebarBookInfo {
  id: number | null;
  title: string;
  authors: string;
  coverUrl: string | null;
}

export type CbxSidebarTab = 'pages' | 'bookmarks' | 'notes';

@Injectable()
export class CbxSidebarService {
  private urlHelper = inject(UrlHelperService);
  private cbxReaderService = inject(CbxReaderService);
  private bookMarkService = inject(BookMarkService);
  private bookNoteV2Service = inject(BookNoteV2Service);
  private readonly t = inject(TranslocoService);

  private readonly destroyRef = inject(DestroyRef);
  private bookId!: number;
  private altBookType?: string;

  private readonly _isOpen = signal(false);
  readonly isOpen = this._isOpen.asReadonly();

  private readonly _activeTab = signal<CbxSidebarTab>('pages');
  readonly activeTab = this._activeTab.asReadonly();

  private readonly _bookInfo = signal<SidebarBookInfo>({
    id: null,
    title: '',
    authors: '',
    coverUrl: null
  });
  readonly bookInfo = this._bookInfo.asReadonly();

  private readonly _pages = signal<CbxPageInfo[]>([]);
  readonly pages = this._pages.asReadonly();

  private readonly _currentPage = signal(1);
  readonly currentPage = this._currentPage.asReadonly();

  private readonly _bookmarks = signal<BookMark[]>([]);
  readonly bookmarks = this._bookmarks.asReadonly();

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
      note.chapterTitle?.toLowerCase().includes(query)
    );
  });

  private _navigateToPage = new Subject<number>();
  private _editNote = new Subject<BookNoteV2>();
  navigateToPage$ = this._navigateToPage.asObservable();
  editNote$ = this._editNote.asObservable();

  initialize(bookId: number, book: Book, altBookType?: string): void {
    this.bookId = bookId;
    this.altBookType = altBookType;

    this._bookInfo.set({
      id: book.id,
      title: book.metadata?.title || book.fileName || this.t.translate('readerCbx.sidebar.untitled'),
      authors: (book.metadata?.authors || []).join(', '),
      coverUrl: this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn)
    });

    this.loadPageInfo();
    this.loadBookmarks();
    this.loadNotes();
  }

  private loadPageInfo(): void {
    this.cbxReaderService.getPageInfo(this.bookId, this.altBookType)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(pages => this._pages.set(pages));
  }

  private loadBookmarks(): void {
    this.bookMarkService.getBookmarksForBook(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(bookmarks => this._bookmarks.set(bookmarks));
  }

  private loadNotes(): void {
    this.bookNoteV2Service.getNotesForBook(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(notes => this._notes.set(notes));
  }

  setCurrentPage(page: number): void {
    this._currentPage.set(page);
  }

  setActiveTab(tab: CbxSidebarTab): void {
    this._activeTab.set(tab);
  }

  open(tab?: CbxSidebarTab): void {
    if (tab) {
      this._activeTab.set(tab);
    }
    this._isOpen.set(true);
  }

  close(): void {
    this._isOpen.set(false);
  }

  navigateToPage(pageNumber: number): void {
    this._navigateToPage.next(pageNumber);
    this.close();
  }

  isPageBookmarked(pageNumber: number): boolean {
    const pageStr = pageNumber.toString();
    return this._bookmarks().some(bookmark => bookmark.cfi === pageStr);
  }

  getBookmarkForPage(pageNumber: number): BookMark | undefined {
    const pageStr = pageNumber.toString();
    return this._bookmarks().find(bookmark => bookmark.cfi === pageStr);
  }

  createBookmark(pageNumber: number, title?: string): void {
    const request: CreateBookMarkRequest = {
      bookId: this.bookId,
      cfi: pageNumber.toString(),
      title: title || `${this.t.translate('readerCbx.sidebar.page')} ${pageNumber}`
    };

    this.bookMarkService.createBookmark(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadBookmarks());
  }

  deleteBookmark(bookmarkId: number): void {
    this.bookMarkService.deleteBookmark(bookmarkId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadBookmarks());
  }

  toggleBookmark(pageNumber: number): void {
    const existingBookmark = this.getBookmarkForPage(pageNumber);
    if (existingBookmark) {
      this.deleteBookmark(existingBookmark.id);
    } else {
      this.createBookmark(pageNumber);
    }
  }

  navigateToBookmark(pageNumber: string): void {
    const page = parseInt(pageNumber, 10);
    if (!isNaN(page)) {
      this._navigateToPage.next(page);
      this.close();
    }
  }

  pageHasNotes(pageNumber: number): boolean {
    const pageStr = pageNumber.toString();
    return this._notes().some(note => note.cfi === pageStr);
  }

  createNote(pageNumber: number, noteContent: string, color?: string): void {
    const request: CreateBookNoteV2Request = {
      bookId: this.bookId,
      cfi: pageNumber.toString(),
      noteContent,
      color: color || '#FFC107',
      chapterTitle: `${this.t.translate('readerCbx.sidebar.page')} ${pageNumber}`
    };

    this.bookNoteV2Service.createNote(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadNotes());
  }

  updateNote(noteId: number, noteContent: string, color?: string): void {
    const request: UpdateBookNoteV2Request = {
      noteContent,
      color
    };

    this.bookNoteV2Service.updateNote(noteId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadNotes());
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

  navigateToNote(pageNumber: string): void {
    const page = parseInt(pageNumber, 10);
    if (!isNaN(page)) {
      this._navigateToPage.next(page);
      this.close();
    }
  }

  setNotesSearchQuery(query: string): void {
    this._notesSearchQuery.set(query);
  }
}
