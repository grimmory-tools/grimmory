import {Component, DestroyRef, inject, OnInit, QueryList, ViewChildren} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter, take} from 'rxjs/operators';

import {BookdropFile, BookdropFileTaskService, BookdropFinalizePayload, BookdropFinalizeResult} from '../bookdrop-file-task.service';
import {LibraryService} from '../../book/service/library.service';
import {Library} from '../../book/model/library.model';

import {ProgressSpinner} from 'primeng/progressspinner';
import {FormControl, FormGroup, FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {Tooltip} from 'primeng/tooltip';
import {Divider} from 'primeng/divider';
import {ConfirmationService, MessageService} from 'primeng/api';

import {BookdropFileMetadataPickerComponent} from '../bookdrop-file-metadata-picker-component/bookdrop-file-metadata-picker.component';
import {Observable} from 'rxjs';

import {AppSettings} from '../../core/model/app-settings.model';
import {AppSettingsService} from '../../core/service/app-settings.service';
import {BookdropFinalizeResultDialogComponent} from '../bookdrop-finalize-result-dialog-component/bookdrop-finalize-result-dialog-component';
import {DialogService} from 'primeng/dynamicdialog';
import {BookMetadata} from '../../book/model/book.model';
import {UrlHelperService} from '../../utilities/service/url-helper.service';
import {Checkbox} from 'primeng/checkbox';
import {NgClass, NgStyle} from '@angular/common';
import {Paginator} from 'primeng/paginator';

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
    NgStyle,
    NgClass,
    Paginator,
  ],
})
export class BookdropFileReviewComponent implements OnInit {
  private readonly bookdropFileService = inject(BookdropFileTaskService);
  private readonly libraryService = inject(LibraryService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogService = inject(DialogService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly messageService = inject(MessageService);
  private readonly urlHelper = inject(UrlHelperService);

  @ViewChildren('metadataPicker') metadataPickers!: QueryList<BookdropFileMetadataPickerComponent>;

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  uploadPattern = '';
  _defaultLibraryId: string | null = null;

  defaultPathId: string | null = null;
  libraries: Library[] = [];
  bookdropFileUis: BookdropFileUI[] = [];
  fileUiCache: Record<number, BookdropFileUI> = {};
  copiedFlags: Record<number, boolean> = {};
  loading = true;
  saving = false;

  pageSize = 50;
  totalRecords = 0;
  currentPage = 0;

  selectAllAcrossPages = false;
  excludedFiles = new Set<number>();

  ngOnInit(): void {
    this.loading = true;
    this.loadPage(0);

    this.libraryService.libraryState$
      .pipe(filter(state => !!state?.loaded), take(1))
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
    this.bookdropFileService.getPendingFiles(page, this.pageSize)
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

  applyDefaultsToAll(): void {
    if (!this.defaultLibraryId || !this.libraries) return;

    const selectedLib = this.libraries.find(l => String(l.id) === this.defaultLibraryId);
    const selectedPaths = selectedLib?.paths ?? [];

    Object.values(this.fileUiCache).forEach(file => {
      file.selectedLibraryId = this.defaultLibraryId;
      file.availablePaths = selectedPaths.map(path => ({id: String(path.id), name: path.path}));
      file.selectedPathId = this.defaultPathId ?? null;
    });
  }

  copyAll(includeThumbnail: boolean): void {
    Object.values(this.fileUiCache).forEach(fileUi => {
      const fetched = fileUi.file.fetchedMetadata;
      const form = fileUi.metadataForm;
      if (!fetched) return;
      for (const key of Object.keys(fetched)) {
        if (!includeThumbnail && key === 'thumbnailUrl') continue;
        const value = fetched[key as keyof typeof fetched];
        if (value != null) {
          form.get(key)?.setValue(value);
          fileUi.copiedFields[key] = true;
        }
      }
      this.onMetadataCopied(fileUi.file.id, true);
    });
  }

  resetAll(): void {
    Object.values(this.fileUiCache).forEach(fileUi => {
      const original = fileUi.file.originalMetadata;
      fileUi.metadataForm.patchValue({
        title: original?.title || null,
        subtitle: original?.subtitle || null,
        authors: [...(original?.authors ?? [])].sort(),
        categories: [...(original?.categories ?? [])].sort(),
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
    });
    this.copiedFlags = {};
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
      acceptLabel: 'Yes',
      rejectLabel: 'Cancel',
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
      accept: () => {
        const payload: any = {
          selectAll: this.selectAllAcrossPages,
        };

        if (this.selectAllAcrossPages) {
          payload.excludedIds = Array.from(this.excludedFiles);
        } else {
          payload.selectedIds = Object.values(this.fileUiCache)
            .filter(file => file.selected)
            .map(file => file.file.id);
        }

        this.bookdropFileService.discardFiles(payload).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Files Deleted',
              detail: 'Selected Bookdrop files were deleted successfully.',
            });

            const toDelete = Object.values(this.fileUiCache).filter(file => {
              return this.selectAllAcrossPages
                ? !this.excludedFiles.has(file.file.id)
                : file.selected;
            });
            toDelete.forEach(file => delete this.fileUiCache[file.file.id]);

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

    const selectedFiles = Object.values(this.fileUiCache).filter(file => {
      if (this.selectAllAcrossPages) {
        return !this.excludedFiles.has(file.file.id);
      } else {
        return file.selected;
      }
    });

    const files = selectedFiles.map(fileUi => {
      const rawMetadata = fileUi.metadataForm.value;
      const metadata = {...rawMetadata};

      if (metadata.thumbnailUrl?.includes('/api/bookdrop/')) {
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

    this.bookdropFileService.finalizeImport(payload).subscribe({
      next: (result: BookdropFinalizeResult) => {
        this.saving = false;

        this.messageService.add({
          severity: 'success',
          summary: 'Import Complete',
          detail: 'Import process finished. See details below.',
        });

        this.dialogService.open(BookdropFinalizeResultDialogComponent, {
          header: 'Import Summary',
          modal: true,
          closable: true,
          closeOnEscape: true,
          data: {result: result},
        });

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
}
