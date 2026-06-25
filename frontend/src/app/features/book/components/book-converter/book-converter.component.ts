import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Select} from 'primeng/select';
import {finalize} from 'rxjs/operators';

import {Book, BookType} from '../../model/book.model';
import {LibraryService} from '../../service/library.service';
import {BookConversionService} from '../../service/book-conversion.service';

export interface BookConverterDialogData {
  books: Book[];
}

interface ErrorResponseLike {
  error?: {
    message?: string;
  };
  message?: string;
}

const FALLBACK_TARGET_FORMATS: BookType[] = ['EPUB', 'PDF', 'MOBI', 'AZW3', 'FB2'];

@Component({
  selector: 'app-book-converter',
  imports: [
    Button,
    FormsModule,
    Select,
    TranslocoDirective,
  ],
  templateUrl: './book-converter.component.html',
  styleUrls: ['./book-converter.component.scss']
})
export class BookConverterComponent implements OnInit {
  private readonly dynamicDialogConfig = inject(DynamicDialogConfig);
  readonly dynamicDialogRef = inject(DynamicDialogRef);
  private readonly bookConversionService = inject(BookConversionService);
  private readonly libraryService = inject(LibraryService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  private readonly dialogData = this.dynamicDialogConfig.data as BookConverterDialogData | undefined;

  readonly books: Book[] = this.dialogData?.books ?? [];
  readonly selectedFormat = signal<BookType | null>(null);
  readonly isSubmitting = signal(false);

  readonly titleKey = computed(() => this.books.length > 1 ? 'bulkTitle' : 'title');

  readonly eligibleBooks = computed(() => this.books.filter(book => this.isEligibleBook(book)));

  readonly targetFormats = computed<BookType[]>(() => {
    const eligibleBooks = this.eligibleBooks();
    if (eligibleBooks.length === 0) {
      return [];
    }

    const capabilityFormats = this.bookConversionService.capability().supportedTargetFormats;
    const candidates = capabilityFormats.length > 0 ? capabilityFormats : FALLBACK_TARGET_FORMATS;
    const allowedCandidates = candidates.filter(format => this.isAllowedByAllLibraries(format, eligibleBooks));

    if (eligibleBooks.length === 1) {
      const book = eligibleBooks[0];
      return allowedCandidates.filter(format => !this.hasFormat(book, format));
    }

    return allowedCandidates.filter(format => eligibleBooks.some(book => !this.hasFormat(book, format)));
  });

  readonly canSubmit = computed(() => {
    const selectedFormat = this.selectedFormat();
    return !this.isSubmitting()
      && this.eligibleBooks().length > 0
      && selectedFormat !== null
      && this.targetFormats().includes(selectedFormat);
  });

  ngOnInit(): void {
    this.selectedFormat.set(this.targetFormats()[0] ?? null);
  }

  submit(): void {
    const selectedFormat = this.selectedFormat();
    const eligibleBooks = this.eligibleBooks();
    if (!selectedFormat || eligibleBooks.length === 0) {
      return;
    }

    this.isSubmitting.set(true);
    const bookIds = eligibleBooks.map(book => book.id);

    this.bookConversionService.convertBooks(bookIds, selectedFormat).pipe(
      finalize(() => this.isSubmitting.set(false))
    ).subscribe({
      next: response => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('book.converter.toast.startedSummary'),
          detail: this.t.translate('book.converter.toast.startedDetail', {
            count: response.acceptedCount,
            format: response.targetFormat,
          }),
        });
        this.dynamicDialogRef.close(true);
      },
      error: (error: unknown) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.converter.toast.failedSummary'),
          detail: this.extractErrorDetail(error),
        });
      }
    });
  }

  private isEligibleBook(book: Book): boolean {
    const primaryFile = book.primaryFile;
    if (!primaryFile) {
      return false;
    }
    if (primaryFile.folderBased === true) {
      return false;
    }
    return primaryFile.bookType !== 'AUDIOBOOK';
  }
  private isAllowedByAllLibraries(format: BookType, books: Book[]): boolean {
    for (const book of books) {
      const library = this.libraryService.findLibraryById(book.libraryId);
      const allowedFormats = library?.allowedFormats;
      if (allowedFormats && allowedFormats.length > 0 && !allowedFormats.includes(format)) {
        return false;
      }
    }
    return true;
  }

  private hasFormat(book: Book, format: BookType): boolean {
    if (book.primaryFile?.bookType === format) {
      return true;
    }
    return (book.alternativeFormats ?? []).some(alternativeFormat => alternativeFormat.bookType === format);
  }

  private extractErrorDetail(error: unknown): string {
    const maybeError = error as ErrorResponseLike | null | undefined;
    return maybeError?.error?.message
      || maybeError?.message
      || this.t.translate('book.converter.toast.failedDetail');
  }
}
