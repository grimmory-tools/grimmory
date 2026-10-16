import {NgTemplateOutlet} from '@angular/common';
import {Component, computed, effect, inject, untracked, viewChild} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';

import {BrowseEmptyDef, BrowseGridItemDef, BrowseGridSkeletonDef} from '../../../shared/browse/grid/grid.directives';
import {createBrowsePage} from '../../../shared/browse/browse-page';
import {
  BrowsePageComponent,
  BrowsePageTableDef,
  BrowsePageToolbarDef,
  type BrowsePageMessages,
} from '../../../shared/browse/browse-page.component';
import {createBrowseUrlState} from '../../../shared/browse/url-state';
import {CoverScalePreferenceService} from '../../../shared/service/cover-scale-preference.service';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {heldSignal} from '../../../shared/util/held-signal';
import {BookMenuComponent} from '../components/book-menu/book-menu.component';
import {BookCardSkeletonComponent} from '../components/cards/book-card-skeleton.component';
import {BookCardComponent, bookCardCoverSrc} from '../components/cards/book-card.component';
import {bookCardHeightForWidth} from '../components/cards/book-card.layout';
import {
  injectPendingBookDeletions,
  injectPendingBookProgressResets,
  injectPendingBookReadStatuses,
  overlayPendingBookState,
  type PendingBookOverlay,
} from '../data/book-command-pending-state';
import {type BookQueryFacetKey, type BookQuerySortKey} from '../data/book-query-params';
import {BookQueryService} from '../data/book-query.service';
import {type BookSummary} from '../data/book-response.models';
import {BookNavigationService} from '../service/book-navigation.service';
import {LibraryService} from '../service/library.service';
import {BookBrowseBulkBarComponent} from './book-browse-bulk-bar.component';
import {BookBrowseDetailLineService} from './book-browse-detail-line.service';
import {BOOK_FIELDS, type BookDetailLineSortKey} from './book-browse-fields';
import {createBookBrowseLabels} from './book-browse-labels';
import {createBookBrowsePreferences} from './book-browse-preferences';
import {bookBrowseScope, bookBrowseScopePreferenceContext} from './book-browse-scope';
import {bookSortHasDetailLine} from './book-browse-sort';
import {BookBrowseTableComponent} from './book-browse-table.component';
import {BookBrowseToolbarComponent} from './book-browse-toolbar.component';

const BOOK_BROWSE_MESSAGES: BrowsePageMessages = {
  empty: 'browse.book.empty',
  filteredEmpty: 'browse.book.filteredEmpty',
  loadError: 'browse.book.loadError',
  pageError: 'browse.book.pageError',
  searchPlaceholder: 'browse.rail.searchFields',
};

@Component({
  selector: 'app-book-browse-page',
  imports: [
    NgTemplateOutlet,
    TranslocoPipe,
    BrowsePageComponent,
    BrowsePageToolbarDef,
    BrowsePageTableDef,
    BrowseGridItemDef,
    BrowseGridSkeletonDef,
    BrowseEmptyDef,
    BookCardComponent,
    BookCardSkeletonComponent,
    BookMenuComponent,
    BookBrowseToolbarComponent,
    BookBrowseTableComponent,
    BookBrowseBulkBarComponent,
  ],
  templateUrl: './book-browse-page.component.html',
})
export class BookBrowsePageComponent {
  private readonly bookQuery = inject(BookQueryService);
  private readonly route = inject(ActivatedRoute);
  private readonly detailLines = inject(BookBrowseDetailLineService);
  private readonly libraryService = inject(LibraryService);
  protected readonly bookNavigation = inject(BookNavigationService);
  private readonly urlHelper = inject(UrlHelperService);
  private readonly coverScale = inject(CoverScalePreferenceService);
  private readonly pendingReadStatuses = injectPendingBookReadStatuses();
  private readonly pendingProgressResets = injectPendingBookProgressResets();
  protected readonly pendingDeletions = injectPendingBookDeletions();
  protected readonly messages = BOOK_BROWSE_MESSAGES;

