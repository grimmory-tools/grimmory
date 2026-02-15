import {Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {Book, BookMetadata, ComicMetadata, MetadataClearFlags, MetadataUpdateWrapper} from '../../../../book/model/book.model';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {AsyncPipe} from '@angular/common';
import {forkJoin, Observable} from 'rxjs';
import {Tooltip} from 'primeng/tooltip';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {BookService} from '../../../../book/service/book.service';
import {BookMetadataManageService} from '../../../../book/service/book-metadata-manage.service';
import {Textarea} from 'primeng/textarea';
import {filter, take} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AutoComplete, AutoCompleteSelectEvent} from 'primeng/autocomplete';
import {Image} from 'primeng/image';
import {Checkbox} from 'primeng/checkbox';
import {LazyLoadImageModule} from 'ng-lazyload-image';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataProviderSpecificFields} from '../../../../../shared/model/app-settings.model';
import {ALL_COMIC_METADATA_FIELDS, ALL_METADATA_FIELDS, AUDIOBOOK_METADATA_FIELDS, COMIC_ARRAY_METADATA_FIELDS, COMIC_FORM_TO_MODEL_LOCK, COMIC_TEXT_METADATA_FIELDS, COMIC_TEXTAREA_METADATA_FIELDS, getArrayFields, getBookDetailsFields, getBottomFields, getProviderFields, getSeriesFields, getTextareaFields, getTopFields, MetadataFieldConfig, MetadataFormBuilder, MetadataUtilsService} from '../../../../../shared/metadata';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-picker',
  standalone: true,
  templateUrl: './metadata-picker.component.html',
  styleUrls: ['./metadata-picker.component.scss'],
  imports: [
    Button,
    FormsModule,
    InputText,
    ReactiveFormsModule,
    Tooltip,
    AsyncPipe,
    Textarea,
    AutoComplete,
    Image,
    LazyLoadImageModule,
    Checkbox,
    TranslocoDirective
  ]
})
export class MetadataPickerComponent implements OnInit {

  // Cached arrays for template binding (avoid getter re-computation)
  metadataFieldsTop: MetadataFieldConfig[] = [];
  metadataChips: MetadataFieldConfig[] = [];
  metadataDescription: MetadataFieldConfig[] = [];
  metadataSeriesFields: MetadataFieldConfig[] = [];
  metadataBookDetailsFields: MetadataFieldConfig[] = [];
  metadataProviderFields: MetadataFieldConfig[] = [];
  metadataFieldsBottom: MetadataFieldConfig[] = [];
  audiobookMetadataFields: MetadataFieldConfig[] = [];
  comicTextFields: MetadataFieldConfig[] = [];
  comicArrayFields: MetadataFieldConfig[] = [];
  comicTextareaFields: MetadataFieldConfig[] = [];
  comicSectionExpanded = false;

  @Input() reviewMode!: boolean;
  @Input() fetchedMetadata!: BookMetadata;
  @Input() book$!: Observable<Book | null>;
  @Input() detailLoading = false;
  @Output() goBack = new EventEmitter<boolean>();

  currentBook: Book | null = null;

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
  private bookMetadataManageService = inject(BookMetadataManageService);
  protected urlHelper = inject(UrlHelperService);
  private destroyRef = inject(DestroyRef);
  private appSettingsService = inject(AppSettingsService);
  private formBuilder = inject(MetadataFormBuilder);
  private metadataUtils = inject(MetadataUtilsService);
  private readonly t = inject(TranslocoService);

  private enabledProviderFields: MetadataProviderSpecificFields | null = null;

  constructor() {
    this.metadataForm = this.formBuilder.buildForm(true);
    this.initFieldArrays();
  }

  private initFieldArrays(): void {
    this.metadataFieldsTop = getTopFields();
    this.metadataChips = getArrayFields();
    this.metadataDescription = getTextareaFields();
    this.metadataSeriesFields = getSeriesFields();
    this.metadataBookDetailsFields = getBookDetailsFields();
    this.updateProviderFields();
    this.updateBottomFields();
    this.audiobookMetadataFields = AUDIOBOOK_METADATA_FIELDS;
    this.comicTextFields = COMIC_TEXT_METADATA_FIELDS;
    this.comicArrayFields = COMIC_ARRAY_METADATA_FIELDS;
    this.comicTextareaFields = COMIC_TEXTAREA_METADATA_FIELDS;
  }

