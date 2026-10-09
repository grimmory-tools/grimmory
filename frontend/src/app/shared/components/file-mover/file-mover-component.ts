import {Component, computed, inject, linkedSignal, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {TableModule} from '@openng/optimus-ui/table';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {MessageService} from '@openng/optimus-ui/api';
import {Select} from '@openng/optimus-ui/select';
import {injectMutation, injectQuery} from '@tanstack/angular-query-experimental';

import {BookCommandService} from '../../../features/book/data/book-command.service';
import {BookQueryService} from '../../../features/book/data/book-query.service';
import {BookSummary} from '../../../features/book/data/book-response.models';
import {LibraryService} from '../../../features/book/service/library.service';
import {Library, LibraryPath} from '../../../features/book/model/library.model';
import {AppSettingsService} from '../../service/app-settings.service';
import {replacePlaceholders} from '../../util/pattern-resolver';

const PAGE_SIZE = 100;

interface BookTarget {
  readonly bookId: number;
  readonly targetLibraryId: number;
  readonly targetLibraryPathId: number | null;
}

interface FilePreview {
  readonly bookId: number;
  readonly currentLibraryName: string;
  readonly relativeOriginalPath: string;
  readonly targetLibraryId: number;
  readonly targetLibraryName: string;
  readonly targetLibraryPathId: number | null;
  readonly availableLibraryPaths: LibraryPath[];
  readonly relativeNewPath: string;
}

@Component({
  selector: 'app-file-mover-component',
  imports: [Button, FormsModule, TableModule, Select],
  templateUrl: './file-mover-component.html',
  styleUrl: './file-mover-component.scss'
})
export class FileMoverComponent {
  private readonly config = inject(DynamicDialogConfig);
  private readonly ref = inject(DynamicDialogRef);
  private readonly bookQueryService = inject(BookQueryService);
  private readonly bookCommandService = inject(BookCommandService);
  private readonly libraryService = inject(LibraryService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly messageService = inject(MessageService);

  protected readonly bookIds: number[] = this.config.data.bookIds;
  protected readonly pageCount = Math.ceil(this.bookIds.length / PAGE_SIZE);
  protected readonly pageIndex = signal(0);

  protected readonly booksQuery = injectQuery(() => this.bookQueryService.batch(
    this.bookIds.slice(this.pageIndex() * PAGE_SIZE, (this.pageIndex() + 1) * PAGE_SIZE)
  ));
  protected readonly moveFilesMutation = injectMutation(() => this.bookCommandService.moveFiles());

  protected readonly patternsCollapsed = signal(true);
  protected readonly infoCollapsed = signal(true);
  protected readonly moved = signal(false);
  protected readonly defaultTargetLibraryId = signal<number | null>(null);
  private readonly targetOverrides = signal<ReadonlyMap<number, BookTarget>>(new Map());

  private readonly books = computed(() => this.booksQuery.data() ?? []);
  private readonly defaultMovePattern = computed(() => this.appSettingsService.appSettings()?.uploadPattern || '');
  protected readonly libraries = this.libraryService.libraries;

  protected readonly defaultAvailableLibraryPaths = computed(() => {
    const libraryId = this.defaultTargetLibraryId();
    return libraryId === null ? [] : this.getLibraryPaths(libraryId);
  });
  protected readonly defaultTargetLibraryPathId = linkedSignal<LibraryPath[], number | null>({
    source: this.defaultAvailableLibraryPaths,
    computation: (paths, previous) => paths.find(path => path.id === previous?.value)?.id ?? paths[0]?.id ?? null
  });

  protected readonly libraryPatterns = computed(() => {
    const bookCounts = new Map<number, number>();
    for (const book of this.books()) {
      bookCounts.set(book.libraryId, (bookCounts.get(book.libraryId) ?? 0) + 1);
    }

    return [...bookCounts].map(([libraryId, bookCount]) => {
      const library = this.getLibrary(libraryId);
      return {
        libraryId,
        libraryName: library?.name ?? 'Unknown Library',
        pattern: library?.fileNamingPattern || this.defaultMovePattern(),
        source: library?.fileNamingPattern ? 'Library Setting' : 'App Default',
        bookCount
      };
    });
  });

  protected readonly filePreviews = computed(() => this.books().map(book => this.buildPreview(book)));

  protected onDefaultLibraryChange(libraryId: number | null): void {
    this.defaultTargetLibraryId.set(libraryId);
    this.targetOverrides.set(new Map());
  }

  protected onDefaultLibraryPathChange(libraryPathId: number | null): void {
    this.defaultTargetLibraryPathId.set(libraryPathId);
    this.targetOverrides.update(overrides => new Map(
      [...overrides].filter(([, target]) => target.targetLibraryId !== this.defaultTargetLibraryId())
    ));
  }

  protected onLibraryChange(bookId: number, libraryId: number): void {
    this.setTarget({bookId, targetLibraryId: libraryId, targetLibraryPathId: this.getLibraryPaths(libraryId)[0]?.id ?? null});
  }

  protected onLibraryPathChange(preview: FilePreview, libraryPathId: number | null): void {
    this.setTarget({bookId: preview.bookId, targetLibraryId: preview.targetLibraryId, targetLibraryPathId: libraryPathId});
  }

  protected saveChanges(): void {
    const moves = this.bookIds.map(bookId => this.targetOverrides().get(bookId) ?? {
      bookId,
      targetLibraryId: this.defaultTargetLibraryId(),
      targetLibraryPathId: this.defaultTargetLibraryPathId()
    });

    this.moveFilesMutation.mutate({moves}, {
      onSuccess: () => {
        this.moved.set(true);
        this.messageService.add({
          severity: 'success',
          summary: 'Files Organized!',
          detail: `Successfully organized ${this.bookIds.length} file${this.bookIds.length === 1 ? '' : 's'}.`,
          life: 3000
        });
      },
      onError: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Oops! Something went wrong',
          detail: 'We had trouble organizing your files. Please try again.',
          life: 3000
        });
      }
    });
  }

  protected cancel(): void {
    this.ref.close();
  }

  private setTarget(target: BookTarget): void {
    this.targetOverrides.update(overrides => new Map(overrides).set(target.bookId, target));
  }

  private getTarget(book: BookSummary): BookTarget {
    const override = this.targetOverrides().get(book.id);
    if (override) {
      return override;
    }
    const defaultLibraryId = this.defaultTargetLibraryId();
    if (defaultLibraryId !== null) {
      return {bookId: book.id, targetLibraryId: defaultLibraryId, targetLibraryPathId: this.defaultTargetLibraryPathId()};
    }
    return {bookId: book.id, targetLibraryId: book.libraryId, targetLibraryPathId: this.getLibraryPaths(book.libraryId)[0]?.id ?? null};
  }

  private buildPreview(book: BookSummary): FilePreview {
    const fileName = book.primaryFile?.fileName ?? '';
    const fileSubPath = book.primaryFile?.fileSubPath ? `${book.primaryFile.fileSubPath.replace(/\/+$/g, '')}/` : '';
    const target = this.getTarget(book);

    return {
      bookId: book.id,
      currentLibraryName: this.getLibraryName(book.libraryId),
      relativeOriginalPath: `${fileSubPath}${fileName}`,
      targetLibraryId: target.targetLibraryId,
      targetLibraryName: this.getLibraryName(target.targetLibraryId),
      targetLibraryPathId: target.targetLibraryPathId,
      availableLibraryPaths: this.getLibraryPaths(target.targetLibraryId),
      relativeNewPath: this.getNewPath(book, target.targetLibraryId)
    };
  }

  private getNewPath(book: BookSummary, libraryId: number): string {
    const meta = book.metadata!;
    const fileName = book.primaryFile?.fileName ?? '';
    const extension = fileName.match(/\.[^.]+$/)?.[0] ?? '';
    const pattern = this.getLibrary(libraryId)?.fileNamingPattern || this.defaultMovePattern();

    if (!pattern.trim()) {
      return fileName;
    }

    const newPath = replacePlaceholders(pattern, {
      authors: this.sanitize(meta.authors?.join(', ') || 'Unknown Author'),
      title: this.sanitize(meta.title || 'Untitled'),
      subtitle: this.sanitize(meta.subtitle || ''),
      year: this.formatYear(meta.publishedDate),
      series: this.sanitize(meta.seriesName || ''),
      seriesIndex: this.formatSeriesIndex(meta.seriesNumber),
      language: this.sanitize(meta.language || ''),
      publisher: this.sanitize(meta.publisher || ''),
      isbn: this.sanitize(meta.isbn13 || meta.isbn10 || ''),
      currentFilename: this.sanitize(fileName)
    });

    return newPath.endsWith(extension) ? newPath : newPath + extension;
  }

  private getLibrary(libraryId: number): Library | undefined {
    return this.libraries().find(library => library.id === libraryId);
  }

  private getLibraryName(libraryId: number): string {
    return this.getLibrary(libraryId)?.name ?? 'Unknown Library';
  }

  private getLibraryPaths(libraryId: number): LibraryPath[] {
    return this.getLibrary(libraryId)?.paths ?? [];
  }

  private sanitize(input: string | undefined): string {
    const sanitized = (input ?? '')
      .replace(/[\\/:*?"<>|]/g, '')
      .split('')
      .filter(char => {
        const code = char.charCodeAt(0);
        return code >= 32 && code !== 127;
      })
      .join('');

    return sanitized.replace(/\s+/g, ' ').trim();
  }

  private formatYear(dateStr?: string): string {
    if (!dateStr) return '';
    const yearMatch = dateStr.match(/^(\d{4})/);
    if (yearMatch) {
      return yearMatch[1];
    }
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? '' : date.getUTCFullYear().toString();
  }

  private formatSeriesIndex(seriesNumber?: number): string {
    if (seriesNumber == null) return '';
    // Check if it's a whole number
    if (Number.isInteger(seriesNumber)) {
      // Format with leading zero for 1-9
      return this.sanitize(seriesNumber.toString().padStart(2, '0'));
    } else {
      // For decimal numbers, format integer part with leading zero
      const intPart = Math.floor(seriesNumber);
      const decimalPart = (seriesNumber % 1).toFixed(10).substring(1).replace(/0+$/, '');
      return this.sanitize(intPart.toString().padStart(2, '0') + decimalPart);
    }
  }
}
