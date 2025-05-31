import {Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {Select, SelectChangeEvent} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {NgForOf} from '@angular/common';
import {Checkbox} from 'primeng/checkbox';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {FieldOptions, FieldProvider, MetadataRefreshOptions} from '../../model/request/metadata-refresh-options.model';

@Component({
  selector: 'app-metadata-advanced-fetch-options',
  templateUrl: './metadata-advanced-fetch-options.component.html',
  imports: [Select, FormsModule, NgForOf, Checkbox, Button],
  styleUrl: './metadata-advanced-fetch-options.component.scss',
  standalone: true
})
export class MetadataAdvancedFetchOptionsComponent implements OnChanges {

  @Output() metadataOptionsSubmitted: EventEmitter<MetadataRefreshOptions> = new EventEmitter<MetadataRefreshOptions>();
  fields: (keyof FieldOptions)[] = [
    'title',
    'subtitle',
    'description',
    'authors',
    'publisher',
    'publishedDate',
    'seriesName',
    'seriesNumber',
    'seriesTotal',
    'isbn13',
    'isbn10',
    'language',
    'categories',
    'cover'
  ];
  providers: string[] = ['Amazon', 'Google', 'GoodReads', 'Hardcover'];
  refreshCovers: boolean = false;
  mergeCategories: boolean = false;

  allP3 = {placeholder: 'Set All', value: null as string | null};
  allP2 = {placeholder: 'Set All', value: null as string | null};
  allP1 = {placeholder: 'Set All', value: null as string | null};

  fieldOptions: FieldOptions = {
    title: {p3: null, p2: null, p1: null},
    subtitle: {p3: null, p2: null, p1: null},
    description: {p3: null, p2: null, p1: null},
    authors: {p3: null, p2: null, p1: null},
    publisher: {p3: null, p2: null, p1: null},
    publishedDate: {p3: null, p2: null, p1: null},
    seriesName: {p3: null, p2: null, p1: null},
    seriesNumber: {p3: null, p2: null, p1: null},
    seriesTotal: {p3: null, p2: null, p1: null},
    isbn13: {p3: null, p2: null, p1: null},
    isbn10: {p3: null, p2: null, p1: null},
    language: {p3: null, p2: null, p1: null},
    categories: {p3: null, p2: null, p1: null},
    cover: {p3: null, p2: null, p1: null}
  };

  private messageService = inject(MessageService);
  @Input() currentMetadataOptions!: MetadataRefreshOptions;
  @Input() submitButtonLabel!: string;


  ngOnChanges(changes: SimpleChanges): void {
    if (changes['currentMetadataOptions'] && this.currentMetadataOptions) {
      this.refreshCovers = this.currentMetadataOptions.refreshCovers || false;
      this.mergeCategories = this.currentMetadataOptions.mergeCategories || false;

      const backendFieldOptions = this.currentMetadataOptions.fieldOptions as FieldOptions || {};

      for (const field of this.fields) {
        if (!backendFieldOptions[field]) {
          backendFieldOptions[field] = {p3: null, p2: null, p1: null};
        }
      }

      this.fieldOptions = backendFieldOptions;

      this.allP3.value = this.currentMetadataOptions.allP3 || null;
      this.allP2.value = this.currentMetadataOptions.allP2 || null;
      this.allP1.value = this.currentMetadataOptions.allP1 || null;
    }
  }

  syncProvider(event: SelectChangeEvent, providerType: keyof FieldProvider) {
    for (const field of Object.keys(this.fieldOptions)) {
      this.fieldOptions[field as keyof FieldOptions][providerType] = event.value;
    }
  }

  submit() {
    const allP3Selected = Object.keys(this.fieldOptions).every(field => {
      return this.fieldOptions[field as keyof FieldOptions].p3 !== null;
    });

    if (allP3Selected) {
      const metadataRefreshOptions: MetadataRefreshOptions = {
        allP1: this.allP1.value,
        allP2: this.allP2.value,
        allP3: this.allP3.value,
        refreshCovers: this.refreshCovers,
        mergeCategories: this.mergeCategories,
        fieldOptions: this.fieldOptions
      };

      this.metadataOptionsSubmitted.emit(metadataRefreshOptions);
    } else {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        life: 5000,
        detail: 'At least one provider must be selected for all the book fields.'
      });
    }
  }

  reset() {
    this.allP3.value = null;
    this.allP2.value = null;
    this.allP1.value = null;
    for (const field of Object.keys(this.fieldOptions)) {
      this.fieldOptions[field as keyof FieldOptions] = {
        p3: null,
        p2: null,
        p1: null
      };
    }
  }

  formatLabel(field: string): string {
    return field
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, str => str.toUpperCase())
      .trim();
  }

}
