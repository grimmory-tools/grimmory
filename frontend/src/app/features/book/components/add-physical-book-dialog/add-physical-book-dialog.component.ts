import {Component, effect, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {FormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {Select} from '@openng/optimus-ui/select';
import {Textarea} from '@openng/optimus-ui/textarea';
import {InputNumber} from '@openng/optimus-ui/inputnumber';
import {AutoComplete} from '@openng/optimus-ui/autocomplete';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {BookService} from '../../service/book.service';
import {FacetSuggestionsDirective} from '../facet-suggestions.directive';
import {LibraryService} from '../../service/library.service';
import {Library} from '../../model/library.model';
import {CreatePhysicalBookRequest} from '../../model/book.model';
import {MetadataSourceQueryService} from '../../../metadata/sources/metadata-source-query.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';

@Component({
  selector: 'app-add-physical-book-dialog',
  standalone: true,
  templateUrl: './add-physical-book-dialog.component.html',
  imports: [
    FormsModule,
    Button,
    InputText,
    Tooltip,
    Select,
    Textarea,
    InputNumber,
    AutoComplete,
    TranslocoDirective,
    FacetSuggestionsDirective
  ],
  styleUrl: './add-physical-book-dialog.component.scss',
})
export class AddPhysicalBookDialogComponent {
  private readonly dynamicDialogRef = inject(DynamicDialogRef);
  private readonly dialogConfig = inject(DynamicDialogConfig);
  private readonly bookService = inject(BookService);
  private readonly queryClient = inject(QueryClient);
  private readonly sources = inject(MetadataSourceQueryService);
  private readonly libraryService = inject(LibraryService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  selectedLibraryId: number | null = null;
  title: string = '';
  isbn: string = '';
  authors: string[] = [];
  description: string = '';
  publisher: string = '';
  publishedDate: string = '';
  language: string = '';
  pageCount: number | null = null;
  categories: string[] = [];

  coverUrl: string | null = null;
  readonly isLoading = signal(false);
  readonly isFetchingMetadata = signal(false);
  readonly metadataLookupMessage = signal<'metadataNotFound' | 'metadataLookupFailed' | null>(null);
  private readonly initializeSelectedLibraryEffect = effect(() => {
    const libraries = this.libraries;
    if (libraries.length === 0) {
      return;
    }

    if (this.dialogConfig.data?.libraryId) {
      this.selectedLibraryId = this.dialogConfig.data.libraryId;
      return;
    }

    if (this.selectedLibraryId == null && this.libraries[0].id !== undefined) {
      this.selectedLibraryId = this.libraries[0].id;
    }
  });

  get libraries(): Library[] {
    return this.libraryService.libraries();
  }

  onAutoCompleteKeyUp(fieldName: 'authors' | 'categories', event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      const input = event.target as HTMLInputElement;
      const value = input.value?.trim();
      if (value) {
        const values = this[fieldName];
        if (!values.includes(value)) {
          this[fieldName] = [...values, value];
        }
        input.value = '';
      }
    }
  }

  onAutoCompleteSelect(fieldName: 'authors' | 'categories', event: { value: string, originalEvent: Event }): void {
    const values = this[fieldName];
    if (!values.includes(event.value)) {
      this[fieldName] = [...values, event.value];
    }
    (event.originalEvent.target as HTMLInputElement).value = '';
  }

  async fetchMetadataByIsbn(): Promise<void> {
    const isbnValue = this.isbn.trim();
    if (!isbnValue || this.isFetchingMetadata()) return;

    this.isFetchingMetadata.set(true);
    this.metadataLookupMessage.set(null);
    try {
      const metadata = await this.queryClient.query(this.sources.isbnLookup(isbnValue));
      if (!metadata) {
        this.metadataLookupMessage.set('metadataNotFound');
        return;
      }

      if (metadata.title) this.title = metadata.title;
      if (metadata.authors?.length) this.authors = [...metadata.authors];
      if (metadata.description) this.description = metadata.description;
      if (metadata.publisher) this.publisher = metadata.publisher;
      if (metadata.publishedDate) this.publishedDate = metadata.publishedDate;
      if (metadata.language) this.language = metadata.language;
      if (metadata.pageCount) this.pageCount = metadata.pageCount;
      if (metadata.categories?.length) this.categories = [...metadata.categories];
      this.coverUrl = metadata.thumbnailUrl || null;
    } catch {
      this.metadataLookupMessage.set('metadataLookupFailed');
    } finally {
      this.isFetchingMetadata.set(false);
    }
  }

  cancel(): void {
    this.dynamicDialogRef.close();
  }

  canCreate(): boolean {
    return !!this.selectedLibraryId && (!!this.title.trim() || !!this.isbn.trim());
  }

  createBook(): void {
    if (!this.canCreate() || this.isLoading()) return;

    this.isLoading.set(true);

    const request: CreatePhysicalBookRequest = {
      libraryId: this.selectedLibraryId!,
      title: this.title.trim() || undefined,
      isbn: this.isbn.trim() || undefined,
      authors: this.authors.length > 0 ? this.authors : undefined,
      description: this.description.trim() || undefined,
      publisher: this.publisher.trim() || undefined,
      publishedDate: this.publishedDate.trim() || undefined,
      language: this.language.trim() || undefined,
      pageCount: this.pageCount ?? undefined,
      categories: this.categories.length > 0 ? this.categories : undefined,
      thumbnailUrl: this.coverUrl || undefined
    };

    this.bookService.createPhysicalBook(request).subscribe({
      next: (book) => {
        this.isLoading.set(false);
        this.dynamicDialogRef.close(book);
      },
      error: (err: unknown) => {
        this.isLoading.set(false);
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('book.bookService.toast.creationFailedSummary'),
            detail: this.t.translate('book.bulkIsbnImport.reason.alreadyExists')
          });
        }
      }
    });
  }
}
