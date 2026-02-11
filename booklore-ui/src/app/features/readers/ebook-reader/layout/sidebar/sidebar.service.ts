import {inject, Injectable, Injector} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {TocItem} from 'epubjs';
import {BookMark, BookMarkService} from '../../../../../shared/service/book-mark.service';
import {Annotation} from '../../../../../shared/service/annotation.service';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {ReaderViewManagerService} from '../../core/view-manager.service';
import {ReaderProgressService} from '../../state/progress.service';
import {ReaderBookmarkService} from '../../features/bookmarks/bookmark.service';
import {ReaderAnnotationHttpService} from '../../features/annotations/annotation.service';
import {ReaderSelectionService} from '../../features/selection/selection.service';
import {Book} from '../../../../book/model/book.model';

export interface SidebarBookInfo {
  id: number | null;
  title: string;
  authors: string;
  coverUrl: string | null;
}

export type SidebarTab = 'chapters' | 'bookmarks' | 'highlights';

export interface SearchResult {
  cfi: string;
  excerpt: {
    pre: string;
    match: string;
    post: string;
  };
  sectionLabel?: string;
}

export interface SearchState {
  query: string;
  results: SearchResult[];
  isSearching: boolean;
  progress: number;
}

@Injectable()
export class ReaderSidebarService {
  private injector = inject(Injector);
  private urlHelper = inject(UrlHelperService);
  private viewManager = inject(ReaderViewManagerService);
  private progressService = inject(ReaderProgressService);
  private bookmarkService = inject(ReaderBookmarkService);
  private bookMarkHttpService = inject(BookMarkService);
  private annotationService = inject(ReaderAnnotationHttpService);

  private destroy$ = new Subject<void>();
  private bookId!: number;
  private selectionService: ReaderSelectionService | null = null;

  private _isOpen = new BehaviorSubject<boolean>(false);
  private _activeTab = new BehaviorSubject<SidebarTab>('chapters');
  private _bookInfo = new BehaviorSubject<SidebarBookInfo>({
    id: null,
    title: '',
    authors: '',
    coverUrl: null
  });
  private _chapters = new BehaviorSubject<TocItem[]>([]);
  private _bookmarks = new BehaviorSubject<BookMark[]>([]);
  private _annotations = new BehaviorSubject<Annotation[]>([]);
  private _expandedChapters = new Set<string>();

  isOpen$ = this._isOpen.asObservable();
  activeTab$ = this._activeTab.asObservable();
  bookInfo$ = this._bookInfo.asObservable();
  chapters$ = this._chapters.asObservable();
  bookmarks$ = this._bookmarks.asObservable();
  annotations$ = this._annotations.asObservable();

  private _showMetadata = new Subject<void>();
  showMetadata$ = this._showMetadata.asObservable();

  get currentChapterHref(): string | null {
    return this.progressService.currentChapterHref;
  }

  get activeTab(): SidebarTab {
    return this._activeTab.value;
  }

  initialize(bookId: number, book: Book, destroy$: Subject<void>): void {
    this.bookId = bookId;
    this.destroy$ = destroy$;

    this._bookInfo.next({
      id: book.id,
      title: book.metadata?.title || '',
      authors: (book.metadata?.authors || []).join(', '),
      coverUrl: this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn)
    });

    this._chapters.next(this.viewManager.getChapters());

    this.loadBookmarks();

    this.loadAnnotations();

