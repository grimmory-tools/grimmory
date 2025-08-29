import {Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {NgClass} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {InputText} from 'primeng/inputtext';
import {BookMetadata} from '../../book/model/book.model';
import {UrlHelperService} from '../../utilities/service/url-helper.service';
import {Textarea} from 'primeng/textarea';
import {AutoComplete} from 'primeng/autocomplete';
import {Image} from 'primeng/image';
import {LazyLoadImageModule} from 'ng-lazyload-image';

@Component({
  selector: 'app-bookdrop-file-metadata-picker-component',
  imports: [
    ReactiveFormsModule,
    Button,
    Tooltip,
    InputText,
    NgClass,
    FormsModule,
    Textarea,
    AutoComplete,
    Image,
    LazyLoadImageModule
  ],
  templateUrl: './bookdrop-file-metadata-picker.component.html',
  styleUrl: './bookdrop-file-metadata-picker.component.scss'
})
export class BookdropFileMetadataPickerComponent {

  @Input() fetchedMetadata!: BookMetadata;
  @Input() originalMetadata?: BookMetadata;
  @Input() metadataForm!: FormGroup;
  @Input() copiedFields: Record<string, boolean> = {};
  @Input() savedFields: Record<string, boolean> = {};
  @Input() bookdropFileId!: number;

  @Output() metadataCopied = new EventEmitter<boolean>();


  metadataFieldsTop = [
    {label: 'Title', controlName: 'title', fetchedKey: 'title'},
    {label: 'Publisher', controlName: 'publisher', fetchedKey: 'publisher'},
    {label: 'Published', controlName: 'publishedDate', fetchedKey: 'publishedDate'}
  ];

  metadataChips = [
    {label: 'Authors', controlName: 'authors', lockedKey: 'authorsLocked', fetchedKey: 'authors'},
    {label: 'Categories', controlName: 'categories', lockedKey: 'categoriesLocked', fetchedKey: 'categories'}
  ];

  metadataDescription = [
    {label: 'Description', controlName: 'description', lockedKey: 'descriptionLocked', fetchedKey: 'description'},
  ];

  metadataFieldsBottom = [
    {label: 'Series', controlName: 'seriesName', lockedKey: 'seriesNameLocked', fetchedKey: 'seriesName'},
    {label: 'Book #', controlName: 'seriesNumber', lockedKey: 'seriesNumberLocked', fetchedKey: 'seriesNumber'},
    {label: 'Total Books', controlName: 'seriesTotal', lockedKey: 'seriesTotalLocked', fetchedKey: 'seriesTotal'},
    {label: 'Language', controlName: 'language', lockedKey: 'languageLocked', fetchedKey: 'language'},
    {label: 'ISBN-10', controlName: 'isbn10', lockedKey: 'isbn10Locked', fetchedKey: 'isbn10'},
    {label: 'ISBN-13', controlName: 'isbn13', lockedKey: 'isbn13Locked', fetchedKey: 'isbn13'},
    {label: 'ASIN', controlName: 'asin', lockedKey: 'asinLocked', fetchedKey: 'asin'},
    {label: 'Amz Reviews', controlName: 'amazonReviewCount', lockedKey: 'amazonReviewCountLocked', fetchedKey: 'amazonReviewCount'},
    {label: 'Amz Rating', controlName: 'amazonRating', lockedKey: 'amazonRatingLocked', fetchedKey: 'amazonRating'},
    {label: 'GR ID', controlName: 'goodreadsId', lockedKey: 'goodreadsIdLocked', fetchedKey: 'goodreadsId'},
    {label: 'GR Reviews', controlName: 'goodreadsReviewCount', lockedKey: 'goodreadsReviewCountLocked', fetchedKey: 'goodreadsReviewCount'},
    {label: 'GR Rating', controlName: 'goodreadsRating', lockedKey: 'goodreadsRatingLocked', fetchedKey: 'goodreadsRating'},
    {label: 'HC ID', controlName: 'hardcoverId', lockedKey: 'hardcoverIdLocked', fetchedKey: 'hardcoverId'},
    {label: 'HC Reviews', controlName: 'hardcoverReviewCount', lockedKey: 'hardcoverReviewCountLocked', fetchedKey: 'hardcoverReviewCount'},
    {label: 'HC Rating', controlName: 'hardcoverRating', lockedKey: 'hardcoverRatingLocked', fetchedKey: 'hardcoverRating'},
    {label: 'Google ID', controlName: 'googleId', lockedKey: 'googleIdLocked', fetchedKey: 'googleIdRating'},
    {label: 'Comicvine ID', controlName: 'comicvineId', lockedKey: 'comicvineIdLocked', fetchedKey: 'comicvineId'},
    {label: 'Pages', controlName: 'pageCount', lockedKey: 'pageCountLocked', fetchedKey: 'pageCount'}
  ];

  protected urlHelper = inject(UrlHelperService);

  copyMissing(): void {
    Object.keys(this.fetchedMetadata).forEach((field) => {
      if (!this.metadataForm.get(field)?.value && this.fetchedMetadata[field]) {
        this.copyFetchedToCurrent(field);
      }
    });
  }

  copyAll() {
    if (this.fetchedMetadata) {
      Object.keys(this.fetchedMetadata).forEach((field) => {
        if (this.fetchedMetadata[field] && field !== 'thumbnailUrl') {
          this.copyFetchedToCurrent(field);
        }
      });
    }
  }

  copyFetchedToCurrent(field: string): void {
    const value = this.fetchedMetadata[field];
    if (value && !this.copiedFields[field]) {
      this.metadataForm.get(field)?.setValue(value);
      this.copiedFields[field] = true;
      this.highlightCopiedInput(field);
      this.metadataCopied.emit(true);
    }
  }

  highlightCopiedInput(field: string): void {
    this.copiedFields[field] = true;
  }

  isValueCopied(field: string): boolean {
    return this.copiedFields[field];
  }

  isValueSaved(field: string): boolean {
    return this.savedFields[field];
  }

  hoveredFields: { [key: string]: boolean } = {};

  onMouseEnter(controlName: string): void {
    if (this.isValueCopied(controlName) && !this.isValueSaved(controlName)) {
      this.hoveredFields[controlName] = true;
    }
  }

  onMouseLeave(controlName: string): void {
    this.hoveredFields[controlName] = false;
  }

  resetField(field: string) {
    this.metadataForm.get(field)?.setValue(this.originalMetadata?.[field]);
    this.copiedFields[field] = false;
    this.hoveredFields[field] = false;
    if (field === 'thumbnailUrl') {
      this.metadataForm.get('thumbnailUrl')?.setValue(this.urlHelper.getBookdropCoverUrl(this.bookdropFileId));
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

  resetAll() {
    if (this.originalMetadata) {
      this.metadataForm.patchValue({
        title: this.originalMetadata.title || null,
        subtitle: this.originalMetadata.subtitle || null,
        authors: [...(this.originalMetadata.authors ?? [])].sort(),
        categories: [...(this.originalMetadata.categories ?? [])].sort(),
        publisher: this.originalMetadata.publisher || null,
        publishedDate: this.originalMetadata.publishedDate || null,
        isbn10: this.originalMetadata.isbn10 || null,
        isbn13: this.originalMetadata.isbn13 || null,
        description: this.originalMetadata.description || null,
        pageCount: this.originalMetadata.pageCount || null,
        language: this.originalMetadata.language || null,
        asin: this.originalMetadata.asin || null,
        amazonRating: this.originalMetadata.amazonRating || null,
        amazonReviewCount: this.originalMetadata.amazonReviewCount || null,
        goodreadsId: this.originalMetadata.goodreadsId || null,
        goodreadsRating: this.originalMetadata.goodreadsRating || null,
        goodreadsReviewCount: this.originalMetadata.goodreadsReviewCount || null,
        hardcoverId: this.originalMetadata.hardcoverId || null,
        hardcoverRating: this.originalMetadata.hardcoverRating || null,
        hardcoverReviewCount: this.originalMetadata.hardcoverReviewCount || null,
        googleId: this.originalMetadata.googleId || null,
        comicvineId: this.originalMetadata.comicvineId || null,
        seriesName: this.originalMetadata.seriesName || null,
        seriesNumber: this.originalMetadata.seriesNumber || null,
        seriesTotal: this.originalMetadata.seriesTotal || null,
        thumbnailUrl: this.urlHelper.getBookdropCoverUrl(this.bookdropFileId),
      });
    }
    this.copiedFields = {};
    this.hoveredFields = {};
    this.metadataCopied.emit(false);
  }
}
