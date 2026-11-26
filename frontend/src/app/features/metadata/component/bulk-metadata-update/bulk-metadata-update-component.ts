import {Component, DestroyRef, inject, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';

import {InputText} from '@openng/optimus-ui/inputtext';
import {Button} from '@openng/optimus-ui/button';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {DatePicker} from '@openng/optimus-ui/datepicker';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {MessageService} from '@openng/optimus-ui/api';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {FacetSuggestionsDirective} from '../../../book/components/facet-suggestions.directive';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {BulkMetadataUpdateRequest} from '../../../book/model/book.model';
import {
  BOOK_AGE_RATING_LABEL_KEYS,
  BOOK_AGE_RATINGS,
  BOOK_CONTENT_RATING_LABEL_KEYS,
  BOOK_CONTENT_RATINGS,
} from '../../../book/data/book-values';
import {Checkbox} from '@openng/optimus-ui/checkbox';
import {AutoComplete} from '@openng/optimus-ui/autocomplete';
import {AutoCompleteSelectEvent} from '@openng/optimus-ui/autocomplete';
import {Select} from '@openng/optimus-ui/select';
import {filter, skip} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {BulkMetadataUpdateFailure, TaskProgressPayload, TaskService, TaskStatus, TaskType} from '../../../settings/task-management/task.service';
import {Subscription} from 'rxjs';

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
    AutoComplete,
    Select,
    TranslocoPipe,
    FacetSuggestionsDirective,
  ],
  providers: [MessageService],
  templateUrl: './bulk-metadata-update-component.html',
  styleUrl: './bulk-metadata-update-component.scss'
})
export class BulkMetadataUpdateComponent implements OnInit {
  private readonly config = inject(DynamicDialogConfig);
  readonly ref = inject(DynamicDialogRef);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly bookMetadataManageService = inject(BookMetadataManageService);
  private readonly taskService = inject(TaskService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  metadataForm!: FormGroup;
  bookIds: number[] = [];
  mergeCategories = true;
  mergeMoods = true;
  mergeTags = true;
  replaceAll = false;
  loading = false;
  taskProgress = 0;
  progressMessage = '';
  processedCount = 0;
  succeededCount = 0;
  failedCount = 0;
  failedBooks: BulkMetadataUpdateFailure[] = [];
  selectedCoverFile: File | null = null;
  coverUploadFailed = false;
  activeTaskId: string | null = null;
  private taskProgressSubscription: Subscription | null = null;
  private pendingTaskProgress: TaskProgressPayload[] = [];
  private completionHandled = false;
  private readonly maxDisplayedFailures = 200;

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
    ageRating: false,
    contentRating: false,
  };

  readonly ageRatingOptions = BOOK_AGE_RATINGS.map(value => ({
    label: this.t.translate(BOOK_AGE_RATING_LABEL_KEYS[value]),
    value,
  }));
  readonly contentRatingOptions = BOOK_CONTENT_RATINGS.map(value => ({
    label: this.t.translate(BOOK_CONTENT_RATING_LABEL_KEYS[value]),
    value,
  }));

