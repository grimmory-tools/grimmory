import {Component, DestroyRef, inject, OnInit, QueryList, ViewChildren} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter, startWith, take, tap} from 'rxjs/operators';
import {PageTitleService} from "../../../../shared/service/page-title.service";

import {BookdropFile, BookdropFinalizePayload, BookdropFinalizeResult, BookdropService, FileExtractionResult, BulkEditRequest as BackendBulkEditRequest, BulkEditResult as BackendBulkEditResult} from '../../service/bookdrop.service';
import {LibraryService} from '../../../book/service/library.service';
import {Library} from '../../../book/model/library.model';

import {ProgressSpinner} from 'primeng/progressspinner';
import {FormControl, FormGroup, FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {Tooltip} from 'primeng/tooltip';
import {Divider} from 'primeng/divider';
import {ConfirmationService, MessageService} from 'primeng/api';
import {Observable, Subscription} from 'rxjs';
import {InputGroup} from 'primeng/inputgroup';
import {InputGroupAddonModule} from 'primeng/inputgroupaddon';
import {AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {BookMetadata} from '../../../book/model/book.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {Checkbox} from 'primeng/checkbox';
import {NgClass} from '@angular/common';
import {Paginator} from 'primeng/paginator';
import {ActivatedRoute} from '@angular/router';
import {BookdropFileMetadataPickerComponent} from '../bookdrop-file-metadata-picker/bookdrop-file-metadata-picker.component';
import {BookdropBulkEditDialogComponent, BulkEditResult} from '../bookdrop-bulk-edit-dialog/bookdrop-bulk-edit-dialog.component';
import {BookdropPatternExtractDialogComponent} from '../bookdrop-pattern-extract-dialog/bookdrop-pattern-extract-dialog.component';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';

export interface BookdropFileUI {
  file: BookdropFile;
  metadataForm: FormGroup;
  copiedFields: Record<string, boolean>;
  savedFields: Record<string, boolean>;
  selected: boolean;
  showDetails: boolean;
  selectedLibraryId: string | null;
  selectedPathId: string | null;
  availablePaths: { id: string; name: string }[];
}

@Component({
  selector: 'app-bookdrop-file-review-component',
  standalone: true,
  templateUrl: './bookdrop-file-review.component.html',
  styleUrl: './bookdrop-file-review.component.scss',
  imports: [
    ProgressSpinner,
    FormsModule,
    Button,
    Select,
    BookdropFileMetadataPickerComponent,
    Tooltip,
    Divider,
    Checkbox,
    NgClass,
    Paginator,
    InputGroup,
    InputGroupAddonModule,
  ],
})
export class BookdropFileReviewComponent implements OnInit {
  private readonly bookdropService = inject(BookdropService);
  private readonly libraryService = inject(LibraryService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly messageService = inject(MessageService);
  private readonly urlHelper = inject(UrlHelperService);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly pageTitle = inject(PageTitleService);

  @ViewChildren('metadataPicker') metadataPickers!: QueryList<BookdropFileMetadataPickerComponent>;

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;
  private routerSub!: Subscription;

  uploadPattern = '';
  _defaultLibraryId: string | null = null;

  defaultPathId: string | null = null;
  libraries: Library[] = [];
  bookdropFileUis: BookdropFileUI[] = [];
  fileUiCache: Record<number, BookdropFileUI> = {};
  copiedFlags: Record<number, boolean> = {};
  loading = true;
  saving = false;
  includeCoversOnCopy = true;

  pageSize = 50;
  totalRecords = 0;
  currentPage = 0;

  selectAllAcrossPages = false;
  excludedFiles = new Set<number>();

  ngOnInit(): void {
    this.pageTitle.setPageTitle('Review Bookdrop Files');

    this.activatedRoute.queryParams
      .pipe(startWith({}), tap(() => {
        this.loading = true;
        this.loadPage(0);
      }))
      .subscribe();

    this.libraryService.libraryState$
      .subscribe(state => {
        this.libraries = state.libraries ?? [];
      });

    this.appSettings$
      .pipe(filter(Boolean), take(1))
      .subscribe(settings => {
        this.uploadPattern = settings?.uploadPattern ?? '';
      });
  }

  get defaultLibraryId() {
    return this._defaultLibraryId;
  }

  set defaultLibraryId(value: string | null) {
    this._defaultLibraryId = value;

    const selected = this.libraries.find((lib) => lib?.id && String(lib.id) === value)

    if (selected && selected.paths.length === 1) {
      this.defaultPathId = String(selected.paths[0].id)
    }
  }

  get libraryOptions() {
    if (!this.libraries) return [];
    return this.libraries.map(lib => ({label: lib.name, value: String(lib.id ?? '')}));
  }

  get selectedLibraryPaths() {
    if (!this.libraries) return [];
    const selectedLibrary = this.libraries.find(lib => String(lib.id) === this.defaultLibraryId);
    return selectedLibrary?.paths.map(path => ({label: path.path, value: String(path.id ?? '')})) ?? [];
  }

  get canApplyDefaults(): boolean {
    return !!(this.defaultLibraryId && this.defaultPathId);
  }

  get canFinalize(): boolean {
    if (this.selectAllAcrossPages) {
      return (this.totalRecords - this.excludedFiles.size) > 0 && this.areAllSelectedHaveLibraryAndPath();
    }
    const selectedFiles = this.bookdropFileUis.filter(f => f.selected);
    if (selectedFiles.length === 0) return false;
    return selectedFiles.every(f => f.selectedLibraryId && f.selectedPathId);
  }

  get hasSelectedFiles(): boolean {
    if (this.selectAllAcrossPages) {
      return this.totalRecords > this.excludedFiles.size;
    } else {
      return Object.values(this.fileUiCache).some(file => file.selected);
    }
  }

  get selectedCount(): number {
    if (this.selectAllAcrossPages) {
      return this.totalRecords - this.excludedFiles.size;
    } else {
      return Object.values(this.fileUiCache).filter(file => file.selected).length;
    }
  }

  loadPage(page: number): void {
    this.bookdropService.getPendingFiles(page, this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => {
          this.bookdropFileUis = response.content.map(file => {
            const cached = this.fileUiCache[file.id];
            if (cached) {
              cached.file = file;
              return cached;
            } else {
              const fresh = this.createFileUI(file);

              if (this.defaultLibraryId) {
                const selectedLib = this.libraries.find(l => String(l.id) === this.defaultLibraryId);
                const selectedPaths = selectedLib?.paths ?? [];
                fresh.selectedLibraryId = this.defaultLibraryId;
                fresh.availablePaths = selectedPaths.map(p => ({id: String(p.id ?? ''), name: p.path}));
                fresh.selectedPathId = this.defaultPathId ?? null;
              }

              this.fileUiCache[file.id] = fresh;
              return fresh;
            }
          });
          this.totalRecords = response.totalElements;
          this.currentPage = page;
          this.loading = false;
          this.syncCurrentPageSelection();
        },
        error: err => {
          console.error('Error loading files:', err);
          this.loading = false;
        }
      });
  }

  onLibraryChange(file: BookdropFileUI): void {
    const lib = this.libraries.find(l => String(l.id) === file.selectedLibraryId);
    file.availablePaths = lib?.paths.map(p => ({id: String(p.id ?? ''), name: p.path})) ?? [];
    file.selectedPathId = file.availablePaths.length === 1 ? file.availablePaths[0].id : null;
  }

  onMetadataCopied(fileId: number, copied: boolean): void {
    this.copiedFlags[fileId] = copied;
  }

  applyLibraryDefaults(): void {
    if (!this.defaultLibraryId || !this.libraries) return;

    const selectedLib = this.libraries.find(l => String(l.id) === this.defaultLibraryId);
    const selectedPaths = selectedLib?.paths ?? [];

    this.getSelectedFiles().map(fileUi => {
      const cachedfUi = this.fileUiCache[fileUi.file.id];
      cachedfUi.selectedLibraryId = this.defaultLibraryId;
      cachedfUi.availablePaths = selectedPaths.map(path => ({id: String(path.id), name: path.path}));
      cachedfUi.selectedPathId = this.defaultPathId ?? null;
    });
  }

  copyMetadata(): void {
    const files = this.getSelectedFiles().map(fileUi => {
      const cachedfUi = this.fileUiCache[fileUi.file.id];
      const fetched = cachedfUi.file.fetchedMetadata;
      const form = cachedfUi.metadataForm;
      if (!fetched) return;
      for (const key of Object.keys(fetched)) {
        if (!this.includeCoversOnCopy && key === 'thumbnailUrl') continue;
        const value = fetched[key as keyof typeof fetched];
        if (value != null) {
          form.get(key)?.setValue(value);
          cachedfUi.copiedFields[key] = true;
        }
      }
      this.onMetadataCopied(cachedfUi.file.id, true);
    });
  }


  resetMetadata(): void {
    const selectedFiles = this.getSelectedFiles();

    const files = selectedFiles.map(fileUi => {
      const original = fileUi.file.originalMetadata;
      fileUi.metadataForm.patchValue({
        title: original?.title || null,
        subtitle: original?.subtitle || null,
        authors: [...(original?.authors ?? [])].sort(),
        categories: [...(original?.categories ?? [])].sort(),
        moods: [...(original?.moods ?? [])].sort(),
        tags: [...(original?.tags ?? [])].sort(),
        publisher: original?.publisher || null,
        publishedDate: original?.publishedDate || null,
        isbn10: original?.isbn10 ?? null,
        isbn13: original?.isbn13 ?? null,
        description: original?.description ?? null,
        pageCount: original?.pageCount ?? null,
        language: original?.language ?? null,
        asin: original?.asin ?? null,
        amazonRating: original?.amazonRating ?? null,
        amazonReviewCount: original?.amazonReviewCount ?? null,
        goodreadsId: original?.goodreadsId ?? null,
        goodreadsRating: original?.goodreadsRating ?? null,
        goodreadsReviewCount: original?.goodreadsReviewCount ?? null,
        hardcoverId: original?.hardcoverId ?? null,
        hardcoverRating: original?.hardcoverRating ?? null,
        hardcoverReviewCount: original?.hardcoverReviewCount ?? null,
        googleId: original?.googleId ?? null,
        comicvineId: original?.comicvineId ?? null,
        seriesName: original?.seriesName ?? null,
        seriesNumber: original?.seriesNumber ?? null,
        seriesTotal: original?.seriesTotal ?? null,
        thumbnailUrl: this.urlHelper.getBookdropCoverUrl(fileUi.file.id),
      });
      fileUi.copiedFields = {};
      fileUi.savedFields = {};
      this.copiedFlags[fileUi.file.id] = false;
    });
  }

  selectAll(selected: boolean): void {
    if (selected) {
      this.selectAllAcrossPages = true;
      this.excludedFiles.clear();
      Object.values(this.fileUiCache).forEach(file => file.selected = true);
      this.bookdropFileUis.forEach(file => file.selected = true);
    } else {
      this.selectAllAcrossPages = false;
      this.excludedFiles.clear();
      Object.values(this.fileUiCache).forEach(file => file.selected = false);
      this.bookdropFileUis.forEach(file => file.selected = false);
    }
  }

  toggleFileSelection(fileId: number, selected: boolean): void {
    if (this.selectAllAcrossPages) {
      if (!selected) {
        this.excludedFiles.add(fileId);
      } else {
        this.excludedFiles.delete(fileId);
      }
      const cachedFile = this.fileUiCache[fileId];
      if (cachedFile) cachedFile.selected = selected;
    } else {
      const cachedFile = this.fileUiCache[fileId];
      if (cachedFile) cachedFile.selected = selected;
    }
    this.syncCurrentPageSelection();
  }

  confirmReset(): void {
    const selectedCount = this.selectedCount;
    if (selectedCount === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No files selected',
        detail: 'Please select files to reset metadata.',
      });
      return;
    }

    this.confirmationService.confirm({
      message: 'Are you sure you want to reset all metadata changes made to the selected files?',
      header: 'Confirm Reset',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.resetMetadata()
    });
  }

  confirmFinalize(): void {
    const selectedCount = this.selectedCount;
    if (selectedCount === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No files selected',
        detail: 'Please select files to finalize.',
      });
      return;
    }

    this.confirmationService.confirm({
      message: `Are you sure you want to finalize the import of ${selectedCount} file${selectedCount !== 1 ? 's' : ''}?`,
      header: 'Confirm Finalize',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.finalizeImport(),
    });
  }

  confirmDelete(): void {
    const selectedCount = this.selectedCount;
    if (selectedCount === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No files selected',
        detail: 'Please select files to delete.',
      });
      return;
    }

    this.confirmationService.confirm({
      message: `Are you sure you want to delete ${selectedCount} selected Bookdrop file${selectedCount !== 1 ? 's' : ''}? This action cannot be undone.`,
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        const payload: { selectAll: boolean; excludedIds?: number[]; selectedIds?: number[] } = {
          selectAll: this.selectAllAcrossPages,
        };

        if (this.selectAllAcrossPages) {
          payload.excludedIds = Array.from(this.excludedFiles);
        } else {
          payload.selectedIds = Object.values(this.fileUiCache)
            .filter(file => file.selected)
            .map(file => file.file.id);
        }

        this.bookdropService.discardFiles(payload).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Files Deleted',
              detail: 'Selected Bookdrop files were deleted successfully.',
            });

            this.getSelectedFiles().forEach(file => delete this.fileUiCache[file.file.id]);

            this.selectAllAcrossPages = false;
            this.excludedFiles.clear();
            this.loadPage(this.currentPage);
          },
          error: (err) => {
            console.error('Error deleting files:', err);
            this.messageService.add({
              severity: 'error',
              summary: 'Delete Failed',
              detail: 'An error occurred while deleting Bookdrop files.',
            });
          },
        });
      },
    });
  }

  private areAllSelectedHaveLibraryAndPath(): boolean {
    return Object.values(this.fileUiCache).every(file => {
      if (this.selectAllAcrossPages) {
        if (this.excludedFiles.has(file.file.id)) return true;
        return file.selectedLibraryId != null && file.selectedPathId != null;
      } else {
        if (!file.selected) return true;
        return file.selectedLibraryId != null && file.selectedPathId != null;
      }
    });
  }

  private syncCurrentPageSelection(): void {
    if (this.selectAllAcrossPages) {
      this.bookdropFileUis.forEach(fileUi => {
        fileUi.selected = !this.excludedFiles.has(fileUi.file.id);
        const cachedFile = this.fileUiCache[fileUi.file.id];
        if (cachedFile) cachedFile.selected = fileUi.selected;
      });
    }
  }

  private finalizeImport(): void {
    this.saving = true;

    const selectedFiles = this.getSelectedFiles();

    const files = selectedFiles.map(fileUi => {
      const rawMetadata = fileUi.metadataForm.value;
      const metadata = {...rawMetadata};

      if (metadata.thumbnailUrl?.includes('/api/v1/media/bookdrop')) {
        delete metadata.thumbnailUrl;
      }

      return {
        fileId: fileUi.file.id,
        libraryId: Number(fileUi.selectedLibraryId),
        pathId: Number(fileUi.selectedPathId),
        metadata,
      };
    });

    const payload: BookdropFinalizePayload = {
      selectAll: this.selectAllAcrossPages,
      excludedIds: this.selectAllAcrossPages ? Array.from(this.excludedFiles) : undefined,
      defaultLibraryId: this.defaultLibraryId ? Number(this.defaultLibraryId) : undefined,
      defaultPathId: this.defaultPathId ? Number(this.defaultPathId) : undefined,
      files,
    };

    this.bookdropService.finalizeImport(payload).subscribe({
      next: (result: BookdropFinalizeResult) => {
        this.saving = false;

        this.messageService.add({
          severity: 'success',
          summary: 'Import Complete',
          detail: 'Import process finished. See details below.',
        });

        this.dialogLauncherService.openBookdropFinalizeResultDialog(result);

        const finalizedIds = new Set(files.map(f => f.fileId));
        Object.keys(this.fileUiCache).forEach(idStr => {
          const id = Number(idStr);
          if (finalizedIds.has(id)) delete this.fileUiCache[id];
        });

        this.selectAllAcrossPages = false;
        this.excludedFiles.clear();
        this.loadPage(this.currentPage);
      },
      error: (err) => {
        console.error('Error finalizing import:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Import Failed',
          detail: 'Some files could not be moved. Please check the console for more details.',
        });
        this.saving = false;
      }
    });
  }

  private createMetadataForm(original: BookMetadata | undefined, bookdropFileId: number): FormGroup {
    return new FormGroup({
      title: new FormControl(original?.title ?? ''),
      subtitle: new FormControl(original?.subtitle ?? ''),
      authors: new FormControl([...(original?.authors ?? [])].sort()),
      categories: new FormControl([...(original?.categories ?? [])].sort()),
      moods: new FormControl([...(original?.moods ?? [])].sort()),
      tags: new FormControl([...(original?.tags ?? [])].sort()),
      publisher: new FormControl(original?.publisher ?? ''),
      publishedDate: new FormControl(original?.publishedDate ?? ''),
      isbn10: new FormControl(original?.isbn10 ?? ''),
      isbn13: new FormControl(original?.isbn13 ?? ''),
      description: new FormControl(original?.description ?? ''),
      pageCount: new FormControl(original?.pageCount ?? ''),
      language: new FormControl(original?.language ?? ''),
      asin: new FormControl(original?.asin ?? ''),
      amazonRating: new FormControl(original?.amazonRating ?? ''),
      amazonReviewCount: new FormControl(original?.amazonReviewCount ?? ''),
      goodreadsId: new FormControl(original?.goodreadsId ?? ''),
      goodreadsRating: new FormControl(original?.goodreadsRating ?? ''),
      goodreadsReviewCount: new FormControl(original?.goodreadsReviewCount ?? ''),
      hardcoverId: new FormControl(original?.hardcoverId ?? ''),
      hardcoverRating: new FormControl(original?.hardcoverRating ?? ''),
      hardcoverReviewCount: new FormControl(original?.hardcoverReviewCount ?? ''),
      googleId: new FormControl(original?.googleId ?? ''),
      comicvineId: new FormControl(original?.comicvineId ?? ''),
      seriesName: new FormControl(original?.seriesName ?? ''),
      seriesNumber: new FormControl(original?.seriesNumber ?? ''),
      seriesTotal: new FormControl(original?.seriesTotal ?? ''),
      thumbnailUrl: new FormControl(this.urlHelper.getBookdropCoverUrl(bookdropFileId)),
    });
  }

  private createFileUI(file: BookdropFile): BookdropFileUI {
    const metadataForm = this.createMetadataForm(file.originalMetadata, file.id);
    return {
      file,
      selected: false,
      showDetails: false,
      selectedLibraryId: null,
      selectedPathId: null,
      availablePaths: [],
      metadataForm,
      copiedFields: {},
      savedFields: {}
    };
  }

  rescanBookdrop() {
    this.bookdropService.rescan().subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Rescan Triggered',
          detail: 'Bookdrop rescan has been started successfully.',
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Rescan Failed',
          detail: 'Unable to trigger bookdrop rescan. Please try again.',
        });
        console.error(err);
      }
    });
  }

  openBulkEditDialog(): void {
    const selectedFiles = this.getSelectedFiles();
    const totalCount = this.selectAllAcrossPages 
      ? this.totalRecords - this.excludedFiles.size 
      : selectedFiles.length;
      
    if (totalCount === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No files selected',
        detail: 'Please select files to bulk edit.',
      });
      return;
    }

    const dialogRef = this.dialogLauncherService.openDialog(BookdropBulkEditDialogComponent, {
      header: `Bulk Edit ${totalCount} Files`,
      width: '600px',
      modal: true,
      closable: true,
      data: {fileCount: totalCount},
    });

    dialogRef?.onClose.subscribe((result: BulkEditResult | null) => {
      if (result) {
        this.applyBulkMetadataViaBackend(result);
      }
    });
  }

  openPatternExtractDialog(): void {
    const selectedFiles = this.getSelectedFiles();
    const totalCount = this.selectAllAcrossPages 
      ? this.totalRecords - this.excludedFiles.size 
      : selectedFiles.length;
      
    if (totalCount === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'No files selected',
        detail: 'Please select files to extract metadata from.',
      });
      return;
    }

    const sampleFiles = selectedFiles.slice(0, 5).map(f => f.file.fileName);
    const selectedIds = selectedFiles.map(f => f.file.id);

    const dialogRef = this.dialogLauncherService.openDialog(BookdropPatternExtractDialogComponent, {
      header: 'Extract Metadata from Filenames',
      width: '700px',
      modal: true,
      closable: true,
      data: {
        sampleFiles,
        fileCount: totalCount,
        selectAll: this.selectAllAcrossPages,
        excludedIds: Array.from(this.excludedFiles),
        selectedIds,
      },
    });

    dialogRef?.onClose.subscribe((result: { results: FileExtractionResult[] } | null) => {
      if (result?.results) {
        this.applyExtractedMetadata(result.results);
      }
    });
  }

  private getSelectedFiles(): BookdropFileUI[] {
    return Object.values(this.fileUiCache).filter(file => {
      if (this.selectAllAcrossPages) {
        return !this.excludedFiles.has(file.file.id);
      }
      return file.selected;
    });
  }

  private applyBulkMetadataViaBackend(result: BulkEditResult): void {
    const selectedFiles = this.getSelectedFiles();
    const selectedIds = selectedFiles.map(f => f.file.id);

    this.applyBulkMetadataToUI(result, selectedFiles);

    const enabledFieldsArray = Array.from(result.enabledFields);

    const payload: BackendBulkEditRequest = {
      fields: result.fields,
      enabledFields: enabledFieldsArray,
      mergeArrays: result.mergeArrays,
      selectAll: this.selectAllAcrossPages,
      excludedIds: this.selectAllAcrossPages ? Array.from(this.excludedFiles) : undefined,
      selectedIds: !this.selectAllAcrossPages ? selectedIds : undefined,
    };

    this.bookdropService.bulkEditMetadata(payload).subscribe({
      next: (backendResult: BackendBulkEditResult) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Bulk Edit Applied',
          detail: `Updated metadata for ${backendResult.successfullyUpdated} of ${backendResult.totalFiles} file(s).`,
        });
      },
      error: (err) => {
        console.error('Error applying bulk edit:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Bulk Edit Failed',
          detail: 'An error occurred while applying bulk edits.',
        });
      },
    });
  }

  private applyBulkMetadataToUI(result: BulkEditResult, selectedFiles: BookdropFileUI[]): void {
    selectedFiles.forEach(fileUi => {
      result.enabledFields.forEach(fieldName => {
        const value = result.fields[fieldName as keyof BookMetadata];
        if (value === undefined || value === null) {
          return;
        }

        if (Array.isArray(value) && value.length === 0) {
          return;
        }

        const control = fileUi.metadataForm.get(fieldName);
        if (!control) {
          return;
        }

        if (result.mergeArrays && Array.isArray(value)) {
          const currentValue = control.value || [];
          const merged = [...new Set([...currentValue, ...value])];
          control.setValue(merged);
        } else {
          control.setValue(value);
        }
      });
    });
  }

  private applyExtractedMetadata(results: FileExtractionResult[]): void {
    let appliedCount = 0;

    results.forEach(result => {
      if (!result.success || !result.extractedMetadata) {
        return;
      }

      const fileUi = this.fileUiCache[result.fileId];
      if (!fileUi) {
        return;
      }

      Object.entries(result.extractedMetadata).forEach(([key, value]) => {
        if (value === null || value === undefined) {
          return;
        }

        const control = fileUi.metadataForm.get(key);
        if (control) {
          control.setValue(value);
        }
      });

      appliedCount++;
    });

    this.messageService.add({
      severity: 'success',
      summary: 'Pattern Extraction Applied',
      detail: `Applied extracted metadata to ${appliedCount} file(s).`,
    });
  }
}
