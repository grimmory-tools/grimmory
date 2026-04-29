import {Component, effect, EventEmitter, inject, Input, Output} from '@angular/core';
import {FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {NgClass} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {InputText} from 'primeng/inputtext';
import {BookMetadata} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {Textarea} from 'primeng/textarea';
import {AutoComplete} from 'primeng/autocomplete';
import {Image} from 'primeng/image';
import {LazyLoadImageModule} from 'ng-lazyload-image';
import {ConfirmationService, MessageService} from 'primeng/api';
import {CdkDragDrop, CdkDropList, CdkDrag, moveItemInArray} from '@angular/cdk/drag-drop';
import {AutoCompleteSelectEvent} from 'primeng/autocomplete';
import {DatePicker} from 'primeng/datepicker';
import {ALL_METADATA_FIELDS, getArrayFields, getBottomFields, getTextareaFields, MetadataFieldConfig} from '../../../../shared/metadata';
import {MetadataUtilsService} from '../../../../shared/metadata';
import {MetadataProviderSpecificFields} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {BookdropService} from '../../service/bookdrop.service';
import {finalize} from 'rxjs';

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
    LazyLoadImageModule,
    DatePicker,
    TranslocoDirective,
    CdkDropList,
    CdkDrag,
  ],
  templateUrl: './bookdrop-file-metadata-picker.component.html',
  styleUrl: './bookdrop-file-metadata-picker.component.scss'
})
export class BookdropFileMetadataPickerComponent {

  private readonly confirmationService = inject(ConfirmationService);
  private readonly metadataUtils = inject(MetadataUtilsService);
  protected readonly urlHelper = inject(UrlHelperService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);
  private readonly bookdropService = inject(BookdropService);
  private readonly messageService = inject(MessageService);

  @Input() fetchedMetadata!: BookMetadata;
  @Input() originalMetadata?: BookMetadata;
  @Input() metadataForm!: FormGroup;
  @Input() copiedFields: Record<string, boolean> = {};
  @Input() savedFields: Record<string, boolean> = {};
  @Input() bookdropFileId!: number;

  @Output() metadataCopied = new EventEmitter<boolean>();
  @Output() metadataRefetched = new EventEmitter<BookMetadata>();

  refetching = false;

  authorInputValue = '';

  private enabledProviderFields: MetadataProviderSpecificFields | null = null;

  metadataFieldsTop: MetadataFieldConfig[] = ALL_METADATA_FIELDS.filter(f =>
    ['title', 'subtitle', 'publisher'].includes(f.controlName)
  );

  metadataPublishDate: MetadataFieldConfig[] = ALL_METADATA_FIELDS.filter(f =>
    f.controlName === 'publishedDate'
  );

  metadataChips: MetadataFieldConfig[] = getArrayFields();

  metadataDescription: MetadataFieldConfig[] = getTextareaFields();

  metadataFieldsBottom: MetadataFieldConfig[] = getBottomFields();

