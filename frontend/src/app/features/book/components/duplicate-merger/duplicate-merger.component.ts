import {Component, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {RadioButton} from 'primeng/radiobutton';
import {SelectButton} from 'primeng/selectbutton';
import {ProgressBar} from 'primeng/progressbar';
import {Tag} from 'primeng/tag';
import {Paginator, PaginatorState} from 'primeng/paginator';
import {Subject, takeUntil} from 'rxjs';
import {BookFileService} from '../../service/book-file.service';
import {BookService} from '../../service/book.service';
import {Book, DuplicateDetectionRequest, DuplicateGroup} from '../../model/book.model';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {CoverPlaceholderComponent} from '../../../../shared/components/cover-generator/cover-generator.component';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';

type PresetMode = 'strict' | 'balanced' | 'aggressive' | 'custom';

interface DisplayGroup extends DuplicateGroup {
  selectedTargetBookId: number;
  dismissed: boolean;
  selectedForDeletion: Set<number>;
}

@Component({
  selector: 'app-duplicate-merger',
  standalone: true,
  imports: [
    FormsModule,
    Button,
    Checkbox,
    RadioButton,
    SelectButton,
    ProgressBar,
    Tag,
    Paginator,
    TranslocoDirective,
    TranslocoPipe,
    CoverPlaceholderComponent,
  ],
  templateUrl: './duplicate-merger.component.html',
  styleUrls: ['./duplicate-merger.component.scss']
})
export class DuplicateMergerComponent implements OnInit, OnDestroy {
  libraryId!: number;
  presetMode: PresetMode = 'balanced';
  showAdvanced = false;

  matchByIsbn = true;
  matchByExternalId = true;
  matchByTitleAuthor = true;
  matchByDirectory = false;
  matchByFilename = false;

  isScanning = signal(false);
  isMerging = signal(false);
  hasScanned = signal(false);
  moveFiles = false;
  mergeProgress = signal(0);
  mergeTotal = signal(0);

  groups: DisplayGroup[] = [];
  presetOptions: { label: string; value: PresetMode }[] = [];

  pageFirst = 0;
  pageSize = 20;

  private destroy$ = new Subject<void>();
  private readonly bookFileService = inject(BookFileService);
  private readonly bookService = inject(BookService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly config = inject(DynamicDialogConfig);
  private readonly t = inject(TranslocoService);
  readonly urlHelper = inject(UrlHelperService);
  private readonly appSettingsService = inject(AppSettingsService);

  ngOnInit(): void {
    this.libraryId = this.config.data.libraryId;

    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.moveFiles = settings.metadataPersistenceSettings?.moveFilesToLibraryPattern ?? false;
    }

    this.presetOptions = [
      {label: this.t.translate('book.duplicateMerger.presetStrict'), value: 'strict'},
      {label: this.t.translate('book.duplicateMerger.presetBalanced'), value: 'balanced'},
      {label: this.t.translate('book.duplicateMerger.presetAggressive'), value: 'aggressive'},
      {label: this.t.translate('book.duplicateMerger.presetCustom'), value: 'custom'},
    ];
    this.applyPreset('balanced');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onPresetChange(): void {
    if (this.presetMode !== 'custom') {
      this.applyPreset(this.presetMode);
    }
  }

  onSignalToggle(): void {
    this.presetMode = 'custom';
  }

  applyPreset(mode: PresetMode): void {
    switch (mode) {
      case 'strict':
        this.matchByIsbn = true;
        this.matchByExternalId = true;
        this.matchByTitleAuthor = false;
        this.matchByDirectory = false;
        this.matchByFilename = false;
        break;
      case 'balanced':
        this.matchByIsbn = true;
        this.matchByExternalId = true;
        this.matchByTitleAuthor = true;
        this.matchByDirectory = false;
        this.matchByFilename = false;
        break;
      case 'aggressive':
        this.matchByIsbn = true;
        this.matchByExternalId = true;
        this.matchByTitleAuthor = true;
        this.matchByDirectory = true;
        this.matchByFilename = true;
        break;
    }
  }

  scan(): void {
    this.isScanning.set(true);
    this.hasScanned.set(false);
    this.groups = [];
    this.pageFirst = 0;

    const request: DuplicateDetectionRequest = {
      libraryId: this.libraryId,
      matchByIsbn: this.matchByIsbn,
      matchByExternalId: this.matchByExternalId,
      matchByTitleAuthor: this.matchByTitleAuthor,
      matchByDirectory: this.matchByDirectory,
      matchByFilename: this.matchByFilename,
    };

    this.bookFileService.findDuplicates(request).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (groups) => {
        this.groups = groups.map(g => ({
          ...g,
          selectedTargetBookId: g.suggestedTargetBookId,
          dismissed: false,
          selectedForDeletion: new Set<number>(),
        }));
        this.isScanning.set(false);
        this.hasScanned.set(true);
      },
      error: (err) => {
        this.isScanning.set(false);
        this.hasScanned.set(true);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.duplicateMerger.toast.scanFailedSummary'),
          detail: err?.error?.message || this.t.translate('book.duplicateMerger.toast.scanFailedDetail'),
        });
      }
    });
  }

  get activeGroups(): DisplayGroup[] {
    return this.groups.filter(g => !g.dismissed);
  }

  get pagedGroups(): DisplayGroup[] {
    return this.activeGroups.slice(this.pageFirst, this.pageFirst + this.pageSize);
  }

  get canScan(): boolean {
    return !this.isScanning() && !this.isMerging() &&
      (this.matchByIsbn || this.matchByExternalId || this.matchByTitleAuthor ||
        this.matchByDirectory || this.matchByFilename);
  }

  onPageChange(event: PaginatorState): void {
    this.pageFirst = event.first ?? 0;
    this.pageSize = event.rows ?? this.pageSize;
  }

  getBookFormats(book: Book): string[] {
    const formats: string[] = [];
    if (book.primaryFile?.bookType) {
      formats.push(book.primaryFile.bookType);
    }
    if (book.alternativeFormats) {
      for (const alt of book.alternativeFormats) {
        if (alt.bookType) {
          formats.push(alt.bookType);
        }
      }
    }
    return formats;
  }

  getFileCount(book: Book): number {
    let count = book.primaryFile ? 1 : 0;
    count += book.alternativeFormats?.length ?? 0;
    return count;
  }

  getMatchReasonLabel(reason: string): string {
    return this.t.translate(`book.duplicateMerger.reason.${reason}`);
  }

  hasSameFormatConflict(group: DisplayGroup): boolean {
    const formats = new Set<string>();
    for (const book of group.books) {
      if (book.primaryFile?.bookType) {
        if (formats.has(book.primaryFile.bookType)) return true;
        formats.add(book.primaryFile.bookType);
      }
    }
    return false;
  }

  getBookFilePath(book: Book): string {
    const subPath = book.primaryFile?.fileSubPath;
    const fileName = book.primaryFile?.fileName || '';
    if (subPath) return `${subPath}/${fileName}`;
    return fileName;
  }

  formatFileSize(sizeKb?: number): string {
    if (!sizeKb) return '';
    if (sizeKb < 1024) return `${sizeKb} KB`;
    const sizeMb = sizeKb / 1024;
    if (sizeMb < 1024) return `${sizeMb.toFixed(1)} MB`;
    return `${(sizeMb / 1024).toFixed(2)} GB`;
  }

  getMatchReasonSeverity(reason: string): "success" | "info" | "warn" | "danger" | "secondary" | "contrast" {
    switch (reason) {
      case 'ISBN':
      case 'EXTERNAL_ID':
        return 'success';
      case 'TITLE_AUTHOR':
        return 'info';
      case 'DIRECTORY':
        return 'warn';
      case 'FILENAME':
        return 'secondary';
      default:
        return 'info';
    }
  }

  onTargetChange(group: DisplayGroup): void {
    group.selectedForDeletion.delete(group.selectedTargetBookId);
  }

  toggleDeleteSelection(group: DisplayGroup, bookId: number): void {
    if (group.selectedForDeletion.has(bookId)) {
      group.selectedForDeletion.delete(bookId);
    } else {
      group.selectedForDeletion.add(bookId);
    }
  }

  getDeleteSelectedCount(group: DisplayGroup): number {
    return group.selectedForDeletion.size;
  }

  dismissGroup(group: DisplayGroup): void {
    group.dismissed = true;
    if (this.pagedGroups.length === 0 && this.pageFirst > 0) {
      this.pageFirst = Math.max(0, this.pageFirst - this.pageSize);
    }
  }

  async mergeGroup(group: DisplayGroup): Promise<void> {
    const targetId = group.selectedTargetBookId;
    const sourceIds = group.books
      .filter(b => b.id !== targetId)
      .map(b => b.id);

    if (sourceIds.length === 0) return;

    group.dismissed = true;
    try {
      await this.bookFileService.attachBookFiles(targetId, sourceIds, this.moveFiles)
        .pipe(takeUntil(this.destroy$))
        .toPromise();
    } catch {
      group.dismissed = false;
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('book.duplicateMerger.toast.mergeFailedSummary'),
        detail: this.t.translate('book.duplicateMerger.toast.mergeFailedDetail'),
      });
    }
  }

  async mergeAll(): Promise<void> {
    const toMerge = this.activeGroups;
    if (toMerge.length === 0) return;

    this.isMerging.set(true);
    this.mergeTotal.set(toMerge.length);
    this.mergeProgress.set(0);

    let successCount = 0;
    let failCount = 0;

    for (const group of toMerge) {
      const targetId = group.selectedTargetBookId;
      const sourceIds = group.books
        .filter(b => b.id !== targetId)
        .map(b => b.id);

      if (sourceIds.length === 0) {
        group.dismissed = true;
        this.mergeProgress.update((p) => p + 1);
        continue;
      }

      try {
        await this.bookFileService.attachBookFiles(targetId, sourceIds, this.moveFiles)
          .pipe(takeUntil(this.destroy$))
          .toPromise();
        group.dismissed = true;
        successCount++;
      } catch {
        failCount++;
      }
      this.mergeProgress.update((p) => p + 1);
    }

    this.isMerging.set(false);

    if (successCount > 0) {
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('book.duplicateMerger.toast.mergeSuccessSummary'),
        detail: this.t.translate('book.duplicateMerger.toast.mergeSuccessDetail', {count: successCount}),
      });
    }
    if (failCount > 0) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('book.duplicateMerger.toast.mergeFailedSummary'),
        detail: this.t.translate('book.duplicateMerger.toast.mergePartialDetail', {success: successCount, failed: failCount}),
      });
    }
  }

  deleteGroup(group: DisplayGroup): void {
    const idsToDelete = Array.from(group.selectedForDeletion);
    if (idsToDelete.length === 0) return;

    this.confirmationService.confirm({
      message: this.t.translate('book.duplicateMerger.confirm.deleteMessage', {count: idsToDelete.length}),
      header: this.t.translate('book.duplicateMerger.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.no'),
      acceptButtonProps: {severity: 'danger'},
      accept: () => {
        this.bookService.deleteBooks(new Set(idsToDelete)).pipe(
          takeUntil(this.destroy$)
        ).subscribe({
          next: () => {
            group.books = group.books.filter(b => !group.selectedForDeletion.has(b.id));
            group.selectedForDeletion.clear();
            if (group.books.length <= 1) {
              group.dismissed = true;
            }
          }
        });
      }
    });
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