  private updateProviderFields(): void {
    this.metadataProviderFields = getProviderFields(this.enabledProviderFields);
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
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(settings => {
        if (settings?.metadataProviderSpecificFields) {
          this.enabledProviderFields = settings.metadataProviderSpecificFields;
          this.updateProviderFields();
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
        takeUntilDestroyed(this.destroyRef)
      ).subscribe((book) => {
      if (this.reviewMode) {
        this.metadataForm.reset();
        this.copiedFields = {};
        this.savedFields = {};
        this.hoveredFields = {};
      }

      this.currentBook = book;
      const metadata = book.metadata!;
      this.originalMetadata = metadata;
      this.originalMetadata.thumbnailUrl = this.urlHelper.getThumbnailUrl(metadata.bookId, metadata.coverUpdatedOn);
      this.currentBookId = metadata.bookId;
      this.patchMetadataToForm(metadata, book);
    });
  }

  private patchMetadataToForm(metadata: BookMetadata, book: Book): void {
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

    // Handle special fields - ebook cover
    patchData['thumbnailUrl'] = this.urlHelper.getCoverUrl(metadata.bookId, metadata.coverUpdatedOn);
    patchData['coverLocked'] = metadata.coverLocked ?? false;

    // Handle audiobook cover
    patchData['audiobookThumbnailUrl'] = this.urlHelper.getAudiobookCoverUrl(metadata.bookId, metadata.audiobookCoverUpdatedOn);
    patchData['audiobookCoverLocked'] = metadata.audiobookCoverLocked ?? false;

    // Handle audiobook-specific metadata fields
    // narrator/abridged are now at top-level of BookMetadata, but we also check nested for backward compatibility
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      const key = field.controlName as keyof BookMetadata;
      const lockedKey = field.lockedKey as keyof BookMetadata;
      // Prefer top-level fields, fallback to nested audiobookMetadata
      const topLevelValue = metadata[key];
      const topLevelLocked = metadata[lockedKey];
      patchData[field.controlName] = topLevelValue ?? (field.type === 'boolean' ? null : '');
      patchData[field.lockedKey] = topLevelLocked ?? false;
    }

    // Handle comic book metadata fields (nested under comicMetadata)
    const comicMeta = metadata.comicMetadata;
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      const value = comicMeta?.[field.fetchedKey as keyof ComicMetadata];
      if (field.type === 'array') {
        patchData[field.controlName] = [...(value as string[] ?? [])].sort();
      } else if (field.type === 'boolean') {
        patchData[field.controlName] = value ?? null;
      } else if (field.type === 'number') {
        patchData[field.controlName] = value ?? null;
      } else {
        patchData[field.controlName] = value ?? '';
      }
      const modelLockKey = COMIC_FORM_TO_MODEL_LOCK[field.lockedKey];
      patchData[field.lockedKey] = comicMeta?.[modelLockKey as keyof ComicMetadata] ?? false;
    }

    this.metadataForm.patchValue(patchData);
    this.applyLockStates(metadata);
    this.comicSectionExpanded = this.hasAnyFetchedComicData() || this.hasAnyCurrentComicData();
  }

  private applyLockStates(metadata: BookMetadata): void {
    const lockedFields: Record<string, boolean> = {};
    for (const field of ALL_METADATA_FIELDS) {
      lockedFields[field.lockedKey] = !!metadata[field.lockedKey as keyof BookMetadata];
    }
    // Also handle audiobook metadata lock states (now at top-level of BookMetadata)
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      lockedFields[field.lockedKey] = !!metadata[field.lockedKey as keyof BookMetadata];
    }
    // Handle comic book metadata lock states (nested under comicMetadata)
    const comicMeta = metadata.comicMetadata;
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      const modelLockKey = COMIC_FORM_TO_MODEL_LOCK[field.lockedKey];
      lockedFields[field.lockedKey] = !!comicMeta?.[modelLockKey as keyof ComicMetadata];
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

    const requests: Observable<unknown>[] = [
      this.bookMetadataManageService.updateBookMetadata(this.currentBookId, updatedBookMetadata, false)
    ];