  private readonly syncProviderFieldsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (settings?.metadataProviderSpecificFields) {
      this.enabledProviderFields = settings.metadataProviderSpecificFields;
      this.metadataFieldsBottom = getBottomFields(this.enabledProviderFields);
    }
  });

  copyMissing(): void {
    this.metadataUtils.copyMissingFields(
      this.fetchedMetadata,
      this.metadataForm,
      this.copiedFields,
      (field) => this.copyFetchedToCurrent(field)
    );
  }

  copyAll(includeCover: boolean = true): void {
    const excludeFields = includeCover ? ['bookId'] : ['thumbnailUrl', 'bookId'];
    this.metadataUtils.copyAllFields(
      this.fetchedMetadata,
      this.metadataForm,
      (field) => this.copyFetchedToCurrent(field),
      excludeFields
    );
  }

  copyFetchedToCurrent(field: string): void {
    if (this.metadataUtils.copyFieldToForm(field, this.fetchedMetadata, this.metadataForm, this.copiedFields)) {
      this.metadataCopied.emit(true);
    }
  }

  isValueChanged(field: string): boolean {
    return this.metadataUtils.isValueChanged(field, this.metadataForm, this.originalMetadata);
  }

  isFetchedDifferent(field: string): boolean {
    return this.metadataUtils.isFetchedDifferent(field, this.metadataForm, this.fetchedMetadata);
  }

  isValueCopied(field: string): boolean {
    return this.copiedFields[field];
  }

  isValueSaved(field: string): boolean {
    return this.savedFields[field];
  }

  resetField(field: string): void {
    this.metadataUtils.resetField(field, this.metadataForm, this.originalMetadata, this.copiedFields);
    if (field === 'thumbnailUrl') {
      this.metadataForm.get('thumbnailUrl')?.setValue(this.urlHelper.getBookdropCoverUrl(this.bookdropFileId));
    }
  }

  dropAuthor(event: CdkDragDrop<string[]>) {
    const authors = [...(this.metadataForm.get('authors')?.value ?? [])];
    moveItemInArray(authors, event.previousIndex, event.currentIndex);
    this.metadataForm.get('authors')?.setValue(authors);
    this.metadataForm.get('authors')?.markAsDirty();
  }

  removeAuthor(index: number) {
    const authors = [...(this.metadataForm.get('authors')?.value ?? [])];
    authors.splice(index, 1);
    this.metadataForm.get('authors')?.setValue(authors);
    this.metadataForm.get('authors')?.markAsDirty();
  }

  onAuthorInputKeyUp(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      const value = this.authorInputValue?.trim();
      if (value) {
        const authors = this.metadataForm.get('authors')?.value || [];
        if (!authors.includes(value)) {
          this.metadataForm.get('authors')?.setValue([...authors, value]);
          this.metadataForm.get('authors')?.markAsDirty();
        }
        this.authorInputValue = '';
      }
    }
  }

  onAuthorInputSelect(event: AutoCompleteSelectEvent) {
    const authors = (this.metadataForm.get('authors')?.value as string[]) || [];
    const value = event.value as string;
    if (!authors.includes(value)) {
      this.metadataForm.get('authors')?.setValue([...authors, value]);
      this.metadataForm.get('authors')?.markAsDirty();
    }
    setTimeout(() => this.authorInputValue = '');
  }

  onAutoCompleteBlur(fieldName: string, event: Event): void {
    const target = event.target as HTMLInputElement;
    const inputValue = target?.value?.trim();
    if (inputValue) {
      const currentValue = this.metadataForm.get(fieldName)?.value || [];
      const values = Array.isArray(currentValue) ? currentValue :
        typeof currentValue === 'string' && currentValue ? currentValue.split(',').map((v: string) => v.trim()) : [];
      if (!values.includes(inputValue)) {
        values.push(inputValue);
        this.metadataForm.get(fieldName)?.setValue(values);
      }
      if (target) {
        target.value = '';
      }
    }
  }

  confirmReset(): void {
    this.confirmationService.confirm({
      message: this.t.translate('bookdrop.metadataPicker.confirmResetMessage'),
      header: this.t.translate('bookdrop.metadataPicker.confirmResetHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.resetAll()
    });
  }

  resetAll(): void {
    if (this.originalMetadata) {
      const patchData: Record<string, unknown> = {};

      for (const field of ALL_METADATA_FIELDS) {
        const key = field.controlName as keyof BookMetadata;
        const value = this.originalMetadata[key];

        if (field.type === 'array') {
          patchData[field.controlName] = [...(value as string[] ?? [])].sort();
        } else {
          patchData[field.controlName] = value ?? null;
        }
      }

      patchData['thumbnailUrl'] = this.urlHelper.getBookdropCoverUrl(this.bookdropFileId);

      this.metadataForm.patchValue(patchData);
    }
    this.copiedFields = {};
    this.metadataCopied.emit(false);
  }

  refetchOnline(): void {
    this.refetching = true;
    const manualMetadata: BookMetadata = this.metadataForm.value;
    
    this.bookdropService.refetchMetadata(this.bookdropFileId, manualMetadata)
      .pipe(finalize(() => this.refetching = false))
      .subscribe({
        next: (updatedFile) => {
          if (updatedFile.fetchedMetadata && updatedFile.fetchedMetadata.title) {
            this.fetchedMetadata = updatedFile.fetchedMetadata;
            this.metadataRefetched.emit(updatedFile.fetchedMetadata);
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('bookdrop.metadataPicker.refetchSuccessSummary'),
              detail: this.t.translate('bookdrop.metadataPicker.refetchSuccessDetail')
            });
          } else {
            this.messageService.add({
              severity: 'warn',
              summary: this.t.translate('bookdrop.metadataPicker.refetchNoResultsSummary'),
              detail: this.t.translate('bookdrop.metadataPicker.refetchNoResultsDetail')
            });
          }
        },
        error: (err) => {
          console.error('Error refetching metadata:', err);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('bookdrop.metadataPicker.refetchFailedSummary'),
            detail: this.t.translate('bookdrop.metadataPicker.refetchFailedDetail')
          });
        }
      });
  }
}
