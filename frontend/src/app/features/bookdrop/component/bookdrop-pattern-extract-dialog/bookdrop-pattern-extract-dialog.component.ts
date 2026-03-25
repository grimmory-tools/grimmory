import {Component, ElementRef, inject, OnInit, ViewChild} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Divider} from 'primeng/divider';
import {Chip} from 'primeng/chip';
import {ProgressSpinner} from 'primeng/progressspinner';
import {BookdropService, PatternExtractResult} from '../../service/bookdrop.service';
import {MessageService} from 'primeng/api';
import {NgClass} from '@angular/common';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

interface PatternPlaceholder {
  name: string;
  descriptionKey: string;
  exampleKey: string;
}

interface PreviewResult {
  fileName: string;
  success: boolean;
  preview: Record<string, string>;
  errorMessage?: string;
}

const PLACEHOLDER_KEY_MAP: Record<string, string> = {
  '*': 'wildcard',
  'SeriesName': 'seriesName',
  'Title': 'title',
  'Subtitle': 'subtitle',
  'Authors': 'authors',
  'SeriesNumber': 'seriesNumber',
  'Published': 'published',
  'Publisher': 'publisher',
  'Language': 'language',
  'SeriesTotal': 'seriesTotal',
  'ISBN10': 'isbn10',
  'ISBN13': 'isbn13',
  'ASIN': 'asin',
};

