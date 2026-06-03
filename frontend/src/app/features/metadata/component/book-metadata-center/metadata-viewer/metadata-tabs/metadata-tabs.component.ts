import {ChangeDetectionStrategy, computed, Component, DestroyRef, effect, inject, input, linkedSignal, output, untracked} from '@angular/core';
import {UpperCasePipe} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
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
import {CoverPlaceholderComponent} from '../../../../../../shared/components/cover-generator/cover-generator.component';
import {BookMetadataManageService} from '../../../../../book/service/book-metadata-manage.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {AudiobookService} from '../../../../../readers/audiobook-player/audiobook.service';
import {AudiobookInfo} from '../../../../../readers/audiobook-player/audiobook.model';
import {finalize} from 'rxjs/operators';

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

export interface DetachBookFileEvent {
  book: Book;
  fileId: number;
  fileName: string;
}

type MetadataTabValue = 'series' | 'similar' | 'covers' | 'chapters' | 'files' | 'notes' | 'sessions' | 'reviews';
interface MetadataTab {
  value: MetadataTabValue;
  icon: string;
  labelKey: string;
}

const metadataTab = (value: MetadataTabValue, icon: string, labelKey: string): MetadataTab => ({value, icon, labelKey});

@Component({
  selector: 'app-metadata-tabs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
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
    TranslocoDirective,
    CoverPlaceholderComponent
  ],
  templateUrl: './metadata-tabs.component.html',
  styleUrl: './metadata-tabs.component.scss'
})
export class MetadataTabsComponent {
  readonly book = input.required<Book>();
  readonly bookInSeries = input<Book[]>([]);
  readonly hasSeries = input(false);
  readonly recommendedBooks = input<BookRecommendation[]>([]);

  protected urlHelper = inject(UrlHelperService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private audiobookService = inject(AudiobookService);
  private t = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);

  readonly audiobookInfo = linkedSignal<number, AudiobookInfo | null>({
    source: () => this.book().id,
    computation: () => null,
  });
  readonly chaptersLoading = linkedSignal({
    source: () => this.book().id,
    computation: () => false,
  });

  readonly readBook = output<ReadEvent>();
  readonly downloadBook = output<DownloadEvent>();
  readonly downloadFile = output<DownloadAdditionalFileEvent>();
  readonly downloadAllFiles = output<DownloadAllFilesEvent>();
  readonly deleteBookFile = output<DeleteBookFileEvent>();
  readonly deleteSupplementaryFile = output<DeleteSupplementaryFileEvent>();
  readonly detachBookFile = output<DetachBookFileEvent>();

  readonly supportsDualCovers = computed(() => this.bookMetadataManageService.supportsDualCovers(this.book()));
  readonly fileState = computed(() => {
    const book = this.book();
    const contentFileCount = (book.primaryFile ? 1 : 0) + (book.alternativeFormats?.length ?? 0);
    const allFileCount = contentFileCount + (book.supplementaryFiles?.length ?? 0);

    return {
      canDetach: allFileCount > 1,
      hasAudiobookFormat: book.primaryFile?.bookType === 'AUDIOBOOK' || book.alternativeFormats?.some(file => file.bookType === 'AUDIOBOOK') === true,
      hasMultipleContentFiles: contentFileCount > 1,
      isPhysical: contentFileCount === 0,
      totalContentFiles: contentFileCount,
    };
  });
  readonly availableTabs = computed<MetadataTab[]>(() => [
    ...(this.hasSeries() ? [metadataTab('series', 'pi pi-ethereum', 'moreInSeries')] : []),
    metadataTab('similar', 'pi pi-bookmark', 'similarBooks'),
    ...(this.supportsDualCovers() ? [metadataTab('covers', 'pi pi-images', 'covers')] : []),
    ...(this.fileState().hasAudiobookFormat ? [metadataTab('chapters', 'pi pi-headphones', 'chapters')] : []),
    metadataTab('files', 'pi pi-folder-open', 'files'),
    metadataTab('notes', 'pi pi-pen-to-square', 'notes'),
    metadataTab('sessions', 'pi pi-clock', 'readingSessions'),
    metadataTab('reviews', 'pi pi-comments', 'reviews'),
  ]);
  readonly activeTab = linkedSignal<MetadataTab[], MetadataTabValue>({
    source: this.availableTabs,
    computation: (availableTabs, previous) =>
      previous && availableTabs.some(tab => tab.value === previous.value)
        ? previous.value
        : availableTabs[0]?.value ?? 'similar',
  });

  constructor() {
    effect(() => {
      const bookId = this.book().id;
      if (this.activeTab() === 'chapters') {
        untracked(() => this.loadChapters(bookId));
      }
    });
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

  detachFile(book: Book, fileId: number, fileName: string): void {
    this.detachBookFile.emit({ book, fileId, fileName });
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
      case 'opus':
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

  onTabChange(value: string | number | undefined): void {
    if (typeof value === 'string' && this.availableTabs().some(tab => tab.value === value)) {
      this.activeTab.set(value as MetadataTabValue);
    }
  }

  loadChapters(bookId = this.book().id): void {
    if (this.audiobookInfo() || this.chaptersLoading()) return;
    this.chaptersLoading.set(true);
    this.audiobookService.getAudiobookInfo(bookId).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        if (this.book().id === bookId) {
          this.chaptersLoading.set(false);
        }
      })
    ).subscribe({
      next: info => {
        if (this.book().id === bookId) {
          this.audiobookInfo.set(info);
        }
      },
      error: () => undefined
    });
  }

  formatDuration(ms: number): string {
    const totalSeconds = Math.floor(ms / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    const pad = (n: number) => n.toString().padStart(2, '0');
    return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
  }

  formatSampleRate(sampleRate: number): string {
    return `${(sampleRate / 1000).toFixed(1)} kHz`;
  }

  getChannelLabel(channels: number): string {
    switch (channels) {
      case 1:
        return this.t.translate('metadata.viewer.channelMono');
      case 2:
        return this.t.translate('metadata.viewer.channelStereo');
      default:
        return this.t.translate('metadata.viewer.channelMultiple', { count: channels });
    }
  }
}