    // Handle audiobook cover upload when fetched from Audible provider
    if (this.isAudibleProvider() && this.copiedFields['audiobookThumbnailUrl']) {
      const audiobookCoverUrl = this.fetchedMetadata.thumbnailUrl;
      if (audiobookCoverUrl) {
        requests.push(this.bookMetadataManageService.uploadAudiobookCoverFromUrl(this.currentBookId, audiobookCoverUrl));
      }
    }

    forkJoin(requests).subscribe({
      next: () => {
        this.isSaving = false;
        for (const field of Object.keys(this.copiedFields)) {
          if (this.copiedFields[field]) {
            this.savedFields[field] = true;
          }
        }
        this.messageService.add({severity: 'info', summary: this.t.translate('metadata.picker.toast.successSummary'), detail: this.t.translate('metadata.picker.toast.metadataUpdated')});
      },
      error: () => {
        this.isSaving = false;
        this.messageService.add({severity: 'error', summary: this.t.translate('metadata.picker.toast.errorSummary'), detail: this.t.translate('metadata.picker.toast.metadataUpdateFailed')});
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
      } else if (field.type === 'boolean') {
        metadata[field.controlName] = this.getBooleanValue(field.controlName);
      } else {
        metadata[field.controlName] = this.getStringValue(field.controlName);
      }

      metadata[field.lockedKey] = this.metadataForm.get(field.lockedKey)?.value ?? false;
    }

    metadata['thumbnailUrl'] = this.getThumbnail();
    metadata['coverLocked'] = this.metadataForm.get('coverLocked')?.value;
    metadata['audiobookCoverLocked'] = this.metadataForm.get('audiobookCoverLocked')?.value;

    // Set audiobook content metadata (narrator/abridged) at top level
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      if (field.type === 'boolean') {
        metadata[field.controlName] = this.getBooleanValue(field.controlName);
      } else {
        metadata[field.controlName] = this.getStringValue(field.controlName);
      }
      metadata[field.lockedKey] = this.metadataForm.get(field.lockedKey)?.value ?? false;
    }

