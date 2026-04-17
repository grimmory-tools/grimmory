import {DestroyRef, inject, Injectable, Injector, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subject} from 'rxjs';
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

  private readonly destroyRef = inject(DestroyRef);
  private bookId!: number;
  private selectionService: ReaderSelectionService | null = null;

  private readonly _isOpen = signal(false);
  readonly isOpen = this._isOpen.asReadonly();

  private readonly _activeTab = signal<SidebarTab>('chapters');
  readonly activeTab = this._activeTab.asReadonly();

  private readonly _bookInfo = signal<SidebarBookInfo>({
    id: null,
    title: '',
    authors: '',
    coverUrl: null
  });
  readonly bookInfo = this._bookInfo.asReadonly();

  private readonly _chapters = signal<TocItem[]>([]);
  readonly chapters = this._chapters.asReadonly();

  private readonly _bookmarks = signal<BookMark[]>([]);
  readonly bookmarks = this._bookmarks.asReadonly();

  private readonly _annotations = signal<Annotation[]>([]);
  readonly annotations = this._annotations.asReadonly();

  private _expandedChapters = new Set<string>();

  private _showMetadata = new Subject<void>();
  showMetadata$ = this._showMetadata.asObservable();

  get currentChapterHref(): string | null {
    return this.progressService.currentChapterHref;
  }

  initialize(bookId: number, book: Book): void {
    this.bookId = bookId;

    this._bookInfo.set({
      id: book.id,
      title: book.metadata?.title || '',
      authors: (book.metadata?.authors || []).join(', '),
      coverUrl: this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn)
    });

    this._chapters.set(this.viewManager.getChapters());

    this.loadBookmarks();

    this.loadAnnotations();

    this.subscribeToAnnotationChanges();
  }

  private subscribeToAnnotationChanges(): void {
    if (!this.selectionService) {
      this.selectionService = this.injector.get(ReaderSelectionService);
    }

    this.selectionService.annotationsChanged$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(annotations => {
        this._annotations.set(annotations);
      });
  }

  private loadAnnotations(): void {
    this.annotationService.getAnnotations(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(annotations => {
        this._annotations.set(annotations);
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
    this._annotations.set(annotations);
  }

  updateChapters(): void {
    this._chapters.set(this.viewManager.getChapters());
  }

  open(tab?: SidebarTab): void {
    if (tab) {
      this._activeTab.set(tab);
    }
    this._isOpen.set(true);
    this.autoExpandCurrentChapter();
  }

  close(): void {
    this._isOpen.set(false);
  }

  toggle(tab?: SidebarTab): void {
    if (this._isOpen() && (!tab || this._activeTab() === tab)) {
      this.close();
    } else {
      this.open(tab);
    }
  }

  setActiveTab(tab: SidebarTab): void {
    this._activeTab.set(tab);
  }

  navigateToChapter(href: string): void {
    this.viewManager.goTo(href)
      .pipe(takeUntilDestroyed(this.destroyRef))
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

    const chapters = this._chapters();
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
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(bookmarks => this._bookmarks.set(bookmarks));
  }

  navigateToBookmark(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.close());
  }

  createBookmark(): void {
    this.bookmarkService.createBookmarkAtCurrentPosition(this.bookId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(success => {
        if (success) {
          this.loadBookmarks();
        }
      });
  }

  toggleBookmark(): void {
    const currentCfi = this.progressService.currentCfi;
    if (!currentCfi) return;

    const existingBookmark = this._bookmarks().find(bookmark => bookmark.cfi === currentCfi);
    if (existingBookmark) {
      this.deleteBookmark(existingBookmark.id!);
    } else {
      this.createBookmark();
    }
  }

  deleteBookmark(bookmarkId: number): void {
    this.bookMarkHttpService.deleteBookmark(bookmarkId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadBookmarks());
  }

  navigateToAnnotation(cfi: string): void {
    this.viewManager.goTo(cfi)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.close());
  }

  deleteAnnotation(annotationId: number): void {
    const annotations = this._annotations();
    const annotation = annotations.find(a => a.id === annotationId);
    if (!annotation) return;

    this.annotationService.deleteAnnotation(annotationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(success => {
        if (success) {
          this.viewManager.deleteAnnotation(annotation.cfi)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe();
          const updatedAnnotations = annotations.filter(a => a.id !== annotationId);
          this._annotations.set(updatedAnnotations);
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
    this._isOpen.set(false);
    this._activeTab.set('chapters');
    this._bookInfo.set({id: null, title: '', authors: '', coverUrl: null});
    this._chapters.set([]);
    this._bookmarks.set([]);
    this._annotations.set([]);
    this._expandedChapters.clear();
  }
}
