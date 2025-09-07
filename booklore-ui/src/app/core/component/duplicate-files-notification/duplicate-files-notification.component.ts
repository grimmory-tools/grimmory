import {Component, inject, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {TagModule} from 'primeng/tag';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {DuplicateFileService} from '../../../shared/websocket/duplicate-file.service';
import {DuplicateFileNotification} from '../../../shared/websocket/model/duplicate-file-notification.model';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DuplicateFilesDialogComponent} from './duplicate-files-dialog.component';

@Component({
  selector: 'app-duplicate-files-notification',
  standalone: true,
  imports: [CommonModule, DialogModule, ButtonModule, TableModule, TagModule],
  templateUrl: './duplicate-files-notification.component.html',
  styleUrls: ['./duplicate-files-notification.component.scss'],
  providers: [DialogService]
})
export class DuplicateFilesNotificationComponent implements OnDestroy {
  displayDialog = false;

  private duplicateFileService = inject(DuplicateFileService);
  private ref: DynamicDialogRef | undefined;

  duplicateFiles$ = this.duplicateFileService.duplicateFiles$;
  duplicateFilesCount$: Observable<number> = this.duplicateFiles$.pipe(
    map(files => files?.length || 0)
  );

  constructor(
    private dialogService: DialogService
  ) {
  }

  openDialog() {
    this.ref = this.dialogService.open(DuplicateFilesDialogComponent, {
      header: 'Duplicate Files Detected',
      width: '80dvw',
      height: '75dvh',
      contentStyle: {overflow: 'hidden'},
      maximizable: true,
      modal: true,
      data: {
        duplicateFiles$: this.duplicateFiles$
      }
    });

    this.ref.onClose.subscribe((result: any) => {
      if (result) {
        // Handle any result from dialog if needed
      }
    });
  }

  closeDialog() {
    this.displayDialog = false;
  }

  clearAllDuplicates() {
    this.duplicateFileService.clearDuplicateFiles();
    this.closeDialog();
  }

  removeDuplicate(file: DuplicateFileNotification) {
    this.duplicateFileService.removeDuplicateFile(file.fullPath, file.libraryId);
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString();
  }

  ngOnDestroy() {
    if (this.ref) {
      this.ref.close();
    }
  }
}
