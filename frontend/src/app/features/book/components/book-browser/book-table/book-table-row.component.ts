import {ChangeDetectionStrategy, Component, computed, inject, input, output} from '@angular/core';
import {CdkOverlayOrigin} from '@angular/cdk/overlay';
import {DatePipe, NgClass} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterLink, UrlTree} from '@angular/router';
import {TranslocoDirective} from '@jsverse/transloco';
import {Checkbox} from 'primeng/checkbox';
import {Tooltip} from 'primeng/tooltip';
import {Book, BookMetadata} from '../../../model/book.model';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {CoverPlaceholderComponent} from '../../../../../shared/components/cover-generator/cover-generator.component';
import {RatingComponent} from '../../../../../shared/components/rating/rating.component';
import {RATING_FIELDS, isMetadataFullyLocked} from './book-table.helpers';

interface BookTableCellLink {
  url: string | UrlTree;
  anchor?: string | number | null;
}

interface BookTableBaseCellView {
  id: string;
  className: string;
}

interface BookTableActionCellView extends BookTableBaseCellView {
  kind: 'select' | 'lock' | 'cover';
}

interface BookTableReadStatusCellView extends BookTableBaseCellView {
  kind: 'readStatus';
  label: string;
}

interface BookTableRatingCellView extends BookTableBaseCellView {
  kind: 'rating';
  rating: number | null;
  display: string;
}

interface BookTableClickableCellView extends BookTableBaseCellView {
  kind: 'clickable';
  links: BookTableCellLink[];
  title: string | number;
}

interface BookTableTextCellView extends BookTableBaseCellView {
  kind: 'text';
  value: string | number;
}

type BookTableCellView =
  | BookTableActionCellView
  | BookTableReadStatusCellView
  | BookTableRatingCellView
  | BookTableClickableCellView
  | BookTableTextCellView;
type BookTableCellKind = BookTableCellView['kind'];

export interface BookTableRowCoverPreview {
  book: Book;
  metadata: BookMetadata;
  origin: CdkOverlayOrigin;
  coverUrl: string | null;
  useSquareCover: boolean;
}

export interface BookTableSelectionChange {
  book: Book;
  checked: boolean;
}

const CLICKABLE_FIELDS = new Set(['title', 'authors', 'publisher', 'seriesName', 'categories', 'language']);
const CLICKABLE_FILTER_KEYS: Record<string, string> = {
  authors: 'author',
  publisher: 'publisher',
  categories: 'category',
  language: 'language',
};

