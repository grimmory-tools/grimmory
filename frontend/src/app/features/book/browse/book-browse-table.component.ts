import {LocationStrategy, NgTemplateOutlet} from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  contentChild,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {
  type Cell,
  type ColumnDef,
  type ColumnSizingState,
  type Header,
  type SortingState,
  columnResizingFeature,
  columnSizingFeature,
  functionalUpdate,
  injectTable,
  rowSortingFeature,
  tableFeatures,
} from '@tanstack/angular-table';
import {injectVirtualizer} from '@tanstack/angular-virtual';
import {LucideArrowDown, LucideArrowUp, LucideEllipsisVertical} from '@lucide/angular';

import {CoverComponent} from '../../../shared/components/cover/cover.component';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {contextMenuRequest, type ContextMenuRequest} from '../../../shared/ui/menu/app-menu.component';
import {isPlainLeftClick} from '../../../shared/util/pointer-gestures';
import {runOnNextTwoFrames} from '../../../shared/util/frames';
import {RouteScrollPositionService} from '../../../shared/service/route-scroll-position.service';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppCheckboxComponent} from '../../../shared/ui/checkbox/app-checkbox.component';
import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuTriggerDirective} from '../../../shared/ui/menu/app-menu-trigger.directive';
import {AppTagComponent} from '../../../shared/ui/tag/app-tag.component';
import {cn} from '../../../shared/ui/cn';
import {bookCardCoverSrc} from '../components/cards/book-card.component';
import {BrowseEmptyDef} from '../../../shared/browse/grid/grid.directives';
import {sameRenderedRange, type BrowseRenderedRange, type BrowseStatus} from '../../../shared/browse/results';
import {createBrowseSkeletonDelay} from '../../../shared/browse/skeleton-delay';
import {type BrowseSelection} from '../../../shared/browse/selection';
import {isBookQuerySortKey, type BookSortTerm} from '../data/book-query-params';
import {type BookSummary} from '../data/book-response.models';
import {bookTitle, type BookColumnValue} from './book-browse-fields';
import {
  BOOK_EMPTY_VALUE,
  bookColumnKind,
  bookColumnSizing,
  bookColumnSortKey,
  bookColumnValue,
  formatBookValue,
  type BookColumnOption,
} from './book-browse-columns';
import {bookFacetLinks, type BookFacetLink} from './book-browse-facet-definitions';
import {type BookSortOption} from './book-browse-sort';
import {BookBrowseColumnWidthPreferenceService} from './book-browse-column-width-preference.service';

interface BookBrowseTableMenuRequest {
  book: BookSummary;
  request: ContextMenuRequest;
}

const ROW_HEIGHT = 54;
const HEADER_HEIGHT = 42;
const RENDER_OVERSCAN = 10;
const SELECT_COLUMN_WIDTH = 44;
const TABLE_SKELETON_ROWS = 12;

const features = tableFeatures({
  rowSortingFeature,
  columnSizingFeature,
  columnResizingFeature,
});

type BookBrowseHeader = Header<typeof features, BookSummary, unknown>;
type BookBrowseCell = Cell<typeof features, BookSummary, unknown>;

const HEADER_CELL_CLASS =
  'relative box-border h-[var(--book-browse-head-h)] min-w-0 flex-none overflow-hidden bg-page px-3 text-left ' +
  'text-xs font-semibold leading-[var(--book-browse-head-h)] whitespace-nowrap text-text-secondary';

const SORT_BUTTON_CLASS =
  '-mx-3 flex min-h-[var(--book-browse-head-h)] w-[calc(100%+1.5rem)] cursor-pointer items-center ' +
  'gap-1.5 border-0 bg-transparent px-3 font-[inherit] text-[inherit] hover:bg-surface-hover hover:text-text ' +
  'focus-visible:-outline-offset-2 focus-visible:outline-2 focus-visible:outline-primary';

const BODY_CELL_CLASS =
  'box-border flex h-[var(--book-browse-row-h)] min-w-0 flex-none items-center overflow-hidden border-b ' +
  'border-border/70 bg-page px-3 group-hover/row:bg-surface-hover ' +
  'group-data-[menu-open=true]/row:bg-surface-hover group-data-[selected=true]/row:bg-active-surface!';

