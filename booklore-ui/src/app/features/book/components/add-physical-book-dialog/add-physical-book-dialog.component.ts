import {Component, DestroyRef, inject, OnInit} from '@angular/core';
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
import {LibraryService} from '../../service/library.service';
import {Library} from '../../model/library.model';
import {CreatePhysicalBookRequest} from '../../model/book.model';
import {filter, take} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
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
export class AddPhysicalBookDialogComponent implements OnInit {
  private dynamicDialogRef = inject(DynamicDialogRef);
  private dialogConfig = inject(DynamicDialogConfig);
  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private destroyRef = inject(DestroyRef);

  libraries: Library[] = [];
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

  allAuthors: string[] = [];
  allCategories: string[] = [];
  filteredAuthors: string[] = [];
  filteredCategories: string[] = [];

  isLoading: boolean = false;

  ngOnInit(): void {
    this.libraryService.libraryState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(state => {
        if (state.loaded && state.libraries) {
          this.libraries = state.libraries;
          if (this.dialogConfig.data?.libraryId) {
            this.selectedLibraryId = this.dialogConfig.data.libraryId;
          } else if (this.libraries.length > 0 && this.libraries[0].id !== undefined) {
            this.selectedLibraryId = this.libraries[0].id;
          }
        }
      });
    this.prepareAutoComplete();
  }

  private prepareAutoComplete(): void {
    this.bookService.bookState$
      .pipe(
        filter((bookState) => bookState.loaded),
        take(1)
      )
      .subscribe((bookState) => {
        const authors = new Set<string>();
        const categories = new Set<string>();

        (bookState.books ?? []).forEach((book) => {
          book.metadata?.authors?.forEach((author) => authors.add(author));
          book.metadata?.categories?.forEach((category) => categories.add(category));
        });

        this.allAuthors = Array.from(authors);
        this.allCategories = Array.from(categories);
      });
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

  cancel(): void {
    this.dynamicDialogRef.close();
  }

  canCreate(): boolean {
    return !!this.selectedLibraryId && (!!this.title.trim() || !!this.isbn.trim());
  }

  createBook(): void {
    if (!this.canCreate() || this.isLoading) return;

    this.isLoading = true;

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
      categories: this.categories.length > 0 ? this.categories : undefined
    };

    this.bookService.createPhysicalBook(request).subscribe({
      next: (book) => {
        this.isLoading = false;
        this.dynamicDialogRef.close(book);
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }
}
