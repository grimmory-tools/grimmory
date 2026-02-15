import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
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

  private destroy$ = new Subject<void>();
  private bookId!: number;
  private altBookType?: string;

  private _isOpen = new BehaviorSubject<boolean>(false);
  private _activeTab = new BehaviorSubject<CbxSidebarTab>('pages');
  private _bookInfo = new BehaviorSubject<SidebarBookInfo>({
    id: null,
    title: '',
    authors: '',
    coverUrl: null
  });
  private _pages = new BehaviorSubject<CbxPageInfo[]>([]);
  private _currentPage = new BehaviorSubject<number>(1);
  private _bookmarks = new BehaviorSubject<BookMark[]>([]);
  private _notes = new BehaviorSubject<BookNoteV2[]>([]);
  private _notesSearchQuery = new BehaviorSubject<string>('');

  private _navigateToPage = new Subject<number>();
  private _editNote = new Subject<BookNoteV2>();
  private _bookmarksChanged = new Subject<void>();

  isOpen$ = this._isOpen.asObservable();
  activeTab$ = this._activeTab.asObservable();
  bookInfo$ = this._bookInfo.asObservable();
  pages$ = this._pages.asObservable();
  currentPage$ = this._currentPage.asObservable();
  bookmarks$ = this._bookmarks.asObservable();
  notes$ = this._notes.asObservable();
  navigateToPage$ = this._navigateToPage.asObservable();
  editNote$ = this._editNote.asObservable();
  bookmarksChanged$ = this._bookmarksChanged.asObservable();

  initialize(bookId: number, book: Book, destroy$: Subject<void>, altBookType?: string): void {
    this.bookId = bookId;
    this.altBookType = altBookType;
    this.destroy$ = destroy$;

    this._bookInfo.next({
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
      .pipe(takeUntil(this.destroy$))
      .subscribe(pages => this._pages.next(pages));
  }

  private loadBookmarks(): void {
    this.bookMarkService.getBookmarksForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => this._bookmarks.next(bookmarks));
  }

  private loadNotes(): void {
    this.bookNoteV2Service.getNotesForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(notes => this._notes.next(notes));
  }

  setCurrentPage(page: number): void {
    this._currentPage.next(page);
  }

  setActiveTab(tab: CbxSidebarTab): void {
    this._activeTab.next(tab);
  }

  open(tab?: CbxSidebarTab): void {
    if (tab) {
      this._activeTab.next(tab);
    }
    this._isOpen.next(true);
  }

  close(): void {
    this._isOpen.next(false);
  }

  navigateToPage(pageNumber: number): void {
    this._navigateToPage.next(pageNumber);
    this.close();
  }

  isPageBookmarked(pageNumber: number): boolean {
    const pageStr = pageNumber.toString();
    return this._bookmarks.value.some(b => b.cfi === pageStr);
  }

  getBookmarkForPage(pageNumber: number): BookMark | undefined {
    const pageStr = pageNumber.toString();
    return this._bookmarks.value.find(b => b.cfi === pageStr);
  }

  createBookmark(pageNumber: number, title?: string): void {
    const request: CreateBookMarkRequest = {
      bookId: this.bookId,
      cfi: pageNumber.toString(),
      title: title || `${this.t.translate('readerCbx.sidebar.page')} ${pageNumber}`
    };

    this.bookMarkService.createBookmark(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.loadBookmarks();
        this._bookmarksChanged.next();
      });
  }

  deleteBookmark(bookmarkId: number): void {
    this.bookMarkService.deleteBookmark(bookmarkId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.loadBookmarks();
        this._bookmarksChanged.next();
      });
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
    return this._notes.value.some(n => n.cfi === pageStr);
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
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadNotes());
  }

  updateNote(noteId: number, noteContent: string, color?: string): void {
    const request: UpdateBookNoteV2Request = {
      noteContent,
      color
    };

    this.bookNoteV2Service.updateNote(noteId, request)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadNotes());
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

  navigateToNote(pageNumber: string): void {
    const page = parseInt(pageNumber, 10);
    if (!isNaN(page)) {
      this._navigateToPage.next(page);
      this.close();
    }
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
      (note.chapterTitle?.toLowerCase().includes(query))
    );
  }
}
