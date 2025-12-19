import {Component, inject, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';

import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {DatePicker} from 'primeng/datepicker';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {BookService} from '../../../book/service/book.service';
import {Book, BulkMetadataUpdateRequest} from '../../../book/model/book.model';
import {Checkbox} from 'primeng/checkbox';
import {AutoComplete} from 'primeng/autocomplete';
import {ProgressSpinner} from 'primeng/progressspinner';
import {filter, take} from "rxjs/operators";

@Component({
  selector: 'app-bulk-metadata-update-component',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    InputText,
    Button,
    Tooltip,
    DatePicker,
    Checkbox,
    ProgressSpinner,
    AutoComplete
],
  providers: [MessageService],
  templateUrl: './bulk-metadata-update-component.html',
  styleUrl: './bulk-metadata-update-component.scss'
})
export class BulkMetadataUpdateComponent implements OnInit {
  metadataForm!: FormGroup;
  bookIds: number[] = [];
  books: Book[] = [];
  showBookList = true;
  mergeCategories = true;
  mergeMoods = true;
  mergeTags = true;
  loading = false;
  selectedCoverFile: File | null = null;

  clearFields = {
    authors: false,
    publisher: false,
    language: false,
    seriesName: false,
    seriesTotal: false,
    publishedDate: false,
    genres: false,
    moods: false,
    tags: false,
  };

  private readonly config = inject(DynamicDialogConfig);
  private readonly ref = inject(DynamicDialogRef);
  private readonly fb = inject(FormBuilder);
  private readonly bookService = inject(BookService);
  private readonly messageService = inject(MessageService);

  allAuthors!: string[];
  allGenres!: string[];
  allMoods!: string[];
  allTags!: string[];
  allPublishers!: string[];
  allSeries!: string[];
  filteredGenres: string[] = [];
  filteredAuthors: string[] = [];
  filteredMoods: string[] = [];
  filteredTags: string[] = [];
  filteredPublishers: string[] = [];
  filteredSeries: string[] = [];

  filterGenres(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredGenres = this.allGenres.filter((cat) =>
      cat.toLowerCase().includes(query)
    );
  }

  filterAuthors(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredAuthors = this.allAuthors.filter((author) =>
      author.toLowerCase().includes(query)
    );
  }

  filterMoods(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredMoods = this.allMoods.filter((mood) =>
      mood.toLowerCase().includes(query)
    );
  }

  filterTags(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredTags = this.allTags.filter((tag) =>
      tag.toLowerCase().includes(query)
    );
  }

  filterPublishers(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredPublishers = this.allPublishers.filter((publisher) =>
      publisher.toLowerCase().includes(query)
    );
  }

  filterSeries(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredSeries = this.allSeries.filter((seriesName) =>
      seriesName.toLowerCase().includes(query)
    );
  }

  ngOnInit(): void {
    this.bookIds = this.config.data?.bookIds ?? [];
    this.books = this.bookService.getBooksByIdsFromState(this.bookIds);

    this.metadataForm = this.fb.group({
      authors: [],
      publisher: [''],
      language: [''],
      seriesName: [''],
      seriesTotal: [''],
      publishedDate: [null],
      genres: [],
      moods: [],
      tags: []
    });

    this.bookService.bookState$
      .pipe(
        filter((bookState) => bookState.loaded),
        take(1)
      )
      .subscribe((bookState) => {
        const authors = new Set<string>();
        const categories = new Set<string>();
        const moods = new Set<string>();
        const tags = new Set<string>();
        const publishers = new Set<string>();
        const series = new Set<string>();

        (bookState.books ?? []).forEach((book) => {
          book.metadata?.authors?.forEach((author) => authors.add(author));
          book.metadata?.categories?.forEach((category) =>
            categories.add(category)
          );
          book.metadata?.moods?.forEach((mood) => moods.add(mood));
          book.metadata?.tags?.forEach((tag) => tags.add(tag));
          if (book.metadata?.publisher) {
            publishers.add(book.metadata.publisher);
          }
          if (book.metadata?.seriesName) {
            series.add(book.metadata.seriesName);
          }
        });

        this.allAuthors = Array.from(authors);
        this.allGenres = Array.from(categories);
        this.allMoods = Array.from(moods);
        this.allTags = Array.from(tags);
        this.allPublishers = Array.from(publishers);
        this.allSeries = Array.from(series);
      });
  }

