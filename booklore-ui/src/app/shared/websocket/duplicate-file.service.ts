import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {DuplicateFileNotification} from './model/duplicate-file-notification.model';

@Injectable({
  providedIn: 'root'
})
export class DuplicateFileService {
  private duplicateFiles: DuplicateFileNotification[] = [];
  private duplicateFilesSubject = new BehaviorSubject<DuplicateFileNotification[]>([]);

  duplicateFiles$ = this.duplicateFilesSubject.asObservable();

  addDuplicateFile(notification: DuplicateFileNotification) {
    const exists = this.duplicateFiles.some(file =>
      file.fullPath === notification.fullPath && file.libraryId === notification.libraryId
    );

    if (!exists) {
      this.duplicateFiles.unshift(notification);
      this.duplicateFilesSubject.next([...this.duplicateFiles]);
    }
  }

  clearDuplicateFiles() {
    this.duplicateFiles = [];
    this.duplicateFilesSubject.next([]);
  }

  removeDuplicateFile(fullPath: string, libraryId: number) {
    this.duplicateFiles = this.duplicateFiles.filter(file =>
      !(file.fullPath === fullPath && file.libraryId === libraryId)
    );
    this.duplicateFilesSubject.next([...this.duplicateFiles]);
  }
}