  ngOnInit(): void {
    this.bookIds = this.config.data?.bookIds ?? [];

    this.metadataForm = this.fb.group({
      authors: [],
      publisher: [''],
      language: [''],
      seriesName: [''],
      seriesTotal: [''],
      publishedDate: [null],
      genres: [],
      moods: [],
      tags: [],
      ageRating: [null],
      contentRating: [null]
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

  get visibleFailedBooks(): BulkMetadataUpdateFailure[] {
    return this.failedBooks.slice(0, this.maxDisplayedFailures);
  }

  onAutoCompleteSelect(fieldName: string, event: AutoCompleteSelectEvent) {
    const values = (this.metadataForm.get(fieldName)?.value as string[]) || [];
    if (!values.includes(event.value as string)) {
      this.metadataForm.get(fieldName)?.setValue([...values, event.value as string]);
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

      ageRating: this.clearFields.ageRating ? null : (formValue.ageRating ?? undefined),
      clearAgeRating: this.clearFields.ageRating,

      contentRating: this.clearFields.contentRating ? null : (formValue.contentRating ?? undefined),
      clearContentRating: this.clearFields.contentRating,

      mergeCategories: this.mergeCategories,
      mergeMoods: this.mergeMoods,
      mergeTags: this.mergeTags,
      replaceAll: this.replaceAll
    };

    this.loading = true;
    this.taskProgress = 0;
    this.progressMessage = this.t.translate('bulkMetadata.progress.submitting');
    this.processedCount = 0;
    this.succeededCount = 0;
    this.failedCount = 0;
    this.failedBooks = [];
    this.coverUploadFailed = false;
    this.activeTaskId = null;
    this.pendingTaskProgress = [];
    this.completionHandled = false;

    this.taskProgressSubscription?.unsubscribe();
    this.taskProgressSubscription = this.taskService.taskProgress$
      .pipe(
        skip(1),
        filter((progress): progress is TaskProgressPayload =>
          progress !== null && progress.taskType === TaskType.BULK_UPDATE_BOOK_METADATA),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(progress => this.receiveTaskProgress(progress));

    this.bookMetadataManageService.updateBooksMetadata(payload).subscribe({
      next: response => {
        this.activeTaskId = response.taskId;
        this.progressMessage = this.t.translate('bulkMetadata.progress.queued');
        const earlyProgress = this.pendingTaskProgress;
        this.pendingTaskProgress = [];
        for (const progress of earlyProgress) {
          if (progress.taskId === this.activeTaskId) {
            this.applyTaskProgress(progress);
          }
        }
      },
      error: err => {
        this.taskProgressSubscription?.unsubscribe();
        this.taskProgressSubscription = null;
        console.error('Bulk metadata update failed:', err);
        this.loading = false;
        this.progressMessage = '';
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('bulkMetadata.toast.updateFailedSummary'),
          detail: this.t.translate('bulkMetadata.toast.updateFailedDetail')
        });
      }
    });
  }

  private receiveTaskProgress(progress: TaskProgressPayload): void {
    if (!this.activeTaskId) {
      this.pendingTaskProgress.push(progress);
      return;
    }
    if (progress.taskId === this.activeTaskId) {
      this.applyTaskProgress(progress);
    }
  }

  private applyTaskProgress(progress: TaskProgressPayload): void {
    if (this.completionHandled) return;

    this.taskProgress = progress.progress;
    this.progressMessage = progress.message;
    this.processedCount = progress.processedCount ?? this.processedCount;
    this.succeededCount = progress.succeededCount ?? this.succeededCount;
    this.failedCount = progress.failedCount ?? this.failedCount;

    for (const failure of progress.failedBooks ?? []) {
      this.failedBooks.push(failure);
    }

    if ([TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED].includes(progress.taskStatus)) {
      this.completionHandled = true;
      this.taskProgressSubscription?.unsubscribe();
      this.taskProgressSubscription = null;
      this.bookMetadataManageService.refreshAllBookCaches();
      if (progress.taskStatus === TaskStatus.COMPLETED && this.selectedCoverFile) {
        this.uploadSelectedCover();
      } else {
        this.finishUpdate(progress.taskStatus);
      }
    }
  }

  private uploadSelectedCover(): void {
    const coverFile = this.selectedCoverFile;
    if (!coverFile) return;

    this.progressMessage = this.t.translate('bulkMetadata.progress.uploadingCover');
    this.bookMetadataManageService.bulkUploadCover(this.bookIds, coverFile).subscribe({
      next: () => this.finishUpdate(TaskStatus.COMPLETED),
      error: error => {
        console.error('Bulk cover upload failed:', error);
        this.coverUploadFailed = true;
        this.messageService.add({
          severity: 'warn',
          summary: this.t.translate('bulkMetadata.toast.coverUploadFailedSummary'),
          detail: this.t.translate('bulkMetadata.toast.coverUploadFailedDetail')
        });
        this.finishUpdate(TaskStatus.COMPLETED);
      }
    });
  }

  private finishUpdate(status: TaskStatus): void {
    this.loading = false;
    if (status === TaskStatus.COMPLETED && this.failedCount === 0 && !this.coverUploadFailed) {
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate(this.selectedCoverFile ? 'bulkMetadata.toast.metadataAndCoverUpdated' : 'bulkMetadata.toast.metadataUpdated'),
        detail: this.selectedCoverFile
            ? this.t.translate('bulkMetadata.toast.metadataAndCoverUpdateDetail')
            : this.t.translate('bulkMetadata.toast.metadataUpdateDetail')
      });
      this.ref.close(true);
      return;
    }

    const detail = this.coverUploadFailed
      ? this.t.translate('bulkMetadata.toast.coverUploadFailedDetail')
      : status === TaskStatus.COMPLETED
        ? this.t.translate('bulkMetadata.toast.booksFailedDetail', {failed: this.failedCount, total: this.bookIds.length})
        : this.t.translate('bulkMetadata.toast.processingStoppedDetail', {processed: this.processedCount, total: this.bookIds.length});
    this.progressMessage = this.t.translate(status === TaskStatus.COMPLETED ? 'bulkMetadata.progress.completedWithErrors' : 'bulkMetadata.progress.incomplete');
    this.messageService.add({
      severity: status === TaskStatus.COMPLETED ? 'warn' : 'error',
      summary: this.t.translate(status === TaskStatus.COMPLETED ? 'bulkMetadata.toast.partialSuccess' : 'bulkMetadata.toast.updateIncomplete'),
      detail
    });
  }

  downloadFailedBooks(): void {
    if (this.failedBooks.length === 0) return;
    const csvRows = [
      [this.t.translate('bulkMetadata.csv.bookId'), this.t.translate('bulkMetadata.csv.title'), this.t.translate('bulkMetadata.csv.reason')].join(','),
      ...this.failedBooks.map(failure =>
        [failure.bookId, failure.title ?? '', failure.reason].map(value => this.toCsvCell(value)).join(','))
    ];
    const url = URL.createObjectURL(new Blob([csvRows.join('\r\n')], {type: 'text/csv;charset=utf-8'}));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'bulk-metadata-update-failures.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  private toCsvCell(value: string | number): string {
    let text = String(value).replace(/"/g, '""');
    if (/^[=+\-@]/.test(text)) {
      text = `'${text}`;
    }
    return `"${text}"`;
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
