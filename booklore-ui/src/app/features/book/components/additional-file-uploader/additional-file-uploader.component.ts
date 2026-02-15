import { Component, OnInit, OnDestroy, ChangeDetectorRef, inject } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { Select } from 'primeng/select';
import { Button } from 'primeng/button';
import { FileSelectEvent, FileUpload, FileUploadHandlerEvent } from 'primeng/fileupload';
import { Badge } from 'primeng/badge';
import { Tooltip } from 'primeng/tooltip';
import { Subject, takeUntil } from 'rxjs';
import { BookFileService } from '../../service/book-file.service';
import { AppSettingsService } from '../../../../shared/service/app-settings.service';
import { Book, AdditionalFileType } from '../../model/book.model';
import { MessageService } from 'primeng/api';
import { filter, take } from 'rxjs/operators';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';

interface FileTypeOption {
  label: string;
  value: AdditionalFileType;
}

interface UploadingFile {
  file: File;
  status: 'Pending' | 'Uploading' | 'Uploaded' | 'Failed';
  errorMessage?: string;
}

@Component({
  selector: 'app-additional-file-uploader',
  standalone: true,
  imports: [
    FormsModule,
    Select,
    Button,
    FileUpload,
    Badge,
    Tooltip,
    TranslocoDirective
],
  templateUrl: './additional-file-uploader.component.html',
  styleUrls: ['./additional-file-uploader.component.scss']
})
export class AdditionalFileUploaderComponent implements OnInit, OnDestroy {
  private readonly t = inject(TranslocoService);

  book!: Book;
  files: UploadingFile[] = [];
  fileType: AdditionalFileType = AdditionalFileType.ALTERNATIVE_FORMAT;
  description: string = '';
  isUploading = false;
  maxFileSizeBytes?: number;

  fileTypeOptions: FileTypeOption[] = [];

  private destroy$ = new Subject<void>();

  constructor(
    private dialogRef: DynamicDialogRef,
    private config: DynamicDialogConfig,
    private bookFileService: BookFileService,
    private appSettingsService: AppSettingsService,
    private messageService: MessageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.book = this.config.data.book;
    this.fileTypeOptions = [
      { label: this.t.translate('book.fileUploader.typeAlternativeFormat'), value: AdditionalFileType.ALTERNATIVE_FORMAT },
      { label: this.t.translate('book.fileUploader.typeSupplementary'), value: AdditionalFileType.SUPPLEMENTARY }
    ];
    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        if (settings) {
          this.maxFileSizeBytes = (settings.maxFileUploadSizeInMb || 100) * 1024 * 1024;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  hasPendingFiles(): boolean {
    return this.files.some(f => f.status === 'Pending');
  }

  filesPresent(): boolean {
    return this.files.length > 0;
  }

  choose(_event: unknown, chooseCallback: () => void): void {
    chooseCallback();
  }

  onClear(clearCallback: () => void): void {
    clearCallback();
    this.files = [];
  }

  onFilesSelect(event: FileSelectEvent): void {
    const newFiles = event.currentFiles;
    // Only take the first file for single file upload
    if (newFiles.length > 0) {
      const file = newFiles[0];

      if (this.maxFileSizeBytes && file.size > this.maxFileSizeBytes) {
        const maxSize = this.formatSize(this.maxFileSizeBytes);
        const errorMsg = this.t.translate('book.fileUploader.toast.fileTooLargeError', { maxSize });
        this.files = [{
          file,
          status: 'Failed',
          errorMessage: errorMsg
        }];
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('book.fileUploader.toast.fileTooLargeSummary'),
          detail: this.t.translate('book.fileUploader.toast.fileTooLargeDetail', { fileName: file.name, maxSize }),
          life: 5000
        });
      } else {
        this.files = [{
          file,
          status: 'Pending'
        }];
      }
    }
  }

  onRemoveTemplatingFile(_event: unknown, _file: File, removeFileCallback: (event: unknown, index: number) => void, index: number): void {
    removeFileCallback(_event, index);
  }

  uploadEvent(uploadCallback: () => void): void {
    uploadCallback();
  }

  uploadFiles(event: FileUploadHandlerEvent): void {
    const filesToUpload = this.files.filter(f => f.status === 'Pending');

    if (filesToUpload.length === 0) return;

    this.isUploading = true;
    let pending = filesToUpload.length;

    for (const uploadFile of filesToUpload) {
      uploadFile.status = 'Uploading';

      this.bookFileService.uploadAdditionalFile(
        this.book.id,
        uploadFile.file,
        this.fileType,
        this.description || undefined
      ).subscribe({
        next: () => {
          uploadFile.status = 'Uploaded';
          if (--pending === 0) {
            this.isUploading = false;
            this.dialogRef.close({ success: true });
          }
        },
        error: (err) => {
          uploadFile.status = 'Failed';
          uploadFile.errorMessage = err?.error?.message || this.t.translate('book.fileUploader.toast.uploadFailedUnknown');
          console.error('Upload failed for', uploadFile.file.name, err);
          if (--pending === 0) {
            this.isUploading = false;
          }
        }
      });
    }
  }

  isChooseDisabled(): boolean {
    return this.isUploading;
  }

  isUploadDisabled(): boolean {
    return this.isChooseDisabled() || !this.filesPresent() || !this.hasPendingFiles();
  }

  formatSize(bytes: number): string {
    const k = 1024;
    const dm = 2;
    if (bytes < k) return `${bytes} B`;
    if (bytes < k * k) return `${(bytes / k).toFixed(dm)} KB`;
    return `${(bytes / (k * k)).toFixed(dm)} MB`;
  }

  getBadgeSeverity(status: UploadingFile['status']): 'info' | 'warn' | 'success' | 'danger' {
    switch (status) {
      case 'Pending':
        return 'warn';
      case 'Uploading':
        return 'info';
      case 'Uploaded':
        return 'success';
      case 'Failed':
        return 'danger';
      default:
        return 'info';
    }
  }

  getFileStatusLabel(uploadFile: UploadingFile): string {
    if (uploadFile.status === 'Failed' && uploadFile.errorMessage?.includes('maximum size')) {
      return this.t.translate('book.fileUploader.statusTooLarge');
    }
    switch (uploadFile.status) {
      case 'Pending':
        return this.t.translate('book.fileUploader.statusReady');
      case 'Uploading':
        return this.t.translate('book.fileUploader.statusUploading');
      case 'Uploaded':
        return this.t.translate('book.fileUploader.statusUploaded');
      case 'Failed':
        return this.t.translate('book.fileUploader.statusFailed');
      default:
        return uploadFile.status;
    }
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