  onFieldClearToggle(field: keyof typeof this.clearFields): void {
    const control = this.metadataForm.get(field);
    if (!control) return;

    if (this.clearFields[field]) {
      control.disable();
      control.setValue(null);
    } else {
      control.enable();
    }
  }

  onAutoCompleteSelect(fieldName: string, event: any) {
    const values = this.metadataForm.get(fieldName)?.value || [];
    if (!values.includes(event.value)) {
      this.metadataForm.get(fieldName)?.setValue([...values, event.value]);
    }
    (event.originalEvent.target as HTMLInputElement).value = "";
  }

  onAutoCompleteKeyUp(fieldName: string, event: KeyboardEvent) {
    if (event.key === "Enter") {
      const input = event.target as HTMLInputElement;
      const value = input.value?.trim();
      if (value) {
        const values = this.metadataForm.get(fieldName)?.value || [];
        if (!values.includes(value)) {
          this.metadataForm.get(fieldName)?.setValue([...values, value]);
        }
        input.value = "";
      }
    }
  }

  onFormKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      if ((event.target as HTMLElement)?.tagName === 'BUTTON' &&
        (event.target as HTMLButtonElement)?.type === 'submit') {
        return;
      }
      event.preventDefault();
    }
  }

  onSubmit(): void {
    if (!this.metadataForm.valid) return;

    const formValue = this.metadataForm.value;

    const payload: BulkMetadataUpdateRequest = {
      bookIds: this.bookIds,

      authors: this.clearFields.authors ? [] : (formValue.authors?.length ? formValue.authors : undefined),
      clearAuthors: this.clearFields.authors,

      publisher: this.clearFields.publisher ? '' : (formValue.publisher?.trim() || undefined),
      clearPublisher: this.clearFields.publisher,

      language: this.clearFields.language ? '' : (formValue.language?.trim() || undefined),
      clearLanguage: this.clearFields.language,

      seriesName: this.clearFields.seriesName ? '' : (formValue.seriesName?.trim() || undefined),
      clearSeriesName: this.clearFields.seriesName,

      seriesTotal: this.clearFields.seriesTotal ? null : (formValue.seriesTotal || undefined),
      clearSeriesTotal: this.clearFields.seriesTotal,

      publishedDate: this.clearFields.publishedDate
        ? null
        : (formValue.publishedDate ? new Date(formValue.publishedDate).toISOString().split('T')[0] : undefined),
      clearPublishedDate: this.clearFields.publishedDate,

      genres: this.clearFields.genres ? [] : (formValue.genres?.length ? formValue.genres : undefined),
      clearGenres: this.clearFields.genres,

      moods: this.clearFields.moods ? [] : (formValue.moods?.length ? formValue.moods : undefined),
      clearMoods: this.clearFields.moods,

      tags: this.clearFields.tags ? [] : (formValue.tags?.length ? formValue.tags : undefined),
      clearTags: this.clearFields.tags,

      mergeCategories: this.mergeCategories,
      mergeMoods: this.mergeMoods,
      mergeTags: this.mergeTags
    };

    this.loading = true;
    this.bookService.updateBooksMetadata(payload).subscribe({
      next: () => {
        if (this.selectedCoverFile) {
          this.bookService.bulkUploadCover(this.bookIds, this.selectedCoverFile).subscribe({
            next: () => {
              this.loading = false;
              this.messageService.add({
                severity: 'success',
                summary: 'Metadata & Cover Updated',
                detail: 'Books updated and cover upload started. Refresh the page when complete.'
              });
              this.ref.close(true);
            },
            error: err => {
              console.error('Bulk cover upload failed:', err);
              this.loading = false;
              this.messageService.add({
                severity: 'warn',
                summary: 'Partial Success',
                detail: 'Metadata updated but cover upload failed'
              });
              this.ref.close(true);
            }
          });
        } else {
          this.loading = false;
          this.messageService.add({
            severity: 'success',
            summary: 'Metadata Updated',
            detail: 'Books updated successfully'
          });
          this.ref.close(true);
        }
      },
      error: err => {
        console.error('Bulk metadata update failed:', err);
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Update Failed',
          detail: 'An error occurred while updating book metadata'
        });
      }
    });
  }

  onCoverFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedCoverFile = input.files[0];
    }
  }

  clearCoverFile(): void {
    this.selectedCoverFile = null;
  }
}
