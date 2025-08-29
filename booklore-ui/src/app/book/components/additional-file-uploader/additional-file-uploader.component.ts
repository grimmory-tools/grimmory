import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { Select } from 'primeng/select';
import { Button } from 'primeng/button';
import { FileSelectEvent, FileUpload, FileUploadHandlerEvent } from 'primeng/fileupload';
import { Badge } from 'primeng/badge';
import { Tooltip } from 'primeng/tooltip';
import { Subject, takeUntil } from 'rxjs';
import { BookService } from '../../service/book.service';
import { AppSettingsService } from '../../../core/service/app-settings.service';
import { Book, AdditionalFileType } from '../../model/book.model';
import { MessageService } from 'primeng/api';
import { filter, take } from 'rxjs/operators';

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
    CommonModule,
    FormsModule,
    Select,
    Button,
    FileUpload,
    Badge,
    Tooltip
  ],
  templateUrl: './additional-file-uploader.component.html',
  styleUrls: ['./additional-file-uploader.component.scss']
})
export class AdditionalFileUploaderComponent implements OnInit, OnDestroy {
  book!: Book;
  files: UploadingFile[] = [];
  fileType: AdditionalFileType = AdditionalFileType.ALTERNATIVE_FORMAT;
  description: string = '';
  isUploading = false;
  maxFileSizeBytes?: number;

  fileTypeOptions: FileTypeOption[] = [
    { label: 'Alternative Format', value: AdditionalFileType.ALTERNATIVE_FORMAT },
    { label: 'Supplementary File', value: AdditionalFileType.SUPPLEMENTARY }
  ];

  private destroy$ = new Subject<void>();

  constructor(
    private dialogRef: DynamicDialogRef,
    private config: DynamicDialogConfig,
    private bookService: BookService,
    private appSettingsService: AppSettingsService,
    private messageService: MessageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.book = this.config.data.book;
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

  choose(_event: any, chooseCallback: () => void): void {
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
      this.files = [{
        file,
        status: 'Pending'
      }];
    }
  }

  onRemoveTemplatingFile(_event: any, _file: File, removeFileCallback: (event: any, index: number) => void, index: number): void {
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

      this.bookService.uploadAdditionalFile(
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
          uploadFile.errorMessage = err?.error?.message || 'Upload failed due to unknown error.';
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
}