  private readonly tableRef = viewChild(BookBrowseTableComponent);
  protected readonly bookMenu = viewChild(BookMenuComponent);
  protected readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);

  protected readonly urlState = createBrowseUrlState(BOOK_FIELDS);
  private readonly routeParamMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });
  private readonly scope = computed(() =>
    bookBrowseScope(this.routeParamMap(), this.route.snapshot.data),
  );
  private readonly preferenceContext = computed(() => bookBrowseScopePreferenceContext(this.scope()));

  protected readonly labels = createBookBrowseLabels(this.scope);
  protected readonly preferences = createBookBrowsePreferences({
    context: this.preferenceContext,
    urlState: this.urlState,
  });

  protected readonly page = createBrowsePage<BookSummary, BookQueryFacetKey, BookQuerySortKey>({
    source: this.bookQuery,
    fields: BOOK_FIELDS,
    urlState: this.urlState,
    facetDefinitions: this.labels.facetDefinitions,
    pinned: this.scope,
    defaultSortTerms: available => this.preferences.defaultSortTerms(available),
    artworkUrls: (books, lastVisibleIndex) => books.slice(0, lastVisibleIndex + 1).flatMap(book => {
      const url = bookCardCoverSrc(book, book.primaryFile, this.urlHelper);
      return url ? [url] : [];
    }),
    clickAwayExempt: 'app-book-card, app-book-browse-table',
    selectionSuspended: () => this.menuOpenBookId() !== null,
    scrollToTop: () => this.tableRef()?.scrollToTop(),
    density: {
      baseWidth: 135,
      scale: this.coverScale.scalePreference,
      mobileColumnsStorageKey: 'mobileColumnsPreference',
    },
  });

  private readonly pendingBookOverlay = computed<PendingBookOverlay>(() => ({
    readStatuses: this.pendingReadStatuses(),
    progressResets: this.pendingProgressResets(),
  }));

  protected readonly books = computed<readonly BookSummary[]>(() => {
    const books = this.page.results.items();
    const overlay = this.pendingBookOverlay();
    if (overlay.readStatuses.size === 0
      && overlay.progressResets.size === 0) {
      return books;
    }
    return books.map(book => overlayPendingBookState(book, overlay));
  });

  private readonly detailLineKey = heldSignal<BookDetailLineSortKey | null>(
    () => {
      const primary = this.page.sortTerms().at(0);
      return primary && !this.page.isDefaultSort(this.page.sortTerms()) && bookSortHasDetailLine(primary.key)
        ? primary.key
        : null;
    },
    this.page.results.showingPreviousResults,
  );
  protected readonly squareCovers = computed(() => this.squareCoversFor(this.books()));
  protected readonly metaLines = computed<2 | 3>(() => this.detailLineKey() === null ? 2 : 3);
  protected readonly estimateItemHeight = (width: number): number =>
    bookCardHeightForWidth(width, {square: this.squareCovers(), metaLines: this.metaLines()});

  constructor() {
    effect(() => {
      const range = this.tableRef()?.renderedRange();
      if (range) {
        untracked(() => this.page.results.onRenderedRange(range));
      }
    });
  }

  protected detailLineFor(book: BookSummary): string | null {
    const key = this.detailLineKey();
    return key === null ? null : this.detailLines.lineFor(key, book);
  }

  private squareCoversFor(books: readonly BookSummary[]): boolean {
    const scope = this.scope();
    if (scope?.kind === 'library') {
      const formats = this.libraryService.libraries()
        .find(library => library.id === scope.entityId)?.allowedFormats;
      if (formats?.length === 1 && formats[0] === 'AUDIOBOOK') {
        return true;
      }
    }
    return books.length > 0
      && books.every(book => book.primaryFile?.bookType === 'AUDIOBOOK');
  }

  protected onBookDetailRequested(book: BookSummary): void {
    this.bookNavigation.openBook(book.id, this.books().map(presented => presented.id));
  }
}