    // Build comic metadata from form controls
    const comicMetadata: Record<string, unknown> = {};
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      if (field.type === 'array') {
        comicMetadata[field.fetchedKey] = this.getArrayValue(field.controlName);
      } else if (field.type === 'number') {
        comicMetadata[field.fetchedKey] = this.getNumberValue(field.controlName);
      } else if (field.type === 'boolean') {
        comicMetadata[field.fetchedKey] = this.getBooleanValue(field.controlName);
      } else {
        comicMetadata[field.fetchedKey] = this.getStringValue(field.controlName);
      }
    }
    // Consolidate lock states back to model lock keys using the form-to-model mapping.
    // If ANY field in a group is locked, the backend group lock is set to true.
    const lockGroups: Record<string, boolean> = {};
    for (const [formKey, modelKey] of Object.entries(COMIC_FORM_TO_MODEL_LOCK)) {
      const value = this.metadataForm.get(formKey)?.value ?? false;
      if (value) {
        lockGroups[modelKey] = true;
      } else if (!(modelKey in lockGroups)) {
        lockGroups[modelKey] = false;
      }
    }
    for (const [modelKey, value] of Object.entries(lockGroups)) {
      comicMetadata[modelKey] = value;
    }
    metadata['comicMetadata'] = comicMetadata;

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

  private getBooleanValue(field: string): boolean | null {
    const formValue = this.metadataForm.get(field)?.value;
    if (formValue === null || formValue === undefined) {
      if (this.copiedFields[field]) {
        return (this.fetchedMetadata[field as keyof BookMetadata] as boolean | null) ?? null;
      }
      return null;
    }
    return !!formValue;
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
      } else if (field.type === 'boolean') {
        flags[key] = curr === null && orig !== null;
      } else {
        flags[key] = !curr && !!orig;
      }
    }

    flags['cover'] = !current.thumbnailUrl && !!original.thumbnailUrl;

    // Handle audiobook metadata clear flags (now at top-level of BookMetadata)
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      const key = field.controlName as keyof BookMetadata;
      const curr = current[key];
      const orig = original[key];

      if (field.type === 'boolean') {
        flags[key] = curr === null && orig !== null;
      } else {
        flags[key] = !curr && !!orig;
      }
    }

    // Handle comic metadata clear flags
    const currComic = current.comicMetadata;
    const origComic = original.comicMetadata;
    if (origComic) {
      for (const field of ALL_COMIC_METADATA_FIELDS) {
        const key = field.fetchedKey as keyof ComicMetadata;
        const curr = currComic?.[key];
        const orig = origComic[key];

        if (field.type === 'array') {
          flags[`comic_${key}`] = !(curr as string[])?.length && !!(orig as string[])?.length;
        } else if (field.type === 'boolean') {
          flags[`comic_${key}`] = curr === null && orig !== null;
        } else if (field.type === 'number') {
          flags[`comic_${key}`] = curr === null && orig !== null;
        } else {
          flags[`comic_${key}`] = !curr && !!orig;
        }
      }
    }

    return flags as MetadataClearFlags;
  }

  getThumbnail(): string | null {
    // For Audible provider, cover is handled separately via uploadAudiobookCoverFromUrl
    if (this.isAudibleProvider()) {
      return null;
    }
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
    this.bookMetadataManageService.updateBookMetadata(this.currentBookId, this.buildMetadataWrapper(shouldLockAllFields), false).subscribe({
      next: () => {
        if (shouldLockAllFields !== undefined) {
          this.messageService.add({
            severity: 'success',
            summary: shouldLockAllFields ? this.t.translate('metadata.picker.toast.metadataLocked') : this.t.translate('metadata.picker.toast.metadataUnlocked'),
            detail: shouldLockAllFields
              ? this.t.translate('metadata.picker.toast.allFieldsLocked')
              : this.t.translate('metadata.picker.toast.allFieldsUnlocked'),
          });
        }
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.picker.toast.errorSummary'),
          detail: this.t.translate('metadata.picker.toast.lockStateFailed'),
        });
      }
    });
  }

  toggleLock(field: string): void {
    const controlName = field;
    let lockField = field;
    if (field === 'thumbnailUrl') {
      lockField = 'cover';
    } else if (field === 'audiobookThumbnailUrl') {
      lockField = 'audiobookCover';
    }
    const isLocked = this.metadataForm.get(lockField + 'Locked')?.value;
    const updatedLockedState = !isLocked;
    this.metadataForm.get(lockField + 'Locked')?.setValue(updatedLockedState);
    if (updatedLockedState) {
      this.metadataForm.get(controlName)?.disable();
    } else {
      this.metadataForm.get(controlName)?.enable();
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
    // Also copy missing comic fields
    if (this.fetchedMetadata?.comicMetadata) {
      for (const field of ALL_COMIC_METADATA_FIELDS) {
        const isLocked = this.metadataForm.get(field.lockedKey)?.value;
        const currentValue = this.metadataForm.get(field.controlName)?.value;
        const fetchedValue = this.fetchedMetadata.comicMetadata[field.fetchedKey as keyof ComicMetadata];
        const isEmpty = Array.isArray(currentValue)
          ? currentValue.length === 0
          : (currentValue === null || currentValue === undefined || currentValue === '');
        const hasFetchedValue = fetchedValue !== null && fetchedValue !== undefined && fetchedValue !== '';
        if (!isLocked && isEmpty && hasFetchedValue) {
          this.copyFetchedToCurrent(field.controlName);
        }
      }
    }
  }

  copyAll(): void {
    this.metadataUtils.copyAllFields(
      this.fetchedMetadata,
      this.metadataForm,
      (field) => this.copyFetchedToCurrent(field)
    );
    // Also copy all comic fields
    if (this.fetchedMetadata?.comicMetadata) {
      for (const field of ALL_COMIC_METADATA_FIELDS) {
        const isLocked = this.metadataForm.get(field.lockedKey)?.value;
        const fetchedValue = this.fetchedMetadata.comicMetadata[field.fetchedKey as keyof ComicMetadata];
        if (!isLocked && fetchedValue != null && fetchedValue !== '') {
          this.copyFetchedToCurrent(field.controlName);
        }
      }
    }
  }

  copyFetchedToCurrent(field: string): void {
    // Handle comic fields (nested under comicMetadata)
    const comicConfig = this.getComicFieldConfig(field);
    if (comicConfig) {
      const isLocked = this.metadataForm.get(comicConfig.lockedKey)?.value;
      if (isLocked) {
        this.messageService.add({
          severity: 'warn',
          summary: this.t.translate('metadata.picker.toast.actionBlockedSummary'),
          detail: this.t.translate('metadata.picker.toast.fieldLockedDetail', {field: comicConfig.label})
        });
        return;
      }
      const value = this.fetchedMetadata?.comicMetadata?.[comicConfig.fetchedKey as keyof ComicMetadata];
      if (value !== null && value !== undefined && value !== '') {
        this.metadataForm.get(field)?.setValue(value);
        this.copiedFields[field] = true;
        this.highlightCopiedInput(field);
      }
      return;
    }

    let lockField = field;
    if (field === 'thumbnailUrl') {
      lockField = 'cover';
    } else if (field === 'audiobookThumbnailUrl') {
      lockField = 'audiobookCover';
    }
    const isLocked = this.metadataForm.get(`${lockField}Locked`)?.value;
    if (isLocked) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.picker.toast.actionBlockedSummary'),
        detail: this.t.translate('metadata.picker.toast.fieldLockedDetail', {field: lockField})
      });
      return;
    }
    // For audiobook cover from Audible, use the thumbnailUrl from fetched metadata
    if (field === 'audiobookThumbnailUrl') {
      this.metadataForm.get('audiobookThumbnailUrl')?.setValue(this.fetchedMetadata.thumbnailUrl);
      this.highlightCopiedInput(field);
      return;
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
    const comicConfig = this.getComicFieldConfig(field);
    if (comicConfig) {
      const value = this.originalMetadata?.comicMetadata?.[comicConfig.fetchedKey as keyof ComicMetadata];
      this.metadataForm.get(field)?.setValue(value ?? (comicConfig.type === 'array' ? [] : comicConfig.type === 'boolean' ? null : ''));
      this.copiedFields[field] = false;
      this.hoveredFields[field] = false;
      return;
    }
    this.metadataUtils.resetField(field, this.metadataForm, this.originalMetadata, this.copiedFields, this.hoveredFields);
  }

  isAudibleProvider(): boolean {
    return this.fetchedMetadata?.provider?.toLowerCase() === 'audible';
  }

  getFetchedAudiobookValue(key: string): unknown {
    return this.fetchedMetadata?.[key as keyof BookMetadata];
  }

  // Cover type detection methods
  hasEbookFormat(book: Book): boolean {
    if (book.isPhysical) {
      return true;
    }
    const allFiles = [book.primaryFile, ...(book.alternativeFormats || [])].filter(f => f?.bookType);
    return allFiles.some(f => f!.bookType !== 'AUDIOBOOK');
  }

  hasAudiobookFormat(book: Book): boolean {
    const allFiles = [book.primaryFile, ...(book.alternativeFormats || [])].filter(f => f?.bookType);
    return allFiles.some(f => f!.bookType === 'AUDIOBOOK');
  }

  supportsDualCovers(book: Book): boolean {
    return this.hasEbookFormat(book) && this.hasAudiobookFormat(book);
  }

  // Comic metadata helpers
  hasAnyFetchedComicData(): boolean {
    const comic = this.fetchedMetadata?.comicMetadata;
    if (!comic) return false;
    return ALL_COMIC_METADATA_FIELDS.some(field => {
      const value = comic[field.fetchedKey as keyof ComicMetadata];
      if (value === null || value === undefined || value === '' || value === false) return false;
      if (Array.isArray(value) && value.length === 0) return false;
      return true;
    });
  }

  hasAnyCurrentComicData(): boolean {
    const comic = this.currentBook?.metadata?.comicMetadata;
    if (!comic) return false;
    return ALL_COMIC_METADATA_FIELDS.some(field => {
      const value = comic[field.fetchedKey as keyof ComicMetadata];
      if (value === null || value === undefined || value === '' || value === false) return false;
      if (Array.isArray(value) && value.length === 0) return false;
      return true;
    });
  }

  shouldShowComicSection(): boolean {
    return this.hasAnyFetchedComicData() || this.hasAnyCurrentComicData() ||
      this.currentBook?.primaryFile?.bookType === 'CBX' ||
      this.fetchedMetadata?.provider?.toLowerCase() === 'comicvine';
  }

  getFetchedComicValue(fetchedKey: string): unknown {
    return this.fetchedMetadata?.comicMetadata?.[fetchedKey as keyof ComicMetadata];
  }

  private getComicFieldConfig(controlName: string): MetadataFieldConfig | undefined {
    return ALL_COMIC_METADATA_FIELDS.find(f => f.controlName === controlName);
  }
}