    this.subscribeToAnnotationChanges();
  }

  private subscribeToAnnotationChanges(): void {
    if (!this.selectionService) {
      this.selectionService = this.injector.get(ReaderSelectionService);
    }

    this.selectionService.annotationsChanged$
      .pipe(takeUntil(this.destroy$))
      .subscribe(annotations => {
        this._annotations.next(annotations);
      });
  }

  private loadAnnotations(): void {
    this.annotationService.getAnnotations(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(annotations => {
        this._annotations.next(annotations);
        if (!this.selectionService) {
          this.selectionService = this.injector.get(ReaderSelectionService);
        }
        this.selectionService.setAnnotations(annotations);
        if (annotations.length > 0) {
          const viewAnnotations = this.annotationService.toViewAnnotations(annotations);
          this.viewManager.addAnnotations(viewAnnotations);
        }
      });
  }

  setAnnotations(annotations: Annotation[]): void {
    this._annotations.next(annotations);
  }

  updateChapters(): void {
    this._chapters.next(this.viewManager.getChapters());
  }

  open(tab?: SidebarTab): void {
    if (tab) {
      this._activeTab.next(tab);
    }
    this._isOpen.next(true);
    this.autoExpandCurrentChapter();
  }

  close(): void {
    this._isOpen.next(false);
  }

  toggle(tab?: SidebarTab): void {
    if (this._isOpen.value && (!tab || this._activeTab.value === tab)) {
      this.close();
    } else {
      this.open(tab);
    }
  }

  setActiveTab(tab: SidebarTab): void {
    this._activeTab.next(tab);
  }

  navigateToChapter(href: string): void {
    this.viewManager.goTo(href)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.close());
  }

  toggleChapterExpand(href: string): void {
    if (this._expandedChapters.has(href)) {
      this._expandedChapters.delete(href);
    } else {
      this._expandedChapters.add(href);
    }
  }

  isChapterExpanded(href: string): boolean {
    return this._expandedChapters.has(href);
  }

  isChapterActive(href: string): boolean {
    const currentHref = this.currentChapterHref;
    if (!currentHref) return false;

    const normalizedItemHref = href.split('#')[0].replace(/^\//, '');
    const normalizedCurrentHref = currentHref.split('#')[0].replace(/^\//, '');

    return normalizedItemHref === normalizedCurrentHref || href === currentHref;
  }

  private autoExpandCurrentChapter(): void {
    const currentHref = this.currentChapterHref;
    if (!currentHref) return;

    const chapters = this._chapters.value;
    const expandParents = (items: TocItem[], parents: string[] = []): boolean => {
      for (const item of items) {
        const currentParents = [...parents, item.href];

        const normalizedItemHref = item.href.split('#')[0].replace(/^\//, '');
        const normalizedCurrentHref = currentHref.split('#')[0].replace(/^\//, '');

        if (normalizedItemHref === normalizedCurrentHref || item.href === currentHref) {
          parents.forEach(parentHref => this._expandedChapters.add(parentHref));
          return true;
        }

        if (item.subitems?.length) {
          if (expandParents(item.subitems, currentParents)) {
            return true;
          }
        }
      }
      return false;
    };

    expandParents(chapters);
  }

  private loadBookmarks(): void {
    this.bookMarkHttpService.getBookmarksForBook(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(bookmarks => this._bookmarks.next(bookmarks));
  }

  navigateToBookmark(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.close());
  }

  createBookmark(): void {
    this.bookmarkService.createBookmarkAtCurrentPosition(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(success => {
        if (success) {
          this.loadBookmarks();
        }
      });
  }

  toggleBookmark(): void {
    const currentCfi = this.progressService.currentCfi;
    if (!currentCfi) return;

    const existingBookmark = this._bookmarks.value.find(b => b.cfi === currentCfi);
    if (existingBookmark) {
      this.deleteBookmark(existingBookmark.id!);
    } else {
      this.createBookmark();
    }
  }

  deleteBookmark(bookmarkId: number): void {
    this.bookMarkHttpService.deleteBookmark(bookmarkId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadBookmarks());
  }

  navigateToAnnotation(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.close());
  }

  deleteAnnotation(annotationId: number): void {
    const annotations = this._annotations.value;
    const annotation = annotations.find(a => a.id === annotationId);
    if (!annotation) return;

    this.annotationService.deleteAnnotation(annotationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(success => {
        if (success) {
          this.viewManager.deleteAnnotation(annotation.cfi)
            .pipe(takeUntil(this.destroy$))
            .subscribe();
          const updatedAnnotations = annotations.filter(a => a.id !== annotationId);
          this._annotations.next(updatedAnnotations);
          if (this.selectionService) {
            this.selectionService.setAnnotations(updatedAnnotations);
          }
        }
      });
  }

  openMetadata(): void {
    this.close();
    this._showMetadata.next();
  }

  reset(): void {
    this._isOpen.next(false);
    this._activeTab.next('chapters');
    this._bookInfo.next({ id: null, title: '', authors: '', coverUrl: null });
    this._chapters.next([]);
    this._bookmarks.next([]);
    this._annotations.next([]);
    this._expandedChapters.clear();
  }
}