@Component({
  selector: 'app-book-browse-table',
  imports: [
    NgTemplateOutlet,
    AppButtonComponent,
    AppCheckboxComponent,
    AppMenuComponent,
    AppMenuItemComponent,
    AppMenuTriggerDirective,
    AppTagComponent,
    CoverComponent,
    TranslocoPipe,
    LucideArrowDown,
    LucideArrowUp,
    LucideEllipsisVertical,
  ],
  templateUrl: './book-browse-table.component.html',
  host: {
    class: 'block max-h-full min-h-0 min-w-0 w-full',
    '[class.flex-1]': 'mobile()',
  },
})
export class BookBrowseTableComponent {
  readonly books = input.required<readonly BookSummary[]>();
  readonly status = input.required<BrowseStatus>();
  readonly hasNextPage = input.required<boolean>();
  readonly pendingDeletionIds = input.required<ReadonlySet<number>>();
  readonly visibleColumns = input.required<readonly BookColumnOption[]>();
  readonly sortTerms = input.required<readonly BookSortTerm[]>();
  readonly sortOptions = input.required<readonly BookSortOption[]>();
  readonly mobile = input.required<boolean>();
  readonly selection = input.required<BrowseSelection | null>();
  readonly selectMode = input(false);
  readonly openMenuBookId = input.required<number | null>();
  readonly useSquareCovers = input.required<boolean>();
  readonly nextPageError = input.required<boolean>();
  readonly initialErrorMessage = input.required<string>();
  readonly nextPageErrorMessage = input.required<string>();

  readonly renderedRange = signal<BrowseRenderedRange | null>(null, {equal: sameRenderedRange});
  readonly sortTermsChange = output<readonly BookSortTerm[]>();
  readonly facetRequested = output<BookFacetLink>();
  readonly menuRequested = output<BookBrowseTableMenuRequest>();
  readonly detailRequested = output<BookSummary>();
  readonly retryInitial = output();
  readonly retryNextPage = output();

  readonly emptyDef = contentChild.required(BrowseEmptyDef);