@Component({
  selector: 'app-book-table-row',
  standalone: true,
  templateUrl: './book-table-row.component.html',
  styleUrls: ['./book-table-row.component.scss'],
  imports: [
    CdkOverlayOrigin,
    Checkbox,
    CoverPlaceholderComponent,
    FormsModule,
    NgClass,
    RatingComponent,
    RouterLink,
    Tooltip,
    TranslocoDirective,
  ],
  providers: [DatePipe],
  host: {
    role: 'row',
    '[attr.aria-rowindex]': 'rowIndex() + 2',
    '[attr.aria-selected]': 'isSelected()',
    '[class.book-table-row-striped]': 'rowIndex() % 2 === 0',
    '[style.height.px]': 'rowHeight()',
    '[style.transform]': '"translateY(" + rowStart() + "px)"',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookTableRowComponent {
  readonly book = input.required<Book>();
  readonly cellIds = input.required<string[]>();
  readonly rowIndex = input.required<number>();
  readonly rowHeight = input.required<number>();
  readonly rowStart = input.required<number>();
  readonly isSelected = input(false);
  readonly isLockPending = input(false);
  readonly useSquareCovers = input(false);

  readonly selectionChange = output<BookTableSelectionChange>();
  readonly metadataLockToggleRequested = output<BookMetadata>();
  readonly coverPreviewShow = output<BookTableRowCoverPreview>();
  readonly coverPreviewHide = output<number>();

  private readonly datePipe = inject(DatePipe);
  private readonly readStatusHelper = inject(ReadStatusHelper);
  protected readonly urlHelper = inject(UrlHelperService);

  readonly metadata = computed<BookMetadata>(() => {
    const book = this.book();
    return book.metadata ?? {bookId: book.id};
  });
  readonly useSquareCover = computed(() => this.isAudiobook() || this.useSquareCovers());
  readonly coverUrl = computed(() => {
    const book = this.book();
    const metadata = this.metadata();
    return this.useSquareCover()
      ? this.urlHelper.getAudiobookThumbnailUrl(book.id, metadata.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(book.id, metadata.coverUpdatedOn);
  });
  readonly readStatusClass = computed(() => this.readStatusHelper.getReadStatusClass(this.book().readStatus));
  readonly readStatusIcon = computed(() => this.readStatusHelper.getReadStatusIcon(this.book().readStatus));
  readonly readStatusTooltip = computed(() => this.readStatusHelper.getReadStatusTooltip(this.book().readStatus));
  readonly showReadStatus = computed(() => this.readStatusHelper.shouldShowStatusIcon(this.book().readStatus));
  readonly isMetadataLocked = computed(() => isMetadataFullyLocked(this.metadata()));
  readonly cells = computed(() => this.cellIds().map(field => this.createCellView(field)));

  onSelectionChange(checked: boolean): void {
    this.selectionChange.emit({book: this.book(), checked});
  }

  showCoverPreview(origin: CdkOverlayOrigin): void {
    this.coverPreviewShow.emit({
      book: this.book(),
      metadata: this.metadata(),
      origin,
      coverUrl: this.coverUrl(),
      useSquareCover: this.useSquareCover(),
    });
  }

  private createCellView(field: string): BookTableCellView {
    const metadata = this.metadata();
    const book = this.book();
    const kind = this.getCellKind(field);
    const cell = {
      id: field,
      className: this.getCellClass(field),
    };

    switch (kind) {
      case 'select':
      case 'lock':
      case 'cover':
        return {...cell, kind};
      case 'readStatus':
        return {...cell, kind, label: this.readStatusHelper.getReadStatusTooltip(book.readStatus)};
      case 'rating': {
        const rating = this.getRatingValue(metadata, field);
        return {...cell, kind, rating, display: rating === null ? '' : rating.toFixed(1)};
      }
      case 'clickable':
        return {...cell, kind, links: this.getCellClickableValue(metadata, book, field), title: this.getCellValue(metadata, book, field)};
      case 'text':
        return {...cell, kind, value: this.getCellValue(metadata, book, field)};
    }
  }

  getCellTitle(cell: BookTableCellView): string | number | null {
    switch (cell.kind) {
      case 'readStatus':
        return cell.label;
      case 'clickable':
        return cell.title;
      case 'text':
        return cell.value;
      default:
        return null;
    }
  }

  private getCellKind(field: string): BookTableCellKind {
    if (field === 'select') return 'select';
    if (field === 'lock') return 'lock';
    if (field === 'cover') return 'cover';
    if (field === 'readStatus') return 'readStatus';
    if (RATING_FIELDS.has(field)) return 'rating';
    if (CLICKABLE_FIELDS.has(field)) return 'clickable';
    return 'text';
  }

  private getCellClass(field: string): string {
    if (field === 'select' || field === 'lock') return 'cell-action';
    if (field === 'cover') return 'cell-cover';
    if (field === 'readStatus') return 'cell-status';
    if (RATING_FIELDS.has(field)) return 'cell-rating';
    if (field.endsWith('ReviewCount') || field === 'pageCount' || field === 'seriesNumber') return 'cell-number';
    return 'cell-text';
  }

  private getCellClickableValue(metadata: BookMetadata, book: Book, field: string): BookTableCellLink[] {
    let values: string[];
    switch (field) {
      case 'title':
        return [{
          url: this.urlHelper.getBookUrl(book),
          anchor: metadata.title ?? book.fileName
        }];
      case 'categories':
        values = metadata.categories ?? [];
        break;
      case 'authors':
        values = metadata.authors ?? [];
        break;
      case 'seriesName': {
        const seriesName = metadata.seriesName;
        if (!seriesName) return [];
        return [{
          url: this.urlHelper.filterBooksBy('series', seriesName),
          anchor: seriesName
        }];
      }
      default: {
        const value = this.getMetadataValue(metadata, field);
        values = typeof value === 'string' && value ? [value] : [];
      }
    }

    return values.map(item => ({
      url: this.urlHelper.filterBooksBy(CLICKABLE_FILTER_KEYS[field] ?? field, item),
      anchor: item
    }));
  }

  private getCellValue(metadata: BookMetadata, book: Book, field: string): string | number {
    switch (field) {
      case 'title':
        return metadata.title ?? '';
      case 'authors':
        return this.getAuthorNames(metadata.authors);
      case 'publisher':
        return metadata.publisher ?? '';
      case 'seriesName':
        return metadata.seriesName ?? '';
      case 'seriesNumber':
        return metadata.seriesNumber ?? '';
      case 'categories':
        return this.getGenres(metadata.categories);
      case 'publishedDate':
        return metadata.publishedDate ? this.datePipe.transform(metadata.publishedDate, 'dd-MMM-yyyy') ?? '' : '';
      case 'lastReadTime':
        return book.lastReadTime ? this.datePipe.transform(book.lastReadTime, 'dd-MMM-yyyy') ?? '' : '';
      case 'addedOn':
        return book.addedOn ? this.datePipe.transform(book.addedOn, 'dd-MMM-yyyy') ?? '' : '';
      case 'fileName':
        return book.primaryFile?.fileName ?? '';
      case 'fileSizeKb':
        return this.formatFileSize(book.fileSizeKb);
      case 'language':
        return metadata.language ?? '';
      case 'pageCount':
        return metadata.pageCount ?? '';
      case 'amazonReviewCount':
      case 'goodreadsReviewCount':
      case 'hardcoverReviewCount':
        return metadata[field] ?? '';
      case 'isbn':
        return metadata.isbn13 ?? metadata.isbn10 ?? '';
      default:
        return '';
    }
  }

  private getAuthorNames(authors?: string[] | null): string {
    return authors?.join(', ') || '';
  }

  private getGenres(genres?: string[] | null): string {
    return genres?.join(', ') || '';
  }

  private formatFileSize(kb?: number): string {
    if (kb == null || Number.isNaN(kb)) return '-';
    if (kb < 1024) {
      return `${Number.isInteger(kb) ? kb.toFixed(0) : kb.toFixed(1)} KB`;
    }
    if (kb < 1024 * 1024) {
      return `${(kb / 1024).toFixed(1)} MB`;
    }
    return `${(kb / (1024 * 1024)).toFixed(1)} GB`;
  }

  private getRatingValue(metadata: BookMetadata, field: string): number | null {
    const rating = this.getMetadataValue(metadata, field);
    return typeof rating === 'number' ? rating : null;
  }

  private getMetadataValue(metadata: BookMetadata, field: string): unknown {
    return this.isKeyOf(metadata, field) ? metadata[field] : undefined;
  }

  private isKeyOf<T extends object>(value: T, field: PropertyKey): field is keyof T {
    return Object.prototype.hasOwnProperty.call(value, field);
  }

  private isAudiobook(): boolean {
    return this.book().primaryFile?.bookType === 'AUDIOBOOK';
  }
}