@Component({
  selector: 'app-bookdrop-pattern-extract-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    Button,
    InputText,
    Divider,
    Chip,
    ProgressSpinner,
    NgClass,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe,
  ],
  templateUrl: './bookdrop-pattern-extract-dialog.component.html',
  styleUrl: './bookdrop-pattern-extract-dialog.component.scss'
})
export class BookdropPatternExtractDialogComponent implements OnInit {

  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly bookdropService = inject(BookdropService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  @ViewChild('patternInput', {static: false}) patternInput?: ElementRef<HTMLInputElement>;

  fileCount = 0;
  selectAll = false;
  excludedIds: number[] = [];
  selectedIds: number[] = [];

  isExtracting = false;
  previewResults: PreviewResult[] = [];

  spinnerStyle = {width: '24px', height: '24px'};

  patternForm = new FormGroup({
    pattern: new FormControl('', Validators.required),
  });

  availablePlaceholders: PatternPlaceholder[] = [
    {name: '*', descriptionKey: 'placeholders.wildcard', exampleKey: 'placeholderExamples.wildcard'},
    {name: 'SeriesName', descriptionKey: 'placeholders.seriesName', exampleKey: 'placeholderExamples.seriesName'},
    {name: 'Title', descriptionKey: 'placeholders.title', exampleKey: 'placeholderExamples.title'},
    {name: 'Subtitle', descriptionKey: 'placeholders.subtitle', exampleKey: 'placeholderExamples.subtitle'},
    {name: 'Authors', descriptionKey: 'placeholders.authors', exampleKey: 'placeholderExamples.authors'},
    {name: 'SeriesNumber', descriptionKey: 'placeholders.seriesNumber', exampleKey: 'placeholderExamples.seriesNumber'},
    {name: 'Published', descriptionKey: 'placeholders.published', exampleKey: 'placeholderExamples.published'},
    {name: 'Publisher', descriptionKey: 'placeholders.publisher', exampleKey: 'placeholderExamples.publisher'},
    {name: 'Language', descriptionKey: 'placeholders.language', exampleKey: 'placeholderExamples.language'},
    {name: 'SeriesTotal', descriptionKey: 'placeholders.seriesTotal', exampleKey: 'placeholderExamples.seriesTotal'},
    {name: 'ISBN10', descriptionKey: 'placeholders.isbn10', exampleKey: 'placeholderExamples.isbn10'},
    {name: 'ISBN13', descriptionKey: 'placeholders.isbn13', exampleKey: 'placeholderExamples.isbn13'},
    {name: 'ASIN', descriptionKey: 'placeholders.asin', exampleKey: 'placeholderExamples.asin'},
  ];

  commonPatterns = [
    {labelKey: 'commonPatternLabels.authorTitle', pattern: '{Authors} - {Title}'},
    {labelKey: 'commonPatternLabels.titleAuthor', pattern: '{Title} - {Authors}'},
    {labelKey: 'commonPatternLabels.titleYear', pattern: '{Title} ({Published:yyyy})'},
    {labelKey: 'commonPatternLabels.authorTitleYear', pattern: '{Authors} - {Title} ({Published:yyyy})'},
    {labelKey: 'commonPatternLabels.seriesNumber', pattern: '{SeriesName} #{SeriesNumber}'},
    {labelKey: 'commonPatternLabels.seriesChapter', pattern: '{SeriesName} - Chapter {SeriesNumber}'},
    {labelKey: 'commonPatternLabels.seriesVol', pattern: '{SeriesName} - Vol {SeriesNumber}'},
    {labelKey: 'commonPatternLabels.tagSeriesChapter', pattern: '[*] {SeriesName} - Chapter {SeriesNumber}'},
    {labelKey: 'commonPatternLabels.titleByAuthor', pattern: '{Title} by {Authors}'},
    {labelKey: 'commonPatternLabels.seriesVTotal', pattern: '{SeriesName} v{SeriesNumber} (of {SeriesTotal})'},
  ];

  ngOnInit(): void {
    this.fileCount = this.config.data?.fileCount ?? 0;
    this.selectAll = this.config.data?.selectAll ?? false;
    this.excludedIds = this.config.data?.excludedIds ?? [];
    this.selectedIds = this.config.data?.selectedIds ?? [];
  }

  insertPlaceholder(placeholderName: string): void {
    const patternControl = this.patternForm.get('pattern');
    const currentPattern = patternControl?.value ?? '';
    const inputElement = this.patternInput?.nativeElement;

    const textToInsert = placeholderName === '*' ? '*' : `{${placeholderName}}`;

    const patternToModify = placeholderName === '*'
      ? currentPattern
      : this.removeExistingPlaceholder(currentPattern, placeholderName);

    if (inputElement) {
      const cursorPosition = this.calculateCursorPosition(inputElement, currentPattern, patternToModify);
      const newPattern = this.insertTextAtCursor(patternToModify, textToInsert, cursorPosition);

      patternControl?.setValue(newPattern);
      this.focusInputAfterInsertion(inputElement, cursorPosition, textToInsert.length);
    } else {
      patternControl?.setValue(patternToModify + textToInsert);
    }

    this.previewPattern();
  }

  private removeExistingPlaceholder(pattern: string, placeholderName: string): string {
    const existingPlaceholderRegex = new RegExp(`\\{${placeholderName}(?::[^}]*)?\\}`, 'g');
    return pattern.replace(existingPlaceholderRegex, '');
  }

  private calculateCursorPosition(inputElement: HTMLInputElement, originalPattern: string, modifiedPattern: string): number {
    let cursorPosition = inputElement.selectionStart ?? modifiedPattern.length;

    if (originalPattern !== modifiedPattern) {
      const existingPlaceholderRegex = new RegExp(`\\{\\w+(?::[^}]*)?\\}`, 'g');
      const matchBefore = originalPattern.substring(0, cursorPosition).match(existingPlaceholderRegex);
      if (matchBefore) {
        cursorPosition -= matchBefore.reduce((sum, match) => sum + match.length, 0);
      }
      cursorPosition = Math.max(0, cursorPosition);
    }

    return cursorPosition;
  }

  private insertTextAtCursor(pattern: string, text: string, cursorPosition: number): string {
    const textBefore = pattern.substring(0, cursorPosition);
    const textAfter = pattern.substring(cursorPosition);
    return textBefore + text + textAfter;
  }

  private focusInputAfterInsertion(inputElement: HTMLInputElement, cursorPosition: number, insertedTextLength: number): void {
    setTimeout(() => {
      const newCursorPosition = cursorPosition + insertedTextLength;
      inputElement.setSelectionRange(newCursorPosition, newCursorPosition);
      inputElement.focus();
    }, 0);
  }

  applyCommonPattern(pattern: string): void {
    this.patternForm.get('pattern')?.setValue(pattern);
    this.previewPattern();
  }

  previewPattern(): void {
    const pattern = this.patternForm.get('pattern')?.value;
    if (!pattern) {
      this.previewResults = [];
      return;
    }

    const request = {
      pattern,
      selectAll: this.selectAll,
      excludedIds: this.excludedIds,
      selectedIds: this.selectedIds,
      preview: true
    };

    this.bookdropService.extractFromPattern(request).subscribe({
      next: (result) => {
        this.previewResults = result.results.map(r => ({
          fileName: r.fileName,
          success: r.success,
          preview: r.extractedMetadata || {},
          errorMessage: r.errorMessage
        }));
      },
      error: () => {
        this.previewResults = [];
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  extract(): void {
    const pattern = this.patternForm.get('pattern')?.value;
    if (!pattern) {
      return;
    }

    this.isExtracting = true;

    const payload = {
      pattern,
      selectAll: this.selectAll,
      excludedIds: this.excludedIds,
      selectedIds: this.selectedIds,
      preview: false,
    };

    this.bookdropService.extractFromPattern(payload).subscribe({
      next: (result: PatternExtractResult) => {
        this.isExtracting = false;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('bookdrop.patternExtract.toast.extractionCompleteSummary'),
          detail: this.t.translate('bookdrop.patternExtract.toast.extractionCompleteDetail', {
            success: result.successfullyExtracted,
            total: result.totalFiles
          }),
        });
        this.dialogRef.close(result);
      },
      error: (err) => {
        this.isExtracting = false;
        console.error('Pattern extraction failed:', err);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('bookdrop.patternExtract.toast.extractionFailedSummary'),
          detail: this.t.translate('bookdrop.patternExtract.toast.extractionFailedDetail'),
        });
      },
    });
  }

  get hasValidPattern(): boolean {
    const pattern: string = this.patternForm.get('pattern')?.value ?? '';
    if (!this.patternForm.valid || !pattern) {
      return false;
    }
    const placeholderRegex = /\{[a-zA-Z0-9_]+(?::[^{}]+)?\}|\*/;
    return placeholderRegex.test(pattern);
  }

  get patternControl(): FormControl {
    return this.patternForm.get('pattern') as FormControl;
  }

  getPlaceholderLabel(name: string): string {
    return name === '*' ? '*' : `{${name}}`;
  }

  getPlaceholderTooltip(placeholder: PatternPlaceholder): string {
    const description = this.t.translate(`bookdrop.patternExtract.${placeholder.descriptionKey}`);
    const example = this.t.translate(`bookdrop.patternExtract.${placeholder.exampleKey}`);
    return `${description} (e.g., ${example})`;
  }

  getCommonPatternLabel(labelKey: string): string {
    return this.t.translate(`bookdrop.patternExtract.${labelKey}`);
  }

  getPreviewClass(preview: PreviewResult): Record<string, boolean> {
    return {
      'preview-success': preview.success,
      'preview-failure': !preview.success
    };
  }

  getPreviewIconClass(preview: PreviewResult): string {
    return preview.success ? 'pi-check-circle' : 'pi-times-circle';
  }

  getPreviewEntries(preview: PreviewResult): {key: string; value: string}[] {
    return Object.entries(preview.preview).map(([key, value]) => ({key, value}));
  }

  getErrorMessage(preview: PreviewResult): string {
    return preview.errorMessage || this.t.translate('bookdrop.patternExtract.patternDidNotMatch');
  }

  getErrorTooltip(preview: PreviewResult): string {
    return preview.success ? '' : (preview.errorMessage || this.t.translate('bookdrop.patternExtract.patternDidNotMatchStructure'));
  }
}
