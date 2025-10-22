import {Component, inject, OnDestroy} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {Divider} from 'primeng/divider';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {filter, take, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';

import {BookService} from '../../../features/book/service/book.service';
import {Book} from '../../../features/book/model/book.model';
import {FileMoveRequest, FileOperationsService} from '../../service/file-operations-service';
import {LibraryService} from "../../../features/book/service/library.service";
import {AppSettingsService} from '../../service/app-settings.service';
import {Select} from 'primeng/select';
import {Library, LibraryPath} from '../../../features/book/model/library.model';

interface FilePreview {
  bookId: number;
  originalPath: string;
  relativeOriginalPath: string;
  currentLibraryId: number | null;
  currentLibraryName: string;
  currentLibraryPath: string;
  targetLibraryId: number | null;
  targetLibraryName: string;
  targetLibraryPath: string;
  targetLibraryPathId: number | null;
  availableLibraryPaths: LibraryPath[];
  newPath: string;
  relativeNewPath: string;
  isMoved?: boolean;
}

@Component({
  selector: 'app-file-mover-component',
  standalone: true,
  imports: [Button, FormsModule, TableModule, Divider, Select],
  templateUrl: './file-mover-component.html',
  styleUrl: './file-mover-component.scss'
})
export class FileMoverComponent implements OnDestroy {
  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);
  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private fileOperationsService = inject(FileOperationsService);
  private messageService = inject(MessageService);
  private appSettingsService = inject(AppSettingsService);
  private destroy$ = new Subject<void>();

  libraryPatterns: {
    libraryId: number | null;
    libraryName: string;
    pattern: string;
    source: string;
    bookCount: number;
  }[] = [];
  defaultMovePattern = '';
  loading = false;
  patternsCollapsed = true;

  bookIds: Set<number> = new Set();
  books: Book[] = [];
  availableLibraries: { id: number | null; name: string }[] = [];
  filePreviews: FilePreview[] = [];
  defaultTargetLibraryId: number | null = null;
  defaultTargetLibraryPathId: number | null = null;
  defaultAvailableLibraryPaths: LibraryPath[] = [];

  constructor() {
    this.bookIds = new Set(this.config.data?.bookIds ?? []);
    this.books = this.bookService.getBooksByIdsFromState([...this.bookIds]);
    this.loadAvailableLibraries();
    this.loadDefaultPattern();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadDefaultPattern(): void {
    this.appSettingsService.appSettings$.pipe(
      filter(settings => settings != null),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(settings => {
      this.defaultMovePattern = settings?.uploadPattern || '';
      this.loadLibraryPatterns();
    });
  }

  private loadLibraryPatterns(): void {
    this.libraryService.libraryState$.pipe(
      filter(state => state.loaded && state.libraries != null),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(state => {
      const booksByLibrary = new Map<number | null, Book[]>();
      this.books.forEach(book => {
        const libraryId =
          book.libraryId ??
          book.libraryPath?.id ??
          (book as any).library?.id ??
          null;
        if (!booksByLibrary.has(libraryId)) {
          booksByLibrary.set(libraryId, []);
        }
        booksByLibrary.get(libraryId)!.push(book);
      });

      this.libraryPatterns = Array.from(booksByLibrary.entries()).map(([libraryId, books]) => {
        let libraryName = 'Unknown Library';
        let pattern = this.defaultMovePattern;
        let source = 'App Default';

        if (libraryId) {
          const library = state.libraries?.find(lib => lib.id === libraryId);
          if (library) {
            libraryName = library.name;
            if (library.fileNamingPattern) {
              pattern = library.fileNamingPattern;
              source = 'Library Setting';
            }
          }
        }

        return {
          libraryId,
          libraryName,
          pattern,
          source,
          bookCount: books.length
        };
      });

      this.applyPattern();
    });
  }

  private loadAvailableLibraries(): void {
    this.libraryService.libraryState$.pipe(
      filter(state => state.loaded && state.libraries != null),
      take(1),
      takeUntil(this.destroy$)
    ).subscribe(state => {
      this.availableLibraries = state.libraries?.map(lib => ({id: lib.id ?? null, name: lib.name})) || [];
    });
  }

  applyPattern(): void {
    this.filePreviews = this.books.map(book => {
      const fileName = book.fileName ?? '';
      const fileSubPath = book.fileSubPath ? `${book.fileSubPath.replace(/\/+$/g, '')}/` : '';

      const relativeOriginalPath = `${fileSubPath}${fileName}`;

      const currentLibraryId = book.libraryId ?? book.libraryPath?.id ?? (book as any).library?.id ?? null;
      const currentLibraryName = this.getLibraryNameById(currentLibraryId);
      const currentLibraryPath = this.getLibraryPathById(currentLibraryId);

      const targetLibraryId = currentLibraryId;
      const targetLibraryName = currentLibraryName;
      const availableLibraryPaths = this.getLibraryPathsById(targetLibraryId);
      const targetLibraryPathId = availableLibraryPaths.length > 0 ? availableLibraryPaths[0].id ?? null : null;
      const targetLibraryPath = availableLibraryPaths.length > 0 ? availableLibraryPaths[0].path : '';

      const preview: FilePreview = {
        bookId: book.id,
        originalPath: this.getFullPath(currentLibraryId, relativeOriginalPath),
        relativeOriginalPath,
        currentLibraryId,
        currentLibraryName,
        currentLibraryPath,
        targetLibraryId,
        targetLibraryName,
        targetLibraryPath,
        targetLibraryPathId,
        availableLibraryPaths,
        newPath: '',
        relativeNewPath: ''
      };

      this.updatePreviewPaths(preview, book);
      return preview;
    });
  }

  onDefaultLibraryChange(): void {
    this.defaultAvailableLibraryPaths = this.getLibraryPathsById(this.defaultTargetLibraryId);
    this.defaultTargetLibraryPathId = this.defaultAvailableLibraryPaths.length > 0 ? this.defaultAvailableLibraryPaths[0].id ?? null : null;

    this.filePreviews.forEach(preview => {
      if (!preview.isMoved) {
        preview.targetLibraryId = this.defaultTargetLibraryId;
        preview.targetLibraryName = this.getLibraryNameById(this.defaultTargetLibraryId);
        preview.availableLibraryPaths = this.defaultAvailableLibraryPaths;
        preview.targetLibraryPathId = this.defaultTargetLibraryPathId;
        preview.targetLibraryPath = this.defaultAvailableLibraryPaths.find(p => p.id === this.defaultTargetLibraryPathId)?.path || '';

        const book = this.books.find(b => b.id === preview.bookId);
        if (book) {
          this.updatePreviewPaths(preview, book);
        }
      }
    });
  }

  onDefaultLibraryPathChange(): void {
    this.filePreviews.forEach(preview => {
      if (!preview.isMoved && preview.targetLibraryId === this.defaultTargetLibraryId) {
        preview.targetLibraryPathId = this.defaultTargetLibraryPathId;
        preview.targetLibraryPath = this.defaultAvailableLibraryPaths.find(p => p.id === this.defaultTargetLibraryPathId)?.path || '';

        const book = this.books.find(b => b.id === preview.bookId);
        if (book) {
          this.updatePreviewPaths(preview, book);
        }
      }
    });
  }

  onLibraryChange(preview: FilePreview): void {
    preview.targetLibraryName = this.getLibraryNameById(preview.targetLibraryId);
    preview.availableLibraryPaths = this.getLibraryPathsById(preview.targetLibraryId);
    preview.targetLibraryPathId = preview.availableLibraryPaths.length > 0 ? preview.availableLibraryPaths[0].id ?? null : null;
    preview.targetLibraryPath = preview.availableLibraryPaths.length > 0 ? preview.availableLibraryPaths[0].path : '';

    const book = this.books.find(b => b.id === preview.bookId);
    if (book) {
      this.updatePreviewPaths(preview, book);
    }
  }

  onLibraryPathChange(preview: FilePreview): void {
    const selectedPath = preview.availableLibraryPaths.find(p => p.id === preview.targetLibraryPathId);
    preview.targetLibraryPath = selectedPath?.path || '';

    const book = this.books.find(b => b.id === preview.bookId);
    if (book) {
      this.updatePreviewPaths(preview, book);
    }
  }

  private updatePreviewPaths(preview: FilePreview, book: Book): void {
    const meta = book.metadata!;
    const fileName = book.fileName ?? '';
    const extension = fileName.match(/\.[^.]+$/)?.[0] ?? '';
    const pattern = this.getPatternForLibrary(preview.targetLibraryId);

    const values: Record<string, string> = {
      authors: this.sanitize(meta.authors?.join(', ') || 'Unknown Author'),
      title: this.sanitize(meta.title || 'Untitled'),
      year: this.formatYear(meta.publishedDate),
      series: this.sanitize(meta.seriesName || ''),
      seriesIndex: this.formatSeriesIndex(meta.seriesNumber ?? undefined),
      language: this.sanitize(meta.language || ''),
      publisher: this.sanitize(meta.publisher || ''),
      isbn: this.sanitize(meta.isbn13 || meta.isbn10 || ''),
      currentFilename: this.sanitize(fileName)
    };

    let newPath: string;

    if (!pattern?.trim()) {
      newPath = fileName;
    } else {
      newPath = pattern.replace(/<([^<>]+)>/g, (_, block) => {
        const placeholders = [...block.matchAll(/{(.*?)}/g)].map(m => m[1]);
        const allHaveValues = placeholders.every(key => values[key]?.trim());
        return allHaveValues
          ? block.replace(/{(.*?)}/g, (_: string, key: string) => values[key] ?? '')
          : '';
      });

      newPath = newPath.replace(/{(.*?)}/g, (_, key) => values[key] ?? '');

      if (!newPath.endsWith(extension)) {
        newPath += extension;
      }
    }

    preview.relativeNewPath = newPath;
    preview.newPath = this.getFullPath(preview.targetLibraryId, newPath);
  }

  private getPatternForLibrary(libraryId: number | null): string {
    if (libraryId === null) {
      return this.defaultMovePattern;
    }

    const libraries = this.libraryService.getLibrariesFromState();
    const library = libraries.find((lib: Library) => lib.id === libraryId);

    return library?.fileNamingPattern || this.defaultMovePattern;
  }

  private getLibraryNameById(libraryId: number | null): string {
    if (libraryId === null) return 'Unknown Library';
    return this.availableLibraries.find(lib => lib.id === libraryId)?.name || 'Unknown Library';
  }

  private getLibraryPathsById(libraryId: number | null): LibraryPath[] {
    if (libraryId === null) return [];

    const libraries = this.libraryService.getLibrariesFromState();
    const library = libraries.find((lib: Library) => lib.id === libraryId);
    return library?.paths || [];
  }

  private getLibraryPathById(libraryId: number | null): string {
    const paths = this.getLibraryPathsById(libraryId);
    return paths.map((p: LibraryPath) => p.path).join(', ');
  }

  private getFullPath(libraryId: number | null, relativePath: string): string {
    if (!libraryId) return relativePath;

    const paths = this.getLibraryPathsById(libraryId);
    const libraryPath = paths.length > 0 ? paths[0].path.replace(/\/+$/g, '') : '';
    return libraryPath ? `${libraryPath}/${relativePath}`.replace(/\/\/+/g, '/') : relativePath;
  }

  get movedFileCount(): number {
    return this.filePreviews.filter(p => p.isMoved).length;
  }

  sanitize(input: string | undefined): string {
    return input?.replace(/[\\/:*?"<>|]/g, '')
      .replace(/[\x00-\x1F\x7F]/g, '')
      .replace(/\s+/g, ' ')
      .trim() ?? '';
  }

  formatYear(dateStr?: string): string {
    if (!dateStr) return '';
    const yearMatch = dateStr.match(/^(\d{4})/);
    if (yearMatch) {
      return yearMatch[1];
    }
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? '' : date.getUTCFullYear().toString();
  }

  formatSeriesIndex(seriesNumber?: number): string {
    if (seriesNumber == null) return '';
    return this.sanitize(seriesNumber.toString());
  }

  saveChanges(): void {
    this.loading = true;

    const request: FileMoveRequest = {
      bookIds: [...this.bookIds],
      moves: this.filePreviews.map(preview => ({
        bookId: preview.bookId,
        targetLibraryId: preview.targetLibraryId,
        targetLibraryPathId: preview.targetLibraryPathId
      }))
    };

    this.fileOperationsService.moveFiles(request).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.loading = false;
        this.filePreviews.forEach(p => (p.isMoved = true));
        this.messageService.add({
          severity: 'success',
          summary: 'Files Organized!',
          detail: `Successfully organized ${this.filePreviews.length} file${this.filePreviews.length === 1 ? '' : 's'}.`,
          life: 3000
        });
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Oops! Something went wrong',
          detail: 'We had trouble organizing your files. Please try again.',
          life: 3000
        });
      }
    });
  }

  cancel(): void {
    this.ref.close();
  }

  togglePatternsCollapsed(): void {
    this.patternsCollapsed = !this.patternsCollapsed;
  }

  trackByBookId(index: number, preview: FilePreview): number {
    return preview.bookId;
  }
}
