import {Component, computed, effect, inject, signal} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {Select} from 'primeng/select';
import {Textarea} from 'primeng/textarea';
import {InputNumber} from 'primeng/inputnumber';
import {AutoComplete, AutoCompleteCompleteEvent} from 'primeng/autocomplete';
import {BookService} from '../../service/book.service';
import {BookMetadataService} from '../../service/book-metadata.service';
import {LibraryService} from '../../service/library.service';
import {Library} from '../../model/library.model';
import {CreatePhysicalBookRequest} from '../../model/book.model';
import {TranslocoDirective} from '@jsverse/transloco';

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
    TranslocoDirective
  ],
  styleUrl: './add-physical-book-dialog.component.scss',
})
export class AddPhysicalBookDialogComponent {
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dialogConfig = inject(DynamicDialogConfig);
  private bookService = inject(BookService);
  private bookMetadataService = inject(BookMetadataService);
  private libraryService = inject(LibraryService);
  private readonly metadata = computed(() => this.bookService.uniqueMetadata());



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

  get allAuthors(): string[] { return this.metadata().authors; }
  get allCategories(): string[] { return this.metadata().categories; }
  filteredAuthors: string[] = [];
  filteredCategories: string[] = [];

  coverUrl: string | null = null;
  isLoading = signal(false);
  isFetchingMetadata = signal(false);
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

  filterAuthors(event: AutoCompleteCompleteEvent): void {
    const query = event.query.toLowerCase();
    this.filteredAuthors = this.allAuthors.filter((author) =>
      author.toLowerCase().includes(query)
    );
  }

  filterCategories(event: AutoCompleteCompleteEvent): void {
    const query = event.query.toLowerCase();
    this.filteredCategories = this.allCategories.filter((category) =>
      category.toLowerCase().includes(query)
    );
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

  fetchMetadataByIsbn(): void {
    const isbnValue = this.isbn.trim();
    if (!isbnValue || this.isFetchingMetadata()) return;

    this.isFetchingMetadata.set(true);
    this.bookMetadataService.lookupByIsbn(isbnValue).subscribe({
      next: (metadata) => {
        if (metadata.title) this.title = metadata.title;
        if (metadata.authors?.length) this.authors = [...metadata.authors];
        if (metadata.description) this.description = metadata.description;
        if (metadata.publisher) this.publisher = metadata.publisher;
        if (metadata.publishedDate) this.publishedDate = metadata.publishedDate;
        if (metadata.language) this.language = metadata.language;
        if (metadata.pageCount) this.pageCount = metadata.pageCount;
        if (metadata.categories?.length) this.categories = [...metadata.categories];
        this.coverUrl = metadata.thumbnailUrl || null;
        this.isFetchingMetadata.set(false);
      },
      error: () => {
        this.isFetchingMetadata.set(false);
      }
    });
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
      error: () => {
        this.isLoading.set(false);
      }
    });
  }
}
