import { Component, computed, inject, OnInit, OnDestroy, Signal, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { AutoComplete, AutoCompleteSelectEvent } from 'primeng/autocomplete';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { Subject, takeUntil } from 'rxjs';
import { BookService } from '../../service/book.service';
import { BookFileService } from '../../service/book-file.service';
import { Book } from '../../model/book.model';
import {MessageService, PrimeTemplate} from 'primeng/api';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppSettingsService } from '../../../../shared/service/app-settings.service';

@Component({
  selector: 'app-book-file-attacher',
  standalone: true,
  imports: [
    FormsModule,
    AutoComplete,
    Button,
    Checkbox,
    TranslocoDirective,
    TranslocoPipe,
    PrimeTemplate,
  ],
  templateUrl: './book-file-attacher.component.html',
  styleUrls: ['./book-file-attacher.component.scss']
})
export class BookFileAttacherComponent implements OnInit, OnDestroy {
  sourceBooks: Book[] = [];
  targetBook: Book | null = null;
  moveFiles = false;
  isAttaching = false;
  searchQuery = '';
  filteredBooks: Book[] = [];

  private destroy$ = new Subject<void>();
  private allBooks: Signal<Book[]> = signal([]);

  private readonly t = inject(TranslocoService);
  private readonly appSettingsService = inject(AppSettingsService);

  constructor(
    private dialogRef: DynamicDialogRef,
    private config: DynamicDialogConfig,
    private bookService: BookService,
    private bookFileService: BookFileService,
    private messageService: MessageService
  ) {}

  ngOnInit(): void {
    // Support both single book and multiple books
    if (this.config.data.sourceBook) {
      this.sourceBooks = [this.config.data.sourceBook];
    } else if (this.config.data.sourceBooks) {
      this.sourceBooks = this.config.data.sourceBooks;
    }

    if (this.sourceBooks.length === 0) {
      this.closeDialog();
      return;
    }

    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.moveFiles = settings.metadataPersistenceSettings?.moveFilesToLibraryPattern ?? false;
    }

    // Get the library ID from first source book (all should be same library)
    const libraryId = this.sourceBooks[0].libraryId;
    const sourceBookIds = new Set(this.sourceBooks.map(b => b.id));

    this.allBooks = computed(() =>
      this.bookService.books().filter(book =>
        book.libraryId === libraryId && !sourceBookIds.has(book.id)
      )
    );

    this.filteredBooks = this.allBooks().slice(0, 20);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isBulkMode(): boolean {
    return this.sourceBooks.length > 1;
  }

  filterBooks(event: { query: string }): void {
    const query = event.query.toLowerCase().trim();
    const books = this.allBooks();
    if (!query) {
      this.filteredBooks = books.slice(0, 20);
      return;
    }

    this.filteredBooks = books
      .filter(book => {
        const title = book.metadata?.title?.toLowerCase() || '';
        const authors = book.metadata?.authors?.join(' ').toLowerCase() || '';
        return title.includes(query) || authors.includes(query);
      })
      .slice(0, 20);
  }

  onBookSelect(event: AutoCompleteSelectEvent): void {
    this.targetBook = event.value as Book;
  }

  onBookClear(): void {
    this.targetBook = null;
  }

  getBookDisplayName(book: Book): string {
    const title = book.metadata?.title || `Book #${book.id}`;
    const authors = book.metadata?.authors?.join(', ');
    return authors ? `${title} - ${authors}` : title;
  }

  getSourceFileInfo(book: Book): string {
    const file = book.primaryFile;
    if (!file) return this.t.translate('book.fileAttacher.unknownFile');
    const format = file.extension?.toUpperCase() || file.bookType || this.t.translate('book.fileAttacher.unknownFormat');
    return `${format} - ${file.fileName || this.t.translate('book.fileAttacher.unknownFilename')}`;
  }

  canAttach(): boolean {
    return !!this.targetBook && !this.isAttaching;
  }

  attach(): void {
    if (!this.targetBook) return;

    this.isAttaching = true;

    const sourceBookIds = this.sourceBooks.map(b => b.id);

    this.bookFileService.attachBookFiles(
      this.targetBook.id,
      sourceBookIds,
      this.moveFiles
    ).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.dialogRef.close({ success: true });
      },
      error: () => {
        this.isAttaching = false;
      }
    });
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
