import {Component, inject, OnInit, ViewChild, effect} from '@angular/core';
import {FileSelectEvent, FileUpload, FileUploadHandlerEvent} from 'primeng/fileupload';
import {Button} from 'primeng/button';
import {FormsModule} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {Badge} from 'primeng/badge';
import {LibraryService} from '../../../features/book/service/library.service';
import {Library, LibraryPath} from '../../../features/book/model/library.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient, HttpEventType, HttpRequest} from '@angular/common/http';
import {Tooltip} from 'primeng/tooltip';
import {AppSettingsService} from '../../service/app-settings.service';
import {SelectButton} from 'primeng/selectbutton';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {ProgressBar} from 'primeng/progressbar';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface UploadingFile {
  file: File;
  status: 'Pending' | 'Uploading' | 'Uploaded' | 'Failed';
  progress: number;
  errorMessage?: string;
}

type FileChooserCallback = () => void;
type FileRemoveCallback = (event: Event, index: number) => void;

@Component({
  selector: 'app-book-uploader',
  standalone: true,
  imports: [
    FileUpload,
    Button,
    FormsModule,
    Select,
    Badge,
    Tooltip,
    SelectButton,
    ProgressBar,
    TranslocoDirective
  ],
  templateUrl: './book-uploader.component.html',
  styleUrl: './book-uploader.component.scss'
})
export class BookUploaderComponent implements OnInit {
  @ViewChild(FileUpload) fileUpload!: FileUpload;

  files: UploadingFile[] = [];
  isUploading: boolean = false;
  uploadCompleted: boolean = false;
  _selectedLibrary: Library | null = null;
  selectedPath: LibraryPath | null = null;