  private readonly transloco = inject(TranslocoService);
  private readonly urlHelper = inject(UrlHelperService);
  private readonly router = inject(Router);
  private readonly locationStrategy = inject(LocationStrategy);
  private readonly route = inject(ActivatedRoute);
  private readonly scrollPosition = inject(RouteScrollPositionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly columnWidthPreference = inject(BookBrowseColumnWidthPreferenceService);

  private readonly initialColumnSizing: ColumnSizingState = this.columnWidthPreference.load();
  private readonly selectColumnVisible = computed(() => this.selection() !== null);
  private readonly sortOptionsById = computed(() =>
    new Map(this.sortOptions().map(option => [option.id, option])));
  private readonly sorting = computed<SortingState>(() =>
    this.sortTerms().map(term => ({id: term.key, desc: term.direction === 'desc'})));
  private readonly columnDefs = computed<ColumnDef<typeof features, BookSummary>[]>(() => [
    ...(this.selectColumnVisible()
      ? [{
          id: 'select',
          size: SELECT_COLUMN_WIDTH,
          minSize: SELECT_COLUMN_WIDTH,
          maxSize: SELECT_COLUMN_WIDTH,
          enableResizing: false,
          enableSorting: false,
        } satisfies ColumnDef<typeof features, BookSummary, BookColumnValue>]
      : []),
    ...this.visibleColumns().map(column => {
      const sortKey = bookColumnSortKey(column.field);
      const sortOption = sortKey ? this.sortOptionsById().get(sortKey) : undefined;
      return {
        id: column.field,
        ...bookColumnSizing(column.field),
        enableSorting: sortOption !== undefined,
        sortDescFirst: sortOption?.defaultDirection === 'desc',
        accessorFn: book => bookColumnValue(book, column.field),
      } satisfies ColumnDef<typeof features, BookSummary, BookColumnValue>;
    }),
  ]);

  protected readonly table = injectTable(() => ({
    features,
    data: this.books(),
    columns: this.columnDefs(),
    manualSorting: true,
    enableSortingRemoval: false,
    columnResizeMode: 'onChange',
    initialState: {columnSizing: this.initialColumnSizing},
    state: {
      sorting: this.sorting(),
    },
    onSortingChange: updater => {
      const options = this.sortOptionsById();
      const terms = functionalUpdate(updater, this.sorting()).flatMap((item): BookSortTerm[] => {
        const direction = item.desc ? 'desc' : 'asc';
        return isBookQuerySortKey(item.id) && options.get(item.id)?.directions.includes(direction)
          ? [{key: item.id, direction}]
          : [];
      });
      this.sortTermsChange.emit(terms);
    },
  }));

  protected readonly headers = computed(() => this.table.getFlatHeaders());
  protected readonly ariaColumnCount = computed(() => this.headers().length);
  protected readonly tableWidth = computed(() => this.table.getTotalSize());
  protected readonly isColumnResizing = computed(() =>
    Boolean(this.table.atoms.columnResizing.get().isResizingColumn),
  );
  protected readonly columnSizeVars = computed<Record<string, string>>(() =>
    this.table.getAllLeafColumns().reduce<Record<string, string>>((styles, column) => {
      styles[`--book-browse-col-${column.id}-size`] = `${column.getSize()}px`;
      return styles;
    }, {'--book-browse-row-h': `${ROW_HEIGHT}px`, '--book-browse-head-h': `${HEADER_HEIGHT}px`}),
  );

  protected readonly headerLabelKeys = computed<Record<string, string>>(() =>
    Object.fromEntries(this.visibleColumns().map(column => [column.field, column.labelKey])));
  protected readonly cellClasses = computed(() =>
    Object.fromEntries(this.headers().map(header => {
      const id = header.column.id;
      const numeric = this.isNumeric(id);
      return [id, {
        header: cn(HEADER_CELL_CLASS, numeric && 'text-right tabular-nums'),
        sortButton: cn(SORT_BUTTON_CLASS, numeric && 'flex-row-reverse'),
        body: cn(BODY_CELL_CLASS, numeric && 'justify-end text-right tabular-nums'),
      }] as const;
    })),
  );
  protected readonly emptyValue = BOOK_EMPTY_VALUE;
  protected readonly bookTitle = bookTitle;

  protected readonly rows = computed(() => this.table.getRowModel().rows);
  private readonly hasRows = computed(() => this.rows().length > 0);
  private readonly skeletonVisible = createBrowseSkeletonDelay(this.status, this.hasRows);
  protected readonly viewState = computed<'empty' | 'initial-error' | 'table'>(() => {
    if (this.hasRows()) {
      return 'table';
    }
    switch (this.status()) {
      case 'error':
        return 'initial-error';
      case 'success':
        return 'empty';
      default:
        return 'table';
    }
  });
  protected readonly rowCount = computed(() => {
    const rows = this.rows();
    if (rows.length > 0) {
      return rows.length + (this.hasNextPage() ? 1 : 0);
    }
    return this.skeletonVisible() ? TABLE_SKELETON_ROWS : 0;
  });
  protected readonly ariaRowCount = computed(() => this.rowCount() + 1);

  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');
  private readonly savedScrollTop =
    this.scrollPosition.getPosition(this.scrollPosition.keyFor(this.route, 'table')) ?? 0;
  private readonly savedScrollLeft =
    this.scrollPosition.getPosition(this.scrollPosition.keyFor(this.route, 'table-x')) ?? 0;
  private scrollRestored = false;
  private readonly viewportHeight = signal(0);
  private readonly overscanRows = computed(() =>
    Math.max(RENDER_OVERSCAN, Math.ceil(this.viewportHeight() / ROW_HEIGHT)),
  );

  protected readonly rowVirtualizer = injectVirtualizer<HTMLElement, HTMLTableRowElement>(() => ({
    scrollElement: this.scrollElement(),
    count: this.rowCount(),
    estimateSize: () => ROW_HEIGHT,
    overscan: this.isColumnResizing() ? 2 : this.overscanRows(),
    getItemKey: index => this.rows()[index]?.original.id ?? `skeleton-${index}`,
    initialOffset: () => this.savedScrollTop,
  }));

  protected readonly paneClass = computed(() => cn(
    'book-browse-table-pane relative isolate box-border max-h-full overflow-auto overscroll-contain ' +
      'bg-page pb-[env(safe-area-inset-bottom)]',
    this.mobile()
      ? '-mx-4 h-full w-[calc(100%+2rem)]'
      : 'rounded-xl border border-border',
  ));

  private selectionShiftKey = false;
  protected readonly selectionActive = computed(() => {
    const selection = this.selection();
    return selection !== null && (selection.active() || this.selectMode());
  });
  protected readonly allSelected = computed(() => this.selection()?.allMatchingSelected() ?? false);
  protected readonly someSelected = computed(() => {
    const selection = this.selection();
    return selection !== null && selection.count() > 0 && !selection.allMatchingSelected();
  });

  protected readonly overflowLinks = signal<readonly BookFacetLink[]>([]);
  protected readonly overflowMenuLabel = signal('');

  constructor() {
    effect(() => {
      const widths = this.table.atoms.columnSizing.get();
      if (!this.isColumnResizing()) {
        this.columnWidthPreference.save(widths);
      }
    });

    this.scrollPosition.trackRoute({
      scrollElement: this.scrollElement,
      route: this.route,
      destroyRef: this.destroyRef,
      keySuffix: 'table',
      beforeSave: () => {
        const element = this.scrollElement()?.nativeElement;
        if (element) {
          this.scrollPosition.savePosition(
            this.scrollPosition.keyFor(this.route, 'table-x'),
            element.scrollLeft,
          );
        }
      },
    });

    effect(onCleanup => {
      const element = this.scrollElement()?.nativeElement;
      if (!element) {
        return;
      }
      const observer = new ResizeObserver(() => this.viewportHeight.set(element.clientHeight));
      observer.observe(element);
      onCleanup(() => observer.disconnect());
    });

    effect(() => {
      const element = this.scrollElement()?.nativeElement;
      if (this.scrollRestored || !element || this.rows().length === 0) {
        return;
      }
      this.scrollRestored = true;
      runOnNextTwoFrames(() => {
        if (this.savedScrollTop > 0) {
          this.rowVirtualizer.scrollToOffset(this.savedScrollTop);
        }
        if (this.savedScrollLeft > 0) {
          element.scrollLeft = this.savedScrollLeft;
        }
      });
    });

    effect(() => {
      const renderedRows = this.rowVirtualizer.getVirtualItems();
      if (renderedRows.length === 0) {
        return;
      }

      const range = {
        start: renderedRows[0].index,
        end: renderedRows[renderedRows.length - 1].index,
      };
      this.renderedRange.set(range);
    });
  }

  scrollToTop(): void {
    const element = this.scrollElement()?.nativeElement;
    if (element) {
      element.scrollTop = 0;
    }
    this.rowVirtualizer.scrollToOffset(0);
  }

  protected columnWidth(field: string): string {
    return `var(--book-browse-col-${field}-size)`;
  }

  private isNumeric(field: string): boolean {
    const kind = bookColumnKind(field);
    return kind === 'number' || kind === 'rating' || kind === 'fileSize';
  }

  protected primaryAriaSort(header: BookBrowseHeader): 'ascending' | 'descending' | null {
    if (header.column.getSortIndex() !== 0) {
      return null;
    }
    const sorted = header.column.getIsSorted();
    if (!sorted) {
      return null;
    }
    return sorted === 'asc' ? 'ascending' : 'descending';
  }

  protected startResize(header: BookBrowseHeader, event: MouseEvent | TouchEvent): void {
    event.stopPropagation();
    header.getResizeHandler()(event);
  }

  protected resetWidth(header: BookBrowseHeader, event: MouseEvent): void {
    event.stopPropagation();
    header.column.resetSize();
  }

  protected authors(book: BookSummary): string[] {
    return book.metadata?.authors ?? [];
  }

  protected coverUrl(book: BookSummary): string | null {
    return bookCardCoverSrc(book, book.primaryFile, this.urlHelper);
  }

  protected squareCover(book: BookSummary): boolean {
    return this.useSquareCovers() || book.primaryFile?.bookType === 'AUDIOBOOK';
  }

  protected cellText(cell: BookBrowseCell): string {
    return formatBookValue(
      bookColumnKind(cell.column.id), cell.getValue<BookColumnValue>(), key => this.transloco.translate(key),
    );
  }

  protected cellLinks(book: BookSummary, field: string): readonly BookFacetLink[] {
    return bookFacetLinks(book, field);
  }

  protected rememberSelectionPointer(event: MouseEvent): void {
    event.stopPropagation();
    this.selectionShiftKey = event.shiftKey;
  }

  protected stopPropagation(event: Event): void {
    event.stopPropagation();
  }

  protected changeHeaderSelection(checked: boolean): void {
    if (checked) {
      this.selection()?.selectAll();
    } else {
      this.selection()?.clear();
    }
  }

  protected toggleRowSelection(book: BookSummary, index: number): void {
    this.selection()?.toggle(book, index, this.selectionShiftKey);
    this.selectionShiftKey = false;
  }

  protected isSelected(book: BookSummary): boolean {
    return this.selection()?.isSelected(book.id) ?? false;
  }

  protected onRowContextMenu(book: BookSummary | undefined, event: MouseEvent): void {
    if (!book || this.mobile()) {
      return;
    }
    event.preventDefault();
    this.menuRequested.emit({book, request: contextMenuRequest(event)});
  }

  protected onRowMenu(book: BookSummary, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.menuRequested.emit({book, request: contextMenuRequest(event)});
  }

  protected detailHref(book: BookSummary): string {
    return this.locationStrategy.prepareExternalUrl(
      this.router.serializeUrl(this.router.createUrlTree(['/book', book.id])),
    );
  }

  protected onDetailClick(book: BookSummary, event: MouseEvent): void {
    if (!isPlainLeftClick(event)) {
      return;
    }
    event.preventDefault();
    this.detailRequested.emit(book);
  }

  protected prepareOverflowMenu(
    links: readonly BookFacetLink[],
    field: string,
  ): void {
    this.overflowLinks.set(links.slice(1));
    this.overflowMenuLabel.set(this.moreLinksLabel(field, links.length - 1));
  }

  protected prepareOverflowMenuOnKey(
    event: KeyboardEvent,
    links: readonly BookFacetLink[],
    field: string,
  ): void {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp' && event.key !== 'Enter' && event.key !== ' ') {
      return;
    }
    this.prepareOverflowMenu(links, field);
  }

  protected moreLinksLabel(field: string, count: number): string {
    const column = this.transloco.translate(this.headerLabelKeys()[field]);
    return this.transloco.translate('browse.table.moreValues', {count, column});
  }
}
