import {Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {Book, BookMetadata, MetadataClearFlags, MetadataUpdateWrapper} from '../../../../book/model/book.model';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {AsyncPipe, NgClass} from '@angular/common';
import {Divider} from 'primeng/divider';
import {Observable} from 'rxjs';
import {Tooltip} from 'primeng/tooltip';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {BookService} from '../../../../book/service/book.service';
import {Textarea} from 'primeng/textarea';
import {filter, map, take} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AutoComplete, AutoCompleteSelectEvent} from 'primeng/autocomplete';
import {Image} from 'primeng/image';
import {LazyLoadImageModule} from 'ng-lazyload-image';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataProviderSpecificFields} from '../../../../../shared/model/app-settings.model';
import {ALL_METADATA_FIELDS, getArrayFields, getBottomFields, getTextareaFields, getTopFields, MetadataFieldConfig, MetadataFormBuilder, MetadataUtilsService} from '../../../../../shared/metadata';

@Component({
  selector: 'app-metadata-picker',
  standalone: true,
  templateUrl: './metadata-picker.component.html',
  styleUrls: ['./metadata-picker.component.scss'],
  imports: [
    Button,
    FormsModule,
    InputText,
    Divider,
    ReactiveFormsModule,
    NgClass,
    Tooltip,
    AsyncPipe,
    Textarea,
    AutoComplete,
    Image,
    LazyLoadImageModule
  ]
})
export class MetadataPickerComponent implements OnInit {

  // Cached arrays for template binding (avoid getter re-computation)
  metadataFieldsTop: MetadataFieldConfig[] = [];
  metadataChips: MetadataFieldConfig[] = [];
  metadataDescription: MetadataFieldConfig[] = [];
  metadataFieldsBottom: MetadataFieldConfig[] = [];

  @Input() reviewMode!: boolean;
  @Input() fetchedMetadata!: BookMetadata;
  @Input() book$!: Observable<Book | null>;
  @Output() goBack = new EventEmitter<boolean>();

  private allItems: Record<string, string[]> = {};
  filteredItems: Record<string, string[]> = {};

  metadataForm!: FormGroup;
  currentBookId!: number;
  copiedFields: Record<string, boolean> = {};
  savedFields: Record<string, boolean> = {};
  originalMetadata!: BookMetadata;
  isSaving = false;
  hoveredFields: Record<string, boolean> = {};

  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  protected urlHelper = inject(UrlHelperService);
  private destroyRef = inject(DestroyRef);
  private appSettingsService = inject(AppSettingsService);
  private formBuilder = inject(MetadataFormBuilder);
  private metadataUtils = inject(MetadataUtilsService);

  private enabledProviderFields: MetadataProviderSpecificFields | null = null;

  constructor() {
    this.metadataForm = this.formBuilder.buildForm(true);
    this.initFieldArrays();
  }

  private initFieldArrays(): void {
    this.metadataFieldsTop = getTopFields();
    this.metadataChips = getArrayFields();
    this.metadataDescription = getTextareaFields();
    this.updateBottomFields();
  }

  private updateBottomFields(): void {
    this.metadataFieldsBottom = getBottomFields(this.enabledProviderFields);
  }

  getFiltered(controlName: string): string[] {
    return this.filteredItems[controlName] ?? [];
  }

  filterItems(event: { query: string }, controlName: string): void {
    const query = event.query.toLowerCase();
    this.filteredItems[controlName] = (this.allItems[controlName] ?? [])
      .filter(item => item.toLowerCase().includes(query));
  }

