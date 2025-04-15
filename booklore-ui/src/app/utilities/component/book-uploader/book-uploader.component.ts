import {Component, inject} from '@angular/core';
import {FileSelectEvent, FileUpload, FileUploadHandlerEvent} from 'primeng/fileupload';
import {Button} from 'primeng/button';
import {AsyncPipe, NgForOf, NgIf, NgSwitch, NgSwitchCase} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {Badge} from 'primeng/badge';
import {LibraryService} from '../../../book/service/library.service';
import {Library, LibraryPath} from '../../../book/model/library.model';
import {LibraryState} from '../../../book/model/state/library-state.model';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../config/api-config';
import {Book} from '../../../book/model/book.model';
import {HttpClient} from '@angular/common/http';
import {Tooltip} from 'primeng/tooltip';

interface UploadingFile {
  file: File;
  status: 'Pending' | 'Uploading' | 'Uploaded' | 'Failed';
  errorMessage?: string;
}

@Component({
  selector: 'app-book-uploader',
  standalone: true,
  imports: [
    FileUpload,
    Button,
    AsyncPipe,
    FormsModule,
    NgIf,
    Select,
    Badge,
    NgForOf,
    NgSwitchCase,
    NgSwitch,
    Tooltip
  ],
  templateUrl: './book-uploader.component.html',
  styleUrl: './book-uploader.component.scss'
})
export class BookUploaderComponent {
  files: UploadingFile[] = [];
  isUploading: boolean = false;
  selectedLibrary: Library | null = null;
  selectedPath: LibraryPath | null = null;

  private readonly libraryService = inject(LibraryService);
  private readonly messageService = inject(MessageService);
  private readonly http = inject(HttpClient);

  readonly libraryState$: Observable<LibraryState> = this.libraryService.libraryState$;

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
    for (const file of newFiles) {
      const exists = this.files.some(f => f.file.name === file.name && f.file.size === file.size);
      if (!exists) {
        this.files.unshift({file, status: 'Pending'});
      }
    }
  }

  onRemoveTemplatingFile(_event: any, _file: File, removeFileCallback: (event: any, index: number) => void, index: number): void {
    removeFileCallback(_event, index);
  }

  uploadEvent(uploadCallback: () => void): void {
    uploadCallback();
  }

  uploadFiles(event: FileUploadHandlerEvent): void {
    if (!this.selectedLibrary || !this.selectedPath) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Missing Data',
        detail: 'Please select a library and path before uploading.',
        life: 4000
      });
      return;
    }

    const libraryId = this.selectedLibrary.id!.toString();
    const pathId = this.selectedPath.id!.toString();
    const filesToUpload = this.files.filter(f => f.status === 'Pending');

    if (filesToUpload.length === 0) return;

    this.isUploading = true;
    let pending = filesToUpload.length;

    for (const uploadFile of filesToUpload) {
      uploadFile.status = 'Uploading';

      const formData = new FormData();
      formData.append('file', uploadFile.file);
      formData.append('libraryId', libraryId);
      formData.append('pathId', pathId);

      this.http.post<Book>(`${API_CONFIG.BASE_URL}/api/v1/files/upload`, formData).subscribe({
        next: () => {
          uploadFile.status = 'Uploaded';
          if (--pending === 0) this.isUploading = false;
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
    return !this.selectedLibrary || !this.selectedPath || this.isUploading;
  }

  isUploadDisabled(): boolean {
    return this.isChooseDisabled() || !this.filesPresent() || !this.hasPendingFiles;
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
