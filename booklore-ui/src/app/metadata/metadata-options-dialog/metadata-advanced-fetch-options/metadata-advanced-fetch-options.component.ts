import {Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';

import {Checkbox} from 'primeng/checkbox';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {FieldOptions, MetadataRefreshOptions} from '../../model/request/metadata-refresh-options.model';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-metadata-advanced-fetch-options',
  templateUrl: './metadata-advanced-fetch-options.component.html',
  imports: [Select, FormsModule, Checkbox, Button, Tooltip],
  styleUrl: './metadata-advanced-fetch-options.component.scss',
  standalone: true
})
export class MetadataAdvancedFetchOptionsComponent implements OnChanges {

  @Output() metadataOptionsSubmitted = new EventEmitter<MetadataRefreshOptions>();
  @Input() currentMetadataOptions!: MetadataRefreshOptions;
  @Input() submitButtonLabel!: string;

  fields: (keyof FieldOptions)[] = [
    'title', 'subtitle', 'description', 'authors', 'publisher', 'publishedDate',
    'seriesName', 'seriesNumber', 'seriesTotal', 'isbn13', 'isbn10',
    'language', 'categories', 'cover', 'pageCount',
    'asin', 'goodreadsId', 'comicvineId', 'hardcoverId', 'googleId',
    'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverRating', 'hardcoverReviewCount', 'moods', 'tags'
  ];

  providerSpecificFields: (keyof FieldOptions)[] = [
    'asin', 'goodreadsId', 'comicvineId', 'hardcoverId', 'googleId',
    'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverRating', 'hardcoverReviewCount', 'moods', 'tags'
  ];

  nonProviderSpecificFields: (keyof FieldOptions)[] = [
    'title', 'subtitle', 'description', 'authors', 'publisher', 'publishedDate',
    'seriesName', 'seriesNumber', 'seriesTotal', 'isbn13', 'isbn10',
    'language', 'categories', 'cover', 'pageCount',
  ];

  providers: string[] = ['Amazon', 'Google', 'GoodReads', 'Hardcover', 'Comicvine', 'Douban'];
  providersWithClear: string[] = ['Clear All', 'Amazon', 'Google', 'GoodReads', 'Hardcover', 'Comicvine', 'Douban'];

  refreshCovers: boolean = false;
  mergeCategories: boolean = false;
  reviewBeforeApply: boolean = false;

  fieldOptions: FieldOptions = this.initializeFieldOptions();
  skipFields: Record<keyof FieldOptions, boolean> = this.initializeSkipFields();

  bulkP1: string | null = null;
  bulkP2: string | null = null;
  bulkP3: string | null = null;
  bulkP4: string | null = null;

  private messageService = inject(MessageService);

  private justSubmitted = false;

  private providerSpecificFieldsList = [
    'asin', 'goodreadsId', 'comicvineId', 'hardcoverId', 'googleId',
    'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverRating', 'hardcoverReviewCount', 'moods', 'tags'
  ];

  private initializeFieldOptions(): FieldOptions {
    return this.fields.reduce((acc, field) => {
      acc[field] = {p1: null, p2: null, p3: null, p4: null};
      return acc;
    }, {} as FieldOptions);
  }