  ngOnInit(): void {
    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => !!settings?.metadataProviderSpecificFields),
        take(1)
      )
      .subscribe(settings => {
        if (settings?.metadataProviderSpecificFields) {
          this.enabledProviderFields = settings.metadataProviderSpecificFields;
          this.updateBottomFields();
        }
      });

    this.bookService.bookState$
      .pipe(
        filter(bookState => bookState.loaded),
        take(1)
      )
      .subscribe(bookState => {
        const itemSets: Record<string, Set<string>> = {
          authors: new Set<string>(),
          categories: new Set<string>(),
          moods: new Set<string>(),
          tags: new Set<string>()
        };

        (bookState.books ?? []).forEach(book => {
          for (const key of Object.keys(itemSets)) {
            const values = book.metadata?.[key as keyof BookMetadata] as string[] | undefined;
            values?.forEach(v => itemSets[key].add(v));
          }
        });

        for (const key of Object.keys(itemSets)) {
          this.allItems[key] = Array.from(itemSets[key]);
        }
      });

    this.book$
      .pipe(
        filter((book): book is Book => !!book && !!book.metadata),
        map(book => book.metadata),
        takeUntilDestroyed(this.destroyRef)
      ).subscribe((metadata) => {
      if (this.reviewMode) {
        this.metadataForm.reset();
        this.copiedFields = {};
        this.savedFields = {};
        this.hoveredFields = {};
      }

      if (metadata) {
        this.originalMetadata = metadata;
        this.originalMetadata.thumbnailUrl = this.urlHelper.getThumbnailUrl(metadata.bookId, metadata.coverUpdatedOn);
        this.currentBookId = metadata.bookId;
        this.patchMetadataToForm(metadata);
      }
    });
  }

  private patchMetadataToForm(metadata: BookMetadata): void {
    const patchData: Record<string, unknown> = {};

    for (const field of ALL_METADATA_FIELDS) {
      const key = field.controlName as keyof BookMetadata;
      const lockedKey = field.lockedKey as keyof BookMetadata;
      const value = metadata[key];

      if (field.type === 'array') {
        patchData[field.controlName] = [...(value as string[] ?? [])].sort();
      } else {
        patchData[field.controlName] = value ?? null;
      }
      patchData[field.lockedKey] = metadata[lockedKey] ?? false;
    }

    // Handle special fields
    patchData['thumbnailUrl'] = this.urlHelper.getCoverUrl(metadata.bookId, metadata.coverUpdatedOn);
    patchData['coverLocked'] = metadata.coverLocked ?? false;

    this.metadataForm.patchValue(patchData);
    this.applyLockStates(metadata);
  }

  private applyLockStates(metadata: BookMetadata): void {
    const lockedFields: Record<string, boolean> = {};
    for (const field of ALL_METADATA_FIELDS) {
      lockedFields[field.lockedKey] = !!metadata[field.lockedKey as keyof BookMetadata];
    }
    this.formBuilder.applyLockStates(this.metadataForm, lockedFields);
  }

  onAutoCompleteSelect(fieldName: string, event: AutoCompleteSelectEvent) {
    const values = (this.metadataForm.get(fieldName)?.value as string[]) || [];
    if (!values.includes(event.value as string)) {
      this.metadataForm.get(fieldName)?.setValue([...values, event.value as string]);
    }
    (event.originalEvent.target as HTMLInputElement).value = '';
  }

  onAutoCompleteKeyUp(fieldName: string, event: KeyboardEvent) {
    if (event.key === 'Enter') {
      const input = event.target as HTMLInputElement;
      const value = input.value?.trim();
      if (value) {
        const values = this.metadataForm.get(fieldName)?.value || [];
        if (!values.includes(value)) {
          this.metadataForm.get(fieldName)?.setValue([...values, value]);
        }
        input.value = '';
      }
    }
  }

  onSave(): void {
    this.isSaving = true;
    const updatedBookMetadata = this.buildMetadataWrapper(undefined);
    this.bookService.updateBookMetadata(this.currentBookId, updatedBookMetadata, false).subscribe({
      next: () => {
        this.isSaving = false;
        for (const field of Object.keys(this.copiedFields)) {
          if (this.copiedFields[field]) {
            this.savedFields[field] = true;
          }
        }
        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Book metadata updated'});
      },
      error: () => {
        this.isSaving = false;
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update book metadata'});
      }
    });
  }

  private buildMetadataWrapper(shouldLockAllFields: boolean | undefined): MetadataUpdateWrapper {
    const metadata = this.buildMetadataFromForm();

    if (shouldLockAllFields !== undefined) {
      (metadata as BookMetadata & { allFieldsLocked?: boolean }).allFieldsLocked = shouldLockAllFields;
    }

    const clearFlags = this.inferClearFlags(metadata, this.originalMetadata);

    return {
      metadata: metadata,
      clearFlags: clearFlags
    };
  }

  private buildMetadataFromForm(): BookMetadata {
    const metadata: Record<string, unknown> = {bookId: this.currentBookId};

    for (const field of ALL_METADATA_FIELDS) {
      if (field.type === 'array') {
        metadata[field.controlName] = this.getArrayValue(field.controlName);
      } else if (field.type === 'number') {
        metadata[field.controlName] = this.getNumberValue(field.controlName);
      } else {
        metadata[field.controlName] = this.getStringValue(field.controlName);
      }

      metadata[field.lockedKey] = this.metadataForm.get(field.lockedKey)?.value ?? false;
    }

    metadata['thumbnailUrl'] = this.getThumbnail();
    metadata['coverLocked'] = this.metadataForm.get('coverLocked')?.value;

    return metadata as BookMetadata;
  }

  private getStringValue(field: string): string {
    const formValue = this.metadataForm.get(field)?.value;
    if (!formValue || formValue === '') {
      if (this.copiedFields[field]) {
        return (this.fetchedMetadata[field as keyof BookMetadata] as string) || '';
      }
      return '';
    }
    return formValue;
  }

  private getNumberValue(field: string): number | null {
    const formValue = this.metadataForm.get(field)?.value;
    if (formValue === '' || formValue === null || formValue === undefined || isNaN(formValue)) {
      if (this.copiedFields[field]) {
        return (this.fetchedMetadata[field as keyof BookMetadata] as number | null) ?? null;
      }
      return null;
    }
    return Number(formValue);
  }

  private getArrayValue(field: string): string[] {
    const fieldValue = this.metadataForm.get(field)?.value;
    if (!fieldValue || (Array.isArray(fieldValue) && fieldValue.length === 0)) {
      if (this.copiedFields[field]) {
        const fallback = this.fetchedMetadata[field as keyof BookMetadata];
        return Array.isArray(fallback) ? fallback as string[] : [];
      }
      return [];
    }
    if (typeof fieldValue === 'string') {
      return fieldValue.split(',').map(item => item.trim());
    }
    return Array.isArray(fieldValue) ? fieldValue as string[] : [];
  }

  private inferClearFlags(current: BookMetadata, original: BookMetadata): MetadataClearFlags {
    const flags: Record<string, boolean> = {};

    for (const field of ALL_METADATA_FIELDS) {
      const key = field.controlName as keyof BookMetadata;
      const curr = current[key];
      const orig = original[key];

      if (field.type === 'array') {
        flags[key] = !(curr as string[])?.length && !!(orig as string[])?.length;
      } else if (field.type === 'number') {
        flags[key] = curr === null && orig !== null;
      } else {
        flags[key] = !curr && !!orig;
      }
    }

    flags['cover'] = !current.thumbnailUrl && !!original.thumbnailUrl;
    return flags as MetadataClearFlags;
  }

  getThumbnail(): string | null {
    const thumbnailUrl = this.metadataForm.get('thumbnailUrl')?.value;
    if (thumbnailUrl?.includes('api/v1')) {
      return null;
    }
    if (this.copiedFields['thumbnailUrl']) {
      return (this.fetchedMetadata['thumbnailUrl' as keyof BookMetadata] as string) || null;
    }
    return null;
  }

  private updateMetadata(shouldLockAllFields: boolean | undefined): void {
    this.bookService.updateBookMetadata(this.currentBookId, this.buildMetadataWrapper(shouldLockAllFields), false).subscribe({
      next: () => {
        if (shouldLockAllFields !== undefined) {
          this.messageService.add({
            severity: 'success',
            summary: shouldLockAllFields ? 'Metadata Locked' : 'Metadata Unlocked',
            detail: shouldLockAllFields
              ? 'All fields have been successfully locked.'
              : 'All fields have been successfully unlocked.',
          });
        }
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update lock state',
        });
      }
    });
  }

  toggleLock(field: string): void {
    if (field === 'thumbnailUrl') {
      field = 'cover';
    }
    const isLocked = this.metadataForm.get(field + 'Locked')?.value;
    const updatedLockedState = !isLocked;
    this.metadataForm.get(field + 'Locked')?.setValue(updatedLockedState);
    if (updatedLockedState) {
      this.metadataForm.get(field)?.disable();
    } else {
      this.metadataForm.get(field)?.enable();
    }
    this.updateMetadata(undefined);
  }

  copyMissing(): void {
    this.metadataUtils.copyMissingFields(
      this.fetchedMetadata,
      this.metadataForm,
      this.copiedFields,
      (field) => this.copyFetchedToCurrent(field)
    );
  }

  copyAll(): void {
    this.metadataUtils.copyAllFields(
      this.fetchedMetadata,
      this.metadataForm,
      (field) => this.copyFetchedToCurrent(field)
    );
  }

  copyFetchedToCurrent(field: string): void {
    if (field === 'thumbnailUrl') {
      field = 'cover';
    }
    const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
    if (isLocked) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Action Blocked',
        detail: `${field} is locked and cannot be updated.`
      });
      return;
    }
    if (field === 'cover') {
      field = 'thumbnailUrl';
    }
    if (this.metadataUtils.copyFieldToForm(field, this.fetchedMetadata, this.metadataForm, this.copiedFields)) {
      this.highlightCopiedInput(field);
    }
  }

  lockAll(): void {
    this.formBuilder.setAllFieldsLocked(this.metadataForm, true);
    this.updateMetadata(true);
  }

  unlockAll(): void {
    this.formBuilder.setAllFieldsLocked(this.metadataForm, false);
    this.updateMetadata(false);
  }

  highlightCopiedInput(field: string): void {
    this.copiedFields = {...this.copiedFields, [field]: true};
  }

  isValueCopied(field: string): boolean {
    return this.copiedFields[field];
  }

  isValueSaved(field: string): boolean {
    return this.savedFields[field];
  }

  goBackClick(): void {
    this.goBack.emit(true);
  }

  onMouseEnter(controlName: string): void {
    if (this.isValueCopied(controlName) && !this.isValueSaved(controlName)) {
      this.hoveredFields[controlName] = true;
    }
  }

  onMouseLeave(controlName: string): void {
    this.hoveredFields[controlName] = false;
  }

  resetField(field: string): void {
    this.metadataUtils.resetField(field, this.metadataForm, this.originalMetadata, this.copiedFields, this.hoveredFields);
  }
}
