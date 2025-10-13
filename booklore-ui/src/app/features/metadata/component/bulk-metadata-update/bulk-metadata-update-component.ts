import {Component, inject, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
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

@Component({
  selector: 'app-bulk-metadata-update-component',
  standalone: true,
  imports: [
    CommonModule,
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

  // Handle blur event for AutoComplete to add custom values
  onAutoCompleteBlur(fieldName: string, event: any) {
    const inputValue = event.target.value?.trim();
    if (inputValue) {
      const currentValue = this.metadataForm.get(fieldName)?.value || [];
      const values = Array.isArray(currentValue) ? currentValue :
                     typeof currentValue === 'string' && currentValue ? currentValue.split(',').map((v: string) => v.trim()) : [];

      // Add the new value if it's not already in the array
      if (!values.includes(inputValue)) {
        values.push(inputValue);
        this.metadataForm.get(fieldName)?.setValue(values);
      }
      // Clear the input
      event.target.value = '';
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
      clearTags: this.clearFields.tags
    };

    this.loading = true;
    this.bookService.updateBooksMetadata(payload, this.mergeCategories).subscribe({
      next: updatedBooks => {
        this.loading = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Metadata Updated',
          detail: `${updatedBooks.length} book${updatedBooks.length > 1 ? 's' : ''} updated successfully`
        });
        this.ref.close(true);
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
}