  private readonly libraryService = inject(LibraryService);
  private readonly messageService = inject(MessageService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly http = inject(HttpClient);
  private readonly ref = inject(DynamicDialogRef);
  private readonly t = inject(TranslocoService);

  readonly libraries = this.libraryService.libraries;
  maxFileSizeBytes?: number;
  maxFileSizeDisplay: string = '100 MB';
  stateOptions = [
    {label: this.t.translate('shared.bookUploader.destinationLibrary'), value: 'library'},
    {label: this.t.translate('shared.bookUploader.destinationBookdrop'), value: 'bookdrop'}
  ];
  value = 'library';
  private readonly selectSingleLibraryEffect = effect(() => {
    const libraries = this.libraries();
    if (libraries.length !== 1 || this.selectedLibrary) {
      return;
    }

    this.selectedLibrary = libraries[0];
  });

  private readonly loadSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (!settings) return;
    const maxSizeMb = settings.maxFileUploadSizeInMb ?? 100;
    this.maxFileSizeBytes = maxSizeMb * 1024 * 1024;
    this.maxFileSizeDisplay = `${maxSizeMb} MB`;
  });

  ngOnInit(): void {
  }

  get selectedLibrary(): Library | null {
    return this._selectedLibrary;
  }

  set selectedLibrary(library: Library | null) {
    this._selectedLibrary = library;

    if (library?.paths?.length === 1) {
      this.selectedPath = library.paths[0];
    }
  }

  hasPendingFiles(): boolean {
    return this.files.some(f => f.status === 'Pending');
  }

  filesPresent(): boolean {
    return this.files.length > 0;
  }

  choose(_event: Event, chooseCallback: FileChooserCallback): void {
    chooseCallback();
  }

  onClear(clearCallback: () => void): void {
    clearCallback();
    this.files = [];
  }

  onFilesSelect(event: FileSelectEvent): void {
    if (this.value === 'library' && (!this.selectedLibrary || !this.selectedPath)) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('shared.bookUploader.toast.noDestinationSummary'),
        detail: this.t.translate('shared.bookUploader.toast.noDestinationDetail'),
        life: 5000
      });
      // We need to clear the files input explicitely, otherwise the files remain selected in the file upload component
      this.fileUpload.clear();
      return;
    }

    const newFiles = event.currentFiles;
    for (const file of newFiles) {
      const exists = this.files.some(f => f.file.name === file.name && f.file.size === file.size);
      if (exists) {
        continue;
      }

      if (this.maxFileSizeBytes && file.size > this.maxFileSizeBytes) {
        const errorMsg = `File exceeds maximum size of ${this.formatSize(this.maxFileSizeBytes)}`;
        this.files.unshift({
          file,
          status: 'Failed',
          progress: 0,
          errorMessage: errorMsg
        });
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('shared.bookUploader.toast.fileTooLargeSummary'),
          detail: this.t.translate('shared.bookUploader.toast.fileTooLargeDetail', {fileName: file.name, maxSize: this.formatSize(this.maxFileSizeBytes)}),
          life: 5000
        });
      } else {
        this.files.unshift({file, status: 'Pending', progress: 0});
      }
    }
  }

  onRemoveTemplatingFile(event: Event, file: File, removeFileCallback: FileRemoveCallback, _index?: number): void {
    void _index;
    // Remove from our tracking array
    this.files = this.files.filter(f => f.file !== file);

    // Find and remove from p-fileupload's internal array (index may differ from ours)
    const fileUploadIndex = this.fileUpload.files?.findIndex(f => f.name === file.name && f.size === file.size) ?? -1;
    if (fileUploadIndex >= 0) {
      removeFileCallback(event, fileUploadIndex);
    }
  }

  uploadEvent(uploadCallback: () => void): void {
    uploadCallback();
  }

  uploadFiles(_event: FileUploadHandlerEvent): void {
    void _event;
    if (this.value === 'library' && (!this.selectedLibrary || !this.selectedPath)) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('shared.bookUploader.toast.missingDataSummary'),
        detail: this.t.translate('shared.bookUploader.toast.missingDataDetail'),
        life: 4000
      });
      return;
    }

    const filesToUpload = this.files.filter(f => f.status === 'Pending');
    if (filesToUpload.length === 0) return;

    this.isUploading = true;
    this.uploadCompleted = false;
    const destination = this.value;
    const libraryId = this.selectedLibrary?.id?.toString();
    const pathId = this.selectedPath?.id?.toString();

    this.uploadBatch(filesToUpload, 0, 1, destination, libraryId, pathId);
  }

  private uploadBatch(files: UploadingFile[], startIndex: number, batchSize: number, destination: string, libraryId?: string, pathId?: string): void {
    const batch = files.slice(startIndex, startIndex + batchSize);
    if (batch.length === 0) {
      this.isUploading = false;
      this.uploadCompleted = true;
      if (destination === 'bookdrop') {
        this.ref.close('uploaded_to_bookdrop');
      }
      return;
    }

    let pending = batch.length;

    for (const uploadFile of batch) {
      uploadFile.status = 'Uploading';
      uploadFile.progress = 0;

      const formData = new FormData();
      const cleanFile = new File([uploadFile.file], uploadFile.file.name, {type: uploadFile.file.type});
      formData.append('file', cleanFile, uploadFile.file.name);

      let uploadUrl: string;
      if (destination === 'library') {
        if (libraryId && pathId) {
          formData.append('libraryId', libraryId);
          formData.append('pathId', pathId);
        }
        uploadUrl = `${API_CONFIG.BASE_URL}/api/v1/files/upload`;
      } else {
        uploadUrl = `${API_CONFIG.BASE_URL}/api/v1/files/upload/bookdrop`;
      }

      const req = new HttpRequest('POST', uploadUrl, formData, {
        reportProgress: true
      });

      this.http.request(req).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            uploadFile.progress = Math.round((event.loaded / event.total) * 100);
          } else if (event.type === HttpEventType.Response) {
            uploadFile.status = 'Uploaded';
            uploadFile.progress = 100;
            if (--pending === 0) {
              setTimeout(() => {
                this.uploadBatch(files, startIndex + batchSize, batchSize, destination, libraryId, pathId);
              }, 1000);
            }
          }
        },
        error: (err) => {
          uploadFile.status = 'Failed';
          uploadFile.progress = 0;
          uploadFile.errorMessage = err?.error?.message || this.t.translate('shared.bookUploader.toast.uploadFailedDefault');
          if (--pending === 0) {
            setTimeout(() => {
              this.uploadBatch(files, startIndex + batchSize, batchSize, destination, libraryId, pathId);
            }, 1000);
          }
        }
      });
    }
  }

  isChooseDisabled(): boolean {
    if (this.value === 'bookdrop') {
      return this.isUploading;
    }
    return !this.selectedLibrary || !this.selectedPath || this.isUploading;
  }

  isUploadDisabled(): boolean {
    return this.isChooseDisabled() || !this.filesPresent() || !this.hasPendingFiles();
  }

  isUploadZoneActive(): boolean {
    if (this.value === 'bookdrop') {
      return true;
    }
    return !!(this.selectedLibrary && this.selectedPath);
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
    if (uploadFile.status === 'Failed' && uploadFile.errorMessage?.includes('exceeds maximum size')) {
      return this.t.translate('shared.bookUploader.statusTooLarge');
    }
    switch (uploadFile.status) {
      case 'Pending':
        return this.t.translate('shared.bookUploader.statusReady');
      case 'Uploading':
        return this.t.translate('shared.bookUploader.statusUploading');
      case 'Uploaded':
        return this.t.translate('shared.bookUploader.statusUploaded');
      case 'Failed':
        return this.t.translate('shared.bookUploader.statusFailed');
      default:
        return uploadFile.status;
    }
  }

  hasUploadCompleted(): boolean {
    return this.uploadCompleted;
  }

  closeDialog(): void {
    this.ref.close();
  }

  getOverallProgress(): number {
    if (this.files.length === 0) return 0;
    const totalProgress = this.files.reduce((sum, f) => sum + f.progress, 0);
    return Math.round(totalProgress / this.files.length);
  }

  getUploadedCount(): number {
    return this.files.filter(f => f.status === 'Uploaded').length;
  }

  getFailedCount(): number {
    return this.files.filter(f => f.status === 'Failed').length;
  }

  getUploadingCount(): number {
    return this.files.filter(f => f.status === 'Uploading').length;
  }

  getTotalBytes(): number {
    return this.files.reduce((sum, f) => sum + f.file.size, 0);
  }

  getUploadedBytes(): number {
    return this.files
      .filter(f => f.status === 'Uploaded')
      .reduce((sum, f) => sum + f.file.size, 0);
  }

}
