import {Component, computed, effect, inject, signal, WritableSignal} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {FormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {Select} from '@openng/optimus-ui/select';
import {Textarea} from '@openng/optimus-ui/textarea';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {ProgressBar} from '@openng/optimus-ui/progressbar';
import {FileUpload, FileSelectEvent} from '@openng/optimus-ui/fileupload';
import {BookService} from '../../service/book.service';
import {BookMetadataService} from '../../service/book-metadata.service';
import {LibraryService} from '../../service/library.service';
import {Library} from '../../model/library.model';
import {BookMetadata, CreatePhysicalBookRequest} from '../../model/book.model';
import {TranslocoDirective} from '@jsverse/transloco';
import {Tabs, TabList, Tab, TabPanels, TabPanel} from '@openng/optimus-ui/tabs';

const MAX_ISBN_COUNT = 500;
const MAX_FILE_SIZE_BYTES = 1_048_576; // 1 MB
const DELAY_BETWEEN_REQUESTS_MS = 300;
const RETRY_DELAY_MS = 2000;

export type IsbnEntryStatus = 'pending' | 'looking-up' | 'created' | 'created-no-metadata' | 'failed';

export interface IsbnEntry {
  isbn: string;
  status: IsbnEntryStatus;
  title?: string;
  error?: string;
}

export interface SkippedEntry {
  value: string;
  reason: string;
}

type ImportPhase = 'upload' | 'processing' | 'summary';

@Component({
  selector: 'app-bulk-isbn-import-dialog',
  standalone: true,
  templateUrl: './bulk-isbn-import-dialog.component.html',
  imports: [
    FormsModule,
    Button,
    Select,
    Textarea,
    Tooltip,
    ProgressBar,
    FileUpload,
    TranslocoDirective,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
  ],
  styleUrl: './bulk-isbn-import-dialog.component.scss',
})
export class BulkIsbnImportDialogComponent {
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dialogConfig = inject(DynamicDialogConfig);
  private bookService = inject(BookService);
  private bookMetadataService = inject(BookMetadataService);
  private libraryService = inject(LibraryService);

  selectedLibraryId: number | null = null;

  phase: WritableSignal<ImportPhase> = signal('upload');
  pasteText = '';
  entries: WritableSignal<IsbnEntry[]> = signal([]);
  skipped: WritableSignal<SkippedEntry[]> = signal([]);
  duplicatesRemoved = signal(0);
  showSkipped = false;

  processedCount = signal(0);
  createdCount = signal(0);
  noMetadataCount = signal(0);
  failedCount = signal(0);
  cancelled = false;

  parseError = signal('');
  readonly maxIsbnCount = MAX_ISBN_COUNT;
  private readonly initializeSelectedLibraryEffect = effect(() => {
    const libraries = this.libraries;
    if (libraries.length === 0) {
      return;
    }

    if (this.dialogConfig.data?.libraryId) {
      this.selectedLibraryId = this.dialogConfig.data.libraryId;
      return;
    }

    if (this.selectedLibraryId == null && libraries[0].id !== undefined) {
      this.selectedLibraryId = libraries[0].id;
    }
  });

  get libraries(): Library[] {
    return this.libraryService.libraries();
  }

  onFileSelect(event: FileSelectEvent): void {
    const file = event.files?.[0];
    if (!file) return;

    if (file.size > MAX_FILE_SIZE_BYTES) {
      this.parseError.set('fileTooLarge');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const content = reader.result as string;
      this.parseContent(content, file.name);
    };
    reader.readAsText(file, 'UTF-8');
  }

  parsePastedText(): void {
    if (!this.pasteText.trim()) return;
    this.parseContent(this.pasteText, 'paste');
  }

  clearParsed(): void {
    this.entries.set([]);
    this.skipped.set([]);
    this.duplicatesRemoved.set(0);
    this.parseError.set('');
    this.pasteText = '';
  }

  canStartImport(): boolean {
    return this.phase() === 'upload' && this.entries().length > 0 && !!this.selectedLibraryId;
  }

  async startImport(): Promise<void> {
    if (!this.canStartImport()) return;

    this.phase.set('processing');
    this.processedCount.set(0);
    this.createdCount.set(0);
    this.noMetadataCount.set(0);
    this.failedCount.set(0);
    this.cancelled = false;

    const updateEntry = (index: number, value: Partial<IsbnEntry>) => {
      this.entries.update((e) => {
        e[index] = {
          ...e[index],
          ...value
        }

        return [...e];
      });
    };

    for (let i = 0; i < this.entries().length; i++) {
      if (this.cancelled) break;

      if (i > 0) {
        await this.delay(DELAY_BETWEEN_REQUESTS_MS);
      }

      if (this.cancelled) break;

      const { isbn } = this.entries()[i];

      updateEntry(i, { status: 'looking-up' })

      try {
        const metadata = await this.lookupIsbn(isbn);
        if (this.cancelled) break;

        const request: CreatePhysicalBookRequest = {
          libraryId: this.selectedLibraryId!,
          isbn: isbn,
          title: metadata?.title || undefined,
          authors: metadata?.authors?.length ? [...metadata.authors] : undefined,
          description: metadata?.description || undefined,
          publisher: metadata?.publisher || undefined,
          publishedDate: metadata?.publishedDate || undefined,
          language: metadata?.language || undefined,
          pageCount: metadata?.pageCount ?? undefined,
          categories: metadata?.categories?.length ? [...metadata.categories] : undefined,
          thumbnailUrl: metadata?.thumbnailUrl || undefined,
        };

        await this.createBook(request);

        if (metadata?.title) {
          updateEntry(i, { status: 'created', title: metadata.title })
          this.createdCount.update(v => v + 1);
        } else {
          updateEntry(i, { status: 'created-no-metadata' })
          this.noMetadataCount.update(v => v + 1);
        }
      } catch (err: unknown) {
        updateEntry(i, { status: 'failed', error: err instanceof Error ? err.message : 'Unknown error' })
        this.failedCount.update(v => v + 1);
      }

      this.processedCount.update(v => v + 1);
    }

    this.phase.set('summary');
  }

  cancelImport(): void {
    this.cancelled = true;
  }

  close(): void {
    this.dynamicDialogRef.close();
  }

  progressPercent = computed(
    () => this.entries().length > 0 ? Math.round((this.processedCount() / this.entries().length) * 100) : 0
  );

  get selectedLibraryName(): string {
    return this.libraries.find(l => l.id === this.selectedLibraryId)?.name ?? '';
  }

  private parseContent(content: string, source: string): void {
    this.parseError.set('');
    this.skipped.set([]);
    this.duplicatesRemoved.set(0);

    // Strip BOM
    const cleaned = content.replace(/^\uFEFF/, '');
    const lines = cleaned.split(/\r?\n|\r/);

    let isbns: string[];
    const extension = source.split('.').pop()?.toLowerCase();

    if (extension === 'csv' || extension === 'tsv') {
      isbns = this.parseCsvLines(lines, extension === 'tsv' ? '\t' : undefined);
    } else if (source === 'paste') {
      // Pasted text: split by newlines, commas, semicolons, or spaces
      const tokens = cleaned.split(/[\n\r,;\s]+/);
      isbns = tokens.map(t => t.trim()).filter(t => t.length > 0);
    } else {
      // Plain text: one per line
      isbns = lines.map(l => l.trim()).filter(l => l.length > 0 && !l.startsWith('#'));
    }

    const existingIsbns = this.getExistingIsbnsForLibrary();
    const validEntries: IsbnEntry[] = [];
    const seen = new Set<string>();

    for (const raw of isbns) {
      const normalized = this.normalizeIsbn(raw);

      if (!this.isValidIsbn(normalized)) {
        this.skipped.update(s => [...s, {value: raw, reason: 'invalid'}]);
        continue;
      }

      if (seen.has(normalized)) {
        this.duplicatesRemoved.update(v => v + 1);
        continue;
      }

      if (existingIsbns.has(normalized)) {
        this.skipped.update(s => [...s, {value: normalized, reason: 'alreadyExists'}]);
        continue;
      }

      seen.add(normalized);
      validEntries.push({isbn: normalized, status: 'pending'});
    }

    if (validEntries.length > MAX_ISBN_COUNT) {
      this.parseError.set('tooMany');
      this.entries.set(validEntries.slice(0, MAX_ISBN_COUNT));
    } else {
      this.entries.set(validEntries);
    }

    if (this.entries().length === 0 && this.skipped().length === 0) {
      this.parseError.set('empty');
    }
  }

  private parseCsvLines(lines: string[], delimiter?: string): string[] {
    if (lines.length === 0) return [];

    // Auto-detect delimiter if not provided
    if (!delimiter) {
      const firstLine = lines[0];
      if (firstLine.includes('\t')) delimiter = '\t';
      else if (firstLine.includes(';')) delimiter = ';';
      else delimiter = ',';
    }

    const headerFields = lines[0].split(delimiter).map(f => f.trim().replace(/^["']|["']$/g, '').toLowerCase());
    let isbnColIndex = headerFields.findIndex(h => h === 'isbn' || h === 'isbn13' || h === 'isbn10');

    let startLine: number;
    if (isbnColIndex >= 0) {
      startLine = 1; // skip header
    } else {
      isbnColIndex = 0; // assume first column
      startLine = 0;
    }

    const result: string[] = [];
    for (let i = startLine; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      const fields = line.split(delimiter);
      if (fields.length > isbnColIndex) {
        const value = fields[isbnColIndex].trim().replace(/^["']|["']$/g, '');
        if (value) result.push(value);
      }
    }
    return result;
  }

  private getExistingIsbnsForLibrary(): Set<string> {
    const isbns = new Set<string>();
    if (!this.selectedLibraryId) return isbns;

    const books = this.bookService.books();
    for (const book of books) {
      if (book.libraryId !== this.selectedLibraryId) continue;
      const isbn13 = book.metadata?.isbn13;
      const isbn10 = book.metadata?.isbn10;
      if (isbn13) isbns.add(isbn13.toUpperCase());
      if (isbn10) isbns.add(isbn10.toUpperCase());
    }
    return isbns;
  }

  private normalizeIsbn(raw: string): string {
    // Strip hyphens, spaces, surrounding whitespace; preserve trailing X
    return raw.trim().replace(/[-\s]/g, '').toUpperCase();
  }

  private isValidIsbn(isbn: string): boolean {
    if (/^\d{10}$/.test(isbn) || /^\d{9}X$/.test(isbn)) return true;
    if (/^\d{13}$/.test(isbn) && (isbn.startsWith('978') || isbn.startsWith('979'))) return true;
    return false;
  }

  private lookupIsbn(isbn: string): Promise<BookMetadata | null> {
    return new Promise((resolve) => {
      this.bookMetadataService.lookupByIsbn(isbn).subscribe({
        next: metadata => resolve(metadata),
        error: err => {
          // Retry once on potential rate limiting
          if (err?.status === 429) {
            setTimeout(() => {
              this.bookMetadataService.lookupByIsbn(isbn).subscribe({
                next: metadata => resolve(metadata),
                error: () => resolve(null), // Still create with ISBN only
              });
            }, RETRY_DELAY_MS);
          } else {
            resolve(null); // No metadata found, still create with ISBN
          }
        },
      });
    });
  }

  private createBook(request: CreatePhysicalBookRequest): Promise<void> {
    return new Promise((resolve, reject) => {
      this.bookService.createPhysicalBook(request).subscribe({
        next: () => resolve(),
        error: err => reject(err),
      });
    });
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}