  private initializeSkipFields(): Record<keyof FieldOptions, boolean> {
    return this.fields.reduce((acc, field) => {
      acc[field] = false;
      return acc;
    }, {} as Record<keyof FieldOptions, boolean>);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['currentMetadataOptions'] && this.currentMetadataOptions && !this.justSubmitted) {
      this.refreshCovers = this.currentMetadataOptions.refreshCovers || false;
      this.mergeCategories = this.currentMetadataOptions.mergeCategories || false;
      this.reviewBeforeApply = this.currentMetadataOptions.reviewBeforeApply || false;

      const backendFieldOptions = this.deepCloneFieldOptions(this.currentMetadataOptions.fieldOptions as FieldOptions || {});
      for (const field of this.fields) {
        if (!backendFieldOptions[field]) {
          backendFieldOptions[field] = {p1: null, p2: null, p3: null, p4: null};
        } else {
          backendFieldOptions[field].p4 ??= null;
        }
      }
      this.fieldOptions = backendFieldOptions;

      if (this.currentMetadataOptions.skipFields) {
        this.skipFields = {...this.skipFields, ...this.currentMetadataOptions.skipFields};
      } else {
        this.skipFields = this.initializeSkipFields();
      }
    }
  }

  private deepCloneFieldOptions(fieldOptions: FieldOptions): FieldOptions {
    const cloned = {} as FieldOptions;
    for (const field of this.fields) {
      cloned[field] = {
        p1: fieldOptions[field]?.p1 || null,
        p2: fieldOptions[field]?.p2 || null,
        p3: fieldOptions[field]?.p3 || null,
        p4: fieldOptions[field]?.p4 || null
      };
    }
    return cloned;
  }

  submit() {
    const allFieldsHaveProvider = Object.entries(this.fieldOptions).every(([field, opt]) =>
      this.skipFields[field as keyof FieldOptions] ||
      this.isProviderSpecificField(field as keyof FieldOptions) ||
      opt.p1 !== null || opt.p2 !== null || opt.p3 !== null || opt.p4 !== null
    );

    if (allFieldsHaveProvider) {
      this.justSubmitted = true;

      const metadataRefreshOptions: MetadataRefreshOptions = {
        libraryId: null,
        refreshCovers: this.refreshCovers,
        mergeCategories: this.mergeCategories,
        reviewBeforeApply: this.reviewBeforeApply,
        fieldOptions: this.fieldOptions,
        skipFields: this.skipFields
      };

      this.metadataOptionsSubmitted.emit(metadataRefreshOptions);

      setTimeout(() => {
        this.justSubmitted = false;
      }, 1000);
    } else {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'At least one provider (P1–P4) must be selected for each non-skipped book field.',
        life: 5000
      });
    }
  }

  setBulkProvider(priority: 'p1' | 'p2' | 'p3' | 'p4', provider: string | null): void {
    if (!provider) return;

    const value = provider === 'Clear All' ? null : provider;

    for (const field of this.nonProviderSpecificFields) {
      if (!this.skipFields[field]) {
        this.fieldOptions[field][priority] = value;
      }
    }

    switch (priority) {
      case 'p1':
        this.bulkP1 = null;
        break;
      case 'p2':
        this.bulkP2 = null;
        break;
      case 'p3':
        this.bulkP3 = null;
        break;
      case 'p4':
        this.bulkP4 = null;
        break;
    }
  }

  reset() {
    this.justSubmitted = false;
    for (const field of Object.keys(this.fieldOptions)) {
      this.fieldOptions[field as keyof FieldOptions] = {
        p1: null,
        p2: null,
        p3: null,
        p4: null
      };
    }
    this.skipFields = this.initializeSkipFields();

    // Reset bulk selectors
    this.bulkP1 = null;
    this.bulkP2 = null;
    this.bulkP3 = null;
    this.bulkP4 = null;
  }

  formatLabel(field: string): string {
    const fieldLabels: Record<string, string> = {
      'title': 'Title',
      'subtitle': 'Subtitle',
      'description': 'Description',
      'authors': 'Authors',
      'publisher': 'Publisher',
      'publishedDate': 'Published Date',
      'seriesName': 'Series Name',
      'seriesNumber': 'Series Number',
      'seriesTotal': 'Series Total',
      'isbn13': 'ISBN-13',
      'isbn10': 'ISBN-10',
      'language': 'Language',
      'categories': 'Genres',
      'cover': 'Cover Image',
      'pageCount': 'Page Count',
      'rating': 'Rating',
      'reviewCount': 'Review Count',
      'asin': 'Amazon ASIN',
      'goodreadsId': 'Goodreads ID',
      'comicvineId': 'Comicvine ID',
      'hardcoverId': 'Hardcover ID',
      'googleId': 'Google Books ID',
      'amazonRating': 'Amazon Rating',
      'amazonReviewCount': 'Amazon Review Count',
      'goodreadsRating': 'Goodreads Rating',
      'goodreadsReviewCount': 'Goodreads Review Count',
      'hardcoverRating': 'Hardcover Rating',
      'hardcoverReviewCount': 'Hardcover Review Count',
      'moods': 'Moods (Hardcover)',
      'tags': 'Tags (Hardcover)'
    };

    return fieldLabels[field] || field.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase()).trim();
  }

  isProviderSpecificField(field: keyof FieldOptions): boolean {
    return this.providerSpecificFieldsList.includes(field as string);
  }
}
