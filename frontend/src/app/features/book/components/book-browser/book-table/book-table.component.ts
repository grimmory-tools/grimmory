import {ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, viewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {CdkConnectedOverlay, ConnectedPosition} from '@angular/cdk/overlay';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {finalize} from 'rxjs';
import {ColumnDef, ColumnSizingState, Header, createAngularTable, functionalUpdate, getCoreRowModel} from '@tanstack/angular-table';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {injectVirtualizer} from '@tanstack/angular-virtual';
import {Checkbox} from 'primeng/checkbox';
import {Book, BookMetadata} from '../../../model/book.model';
import {RouteScrollPositionService} from '../../../../../shared/service/route-scroll-position.service';
import {CoverPlaceholderComponent} from '../../../../../shared/components/cover-generator/cover-generator.component';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {patchAppBooksMetadataLockInCache} from '../../../service/book-query-cache';
import {MessageService} from 'primeng/api';
import {BookSelectionService} from '../book-selection.service';
import {BookTableRowComponent, type BookTableRowCoverPreview, type BookTableSelectionChange} from './book-table-row.component';
import {RATING_FIELDS, isMetadataFullyLocked} from './book-table.helpers';

interface BookTableColumn {
  field: string;
  header: string;
}

const ROW_HEIGHT = 46;
const RENDER_OVERSCAN_ROWS = 16;
const RESIZE_OVERSCAN_ROWS = 2;
const PAGE_LOAD_AHEAD_ROWS = 40;
const DEFAULT_COLUMN_SIZES: Record<string, number> = {
  readStatus: 72,
  title: 260,
  fileName: 240,
  authors: 220,
  categories: 220,
  pageCount: 120,
  seriesNumber: 120,
};
const DEFAULT_RATING_COLUMN_SIZE = 148;
const DEFAULT_REVIEW_COUNT_COLUMN_SIZE = 120;
const DEFAULT_TEXT_COLUMN_SIZE = 180;
const MIN_COLUMN_SIZES: Record<string, number> = {
  readStatus: 64,
  pageCount: 96,
  seriesNumber: 96,
};
const DEFAULT_MIN_COLUMN_SIZE = 120;
const MAX_COLUMN_SIZES: Record<string, number> = {
  title: 420,
  fileName: 420,
  authors: 360,
  categories: 360,
};
const DEFAULT_MAX_COLUMN_SIZE = 280;
const COVER_OVERLAY_POSITIONS: ConnectedPosition[] = [
  {originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: 8},
  {originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center', offsetX: -8},
  {originX: 'end', originY: 'top', overlayX: 'start', overlayY: 'top', offsetX: 8},
  {originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom', offsetX: 8},
];

@Component({
  selector: 'app-book-table',
  standalone: true,
  templateUrl: './book-table.component.html',
  imports: [
    CdkConnectedOverlay,
    BookTableRowComponent,
    Checkbox,
    FormsModule,
    TranslocoDirective,
    CoverPlaceholderComponent
  ],
  styleUrls: ['./book-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookTableComponent {
  readonly selectAllRequested = output<void>();
  readonly loadNextPage = output<void>();

  readonly books = input<Book[]>([]);
  readonly visibleColumns = input<BookTableColumn[]>([]);
  readonly virtualRowCount = input(0);
  readonly loadedBookCount = input<number | null>(null);
  readonly isFetchingNextPage = input(false);
  readonly useSquareCovers = input(false);
  readonly bookQueryToken = input<unknown>(undefined);

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly scrollService = inject(RouteScrollPositionService);
  protected readonly bookSelectionService = inject(BookSelectionService);
  private readonly bookMetadataManageService = inject(BookMetadataManageService);
  private readonly messageService = inject(MessageService);
  private readonly queryClient = inject(QueryClient);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');
  private readonly initialScrollOffset = () => this.scrollService.getPosition(this.scrollService.keyFor(this.activatedRoute, 'table')) ?? 0;

  private readonly columnSizing = signal<ColumnSizingState>({});
  protected readonly coverPreview = signal<BookTableRowCoverPreview | null>(null);
  private readonly pendingLockBookIds = signal<Set<number>>(new Set());

  protected readonly coverOverlayPositions = COVER_OVERLAY_POSITIONS;

  private readonly columnDefs = computed<ColumnDef<Book>[]>(() => [
    {id: 'select', header: '', size: 42, minSize: 42, maxSize: 42, enableResizing: false},
    {id: 'lock', header: '', size: 42, minSize: 42, maxSize: 42, enableResizing: false},
    {id: 'cover', header: '', size: 56, minSize: 56, maxSize: 56, enableResizing: false},
    ...this.visibleColumns().map(column => ({
      id: column.field,
      header: column.header,
      size: this.defaultColumnSize(column.field),
      minSize: this.minColumnSize(column.field),
      maxSize: this.maxColumnSize(column.field),
    })),
  ]);

  readonly table = createAngularTable(() => ({
    data: this.books(),
    columns: this.columnDefs(),
    getCoreRowModel: getCoreRowModel(),
    getRowId: book => String(book.id),
    columnResizeMode: 'onChange',
    state: {
      columnSizing: this.columnSizing(),
    },
    onColumnSizingChange: updater => {
      this.columnSizing.update(current => functionalUpdate(updater, current));
    },
  }));

  readonly visibleCellIds = computed(() => ['select', 'lock', 'cover', ...this.visibleColumns().map(column => column.field)]);
  readonly ariaRowCount = computed(() => this.rowCount() + 1);
  readonly ariaColumnCount = computed(() => this.visibleCellIds().length);
  readonly isColumnResizing = computed(() => Boolean(this.table.getState().columnSizingInfo.isResizingColumn));
  readonly rowCount = computed(() => Math.max(this.virtualRowCount(), this.books().length));
  readonly columnSizeVars = computed<Record<string, number | string>>(() => {
    this.columnSizing();
    this.visibleColumns();

    const headers = this.table.getFlatHeaders();
    const styles = headers.reduce<Record<string, number | string>>((columnStyles, header) => {
      columnStyles[`--book-table-col-${header.column.id}-size`] = header.column.getSize();
      return columnStyles;
    }, {});

    styles['--book-table-grid-template'] = this.table.getVisibleFlatColumns()
      .map(column => `calc(var(--book-table-col-${column.id}-size) * 1px)`)
      .join(' ');

    return styles;
  });
  readonly tableWidth = computed(() => {
    this.columnSizing();
    this.visibleColumns();
    return this.table.getVisibleFlatColumns().reduce((width, column) => width + column.getSize(), 0);
  });
  readonly allLoadedBooksSelected = computed(() => {
    const books = this.books();
    return books.length > 0 && books.every(book => this.bookSelectionService.isBookSelected(book));
  });
  readonly someBooksSelected = computed(() => {
    return this.bookSelectionService.selectedCount() > 0 && !this.allLoadedBooksSelected();
  });

  readonly rowVirtualizer = injectVirtualizer<HTMLElement, HTMLElement>(() => ({
    scrollElement: this.scrollElement(),
    count: this.rowCount(),
    estimateSize: () => ROW_HEIGHT,
    overscan: this.isColumnResizing() ? RESIZE_OVERSCAN_ROWS : RENDER_OVERSCAN_ROWS,
    initialOffset: this.initialScrollOffset,
    getItemKey: index => this.books()[index]?.id ?? `loading-${index}`,
  }));

  constructor() {
    this.scrollService.trackRoute({
      scrollElement: this.scrollElement,
      route: this.activatedRoute,
      destroyRef: this.destroyRef,
      keySuffix: 'table',
      dismissOverlaysBeforeSave: true,
      beforeSave: () => this.clearCoverPreview(),
    });
  }

  private readonly measureRowsEffect = effect(() => {
    this.rowCount();
    this.visibleColumns();
    queueMicrotask(() => this.rowVirtualizer.measure());
  });

  private lastLoadRequestLoadedBookCount = 0;
  private lastSeenQueryToken: unknown;
  private readonly paginatorEffect = effect(() => {
    const queryToken = this.bookQueryToken();
    if (queryToken !== this.lastSeenQueryToken) {
      this.lastLoadRequestLoadedBookCount = 0;
      this.lastSeenQueryToken = queryToken;
    }

    const items = this.books();
    const loadedBookCount = this.loadedBookCount() ?? items.length;
    const lastVirtualItem = this.rowVirtualizer.getVirtualItems().at(-1);
    if (!lastVirtualItem || items.length === 0) return;
    if (lastVirtualItem.index < items.length - PAGE_LOAD_AHEAD_ROWS) return;
    if (items.length >= this.virtualRowCount() || this.isFetchingNextPage()) return;
    if (this.lastLoadRequestLoadedBookCount === loadedBookCount) return;

    this.lastLoadRequestLoadedBookCount = loadedBookCount;
    this.loadNextPage.emit();
  });

  private defaultColumnSize(field: string): number {
    const defaultSize = DEFAULT_COLUMN_SIZES[field];
    if (defaultSize !== undefined) return defaultSize;
    if (RATING_FIELDS.has(field)) return DEFAULT_RATING_COLUMN_SIZE;
    if (field.endsWith('ReviewCount')) return DEFAULT_REVIEW_COUNT_COLUMN_SIZE;
    return DEFAULT_TEXT_COLUMN_SIZE;
  }

  private minColumnSize(field: string): number {
    return MIN_COLUMN_SIZES[field] ?? DEFAULT_MIN_COLUMN_SIZE;
  }

  private maxColumnSize(field: string): number {
    return MAX_COLUMN_SIZES[field] ?? DEFAULT_MAX_COLUMN_SIZE;
  }

  isRowSelected(book: Book): boolean {
    return this.bookSelectionService.isBookSelected(book);
  }

  startColumnResize(header: Header<Book, unknown>, event: MouseEvent | TouchEvent): void {
    event.stopPropagation();
    if (event.cancelable) {
      event.preventDefault();
    }
    header.getResizeHandler()(event);
  }

  scrollToTop(): void {
    const scrollElement = this.scrollElement()?.nativeElement;
    if (scrollElement) {
      scrollElement.scrollTop = 0;
    }
    this.rowVirtualizer.scrollToOffset(0);
  }

  onHeaderCheckboxChange(checked: boolean): void {
    if (checked) {
      this.selectAllRequested.emit();
      return;
    }

    this.bookSelectionService.deselectAll();
  }

  onBookSelectionChange(change: BookTableSelectionChange): void {
    this.bookSelectionService.handleBookSelection(change.book, change.checked);
  }

  showCoverPreview(preview: BookTableRowCoverPreview): void {
    this.coverPreview.set(preview);
  }

  clearCoverPreview(): void {
    this.coverPreview.set(null);
  }

  hideCoverPreview(bookId: number): void {
    if (this.coverPreview()?.book.id === bookId) {
      this.clearCoverPreview();
    }
  }

  isMetadataLockPending(bookId: number): boolean {
    return this.pendingLockBookIds().has(bookId);
  }

  toggleMetadataLock(metadata: BookMetadata): void {
    if (this.isMetadataLockPending(metadata.bookId)) return;

    const lockAction = isMetadataFullyLocked(metadata) ? 'UNLOCK' : 'LOCK';
    this.setMetadataLockPending(metadata.bookId, true);

    this.bookMetadataManageService.toggleAllLock(new Set([metadata.bookId]), lockAction)
      .pipe(
        finalize(() => this.setMetadataLockPending(metadata.bookId, false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          patchAppBooksMetadataLockInCache(this.queryClient, metadata.bookId, lockAction === 'LOCK');
          this.messageService.add({
            severity: 'success',
            summary: lockAction === 'LOCK' ? this.t.translate('book.table.toast.metadataLockedSummary') : this.t.translate('book.table.toast.metadataUnlockedSummary'),
            detail: lockAction === 'LOCK' ? this.t.translate('book.table.toast.metadataLockedDetail') : this.t.translate('book.table.toast.metadataUnlockedDetail'),
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: lockAction === 'LOCK' ? this.t.translate('book.table.toast.lockFailedSummary') : this.t.translate('book.table.toast.unlockFailedSummary'),
            detail: lockAction === 'LOCK' ? this.t.translate('book.table.toast.lockFailedDetail') : this.t.translate('book.table.toast.unlockFailedDetail'),
          });
        }
      });
  }

  private setMetadataLockPending(bookId: number, pending: boolean): void {
    this.pendingLockBookIds.update(current => {
      if (current.has(bookId) === pending) return current;

      const next = new Set(current);
      if (pending) {
        next.add(bookId);
      } else {
        next.delete(bookId);
      }
      return next;
    });
  }
}
