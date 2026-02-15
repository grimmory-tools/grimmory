import {Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {UpperCasePipe} from '@angular/common';
import {Book, BookRecommendation, BookType, FileInfo} from '../../../../../book/model/book.model';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';
import {BookCardLiteComponent} from '../../../../../book/components/book-card-lite/book-card-lite-component';
import {BookReviewsComponent} from '../../../../../book/components/book-reviews/book-reviews.component';
import {BookNotesComponent} from '../../../../../book/components/book-notes/book-notes-component';
import {BookReadingSessionsComponent} from '../../book-reading-sessions/book-reading-sessions.component';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {Image} from 'primeng/image';
import {UrlHelperService} from '../../../../../../shared/service/url-helper.service';
import {BookMetadataManageService} from '../../../../../book/service/book-metadata-manage.service';
import {TranslocoDirective} from '@jsverse/transloco';

export interface ReadEvent {
  bookId: number;
  reader?: 'epub-streaming';
  bookType?: BookType;
}

export interface DownloadEvent {
  book: Book;
}

export interface DownloadAdditionalFileEvent {
  book: Book;
  fileId: number;
}

export interface DownloadAllFilesEvent {
  book: Book;
}

export interface DeleteBookFileEvent {
  book: Book;
  fileId: number;
  fileName: string;
  isPrimary: boolean;
  isOnlyFormat: boolean;
}

export interface DeleteSupplementaryFileEvent {
  bookId: number;
  fileId: number;
  fileName: string;
}

@Component({
  selector: 'app-metadata-tabs',
  standalone: true,
  imports: [
    Tab,
    TabList,
    TabPanel,
    TabPanels,
    Tabs,
    InfiniteScrollDirective,
    BookCardLiteComponent,
    BookReviewsComponent,
    BookNotesComponent,
    BookReadingSessionsComponent,
    Button,
    Tooltip,
    UpperCasePipe,
    Image,
    TranslocoDirective
  ],
  templateUrl: './metadata-tabs.component.html',
  styleUrl: './metadata-tabs.component.scss'
})
export class MetadataTabsComponent {
  @Input() book!: Book;
  @Input() bookInSeries: Book[] = [];
  @Input() recommendedBooks: BookRecommendation[] = [];

  protected urlHelper = inject(UrlHelperService);
  private bookMetadataManageService = inject(BookMetadataManageService);

  @Output() readBook = new EventEmitter<ReadEvent>();
  @Output() downloadBook = new EventEmitter<DownloadEvent>();
  @Output() downloadFile = new EventEmitter<DownloadAdditionalFileEvent>();
  @Output() downloadAllFiles = new EventEmitter<DownloadAllFilesEvent>();
  @Output() deleteBookFile = new EventEmitter<DeleteBookFileEvent>();
  @Output() deleteSupplementaryFile = new EventEmitter<DeleteSupplementaryFileEvent>();

  get defaultTabValue(): string {
    return this.bookInSeries && this.bookInSeries.length > 1 ? 'series' : 'similar';
  }

  read(bookId: number, reader?: 'epub-streaming', bookType?: BookType): void {
    this.readBook.emit({ bookId, reader, bookType });
  }

  download(book: Book): void {
    this.downloadBook.emit({ book });
  }

  downloadAdditionalFile(book: Book, fileId: number): void {
    this.downloadFile.emit({ book, fileId });
  }

  downloadAll(book: Book): void {
    this.downloadAllFiles.emit({ book });
  }

  deleteFile(book: Book, fileId: number, fileName: string, isPrimary: boolean): void {
    const isOnlyFormat = !book.alternativeFormats?.length;
    this.deleteBookFile.emit({ book, fileId, fileName, isPrimary, isOnlyFormat });
  }

  deleteSupplementary(bookId: number, fileId: number, fileName: string): void {
    this.deleteSupplementaryFile.emit({ bookId, fileId, fileName });
  }

  hasMultipleFiles(book: Book): boolean {
    const primaryCount = book.primaryFile ? 1 : 0;
    const altCount = book.alternativeFormats?.length ?? 0;
    return (primaryCount + altCount) > 1;
  }

  getTotalFileCount(book: Book): number {
    const primaryCount = book.primaryFile ? 1 : 0;
    const altCount = book.alternativeFormats?.length ?? 0;
    return primaryCount + altCount;
  }

  getFileSizeInMB(fileInfo: FileInfo | null | undefined): string {
    const sizeKb = fileInfo?.fileSizeKb;
    return sizeKb != null ? `${(sizeKb / 1024).toFixed(2)} MB` : '-';
  }

  getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  getFileIcon(fileType: string | null): string {
    if (!fileType) return 'pi pi-file';
    switch (fileType.toLowerCase()) {
      case 'pdf':
        return 'pi pi-file-pdf';
      case 'epub':
      case 'mobi':
      case 'azw3':
        return 'pi pi-book';
      case 'cbz':
      case 'cbr':
      case 'cbx':
        return 'pi pi-image';
      case 'audiobook':
      case 'm4b':
      case 'm4a':
      case 'mp3':
        return 'pi pi-headphones';
      default:
        return 'pi pi-file';
    }
  }

  getFileTypeBgColor(fileType: string | null | undefined): string {
    if (!fileType) return 'var(--p-gray-500)';
    const type = fileType.toLowerCase();
    return `var(--book-type-${type}-color, var(--p-gray-500))`;
  }

  isPhysicalBook(): boolean {
    return !this.book?.primaryFile && (!this.book?.alternativeFormats || this.book.alternativeFormats.length === 0);
  }

  supportsDualCovers(): boolean {
    return this.bookMetadataManageService.supportsDualCovers(this.book);
  }
}
