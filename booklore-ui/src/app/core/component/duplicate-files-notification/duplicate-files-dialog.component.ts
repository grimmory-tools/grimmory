import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { PaginatorModule } from 'primeng/paginator';
import { Observable, map } from 'rxjs';
import {DuplicateFileNotification} from '../../../shared/websocket/model/duplicate-file-notification.model';
import {DuplicateFileService} from '../../../shared/websocket/duplicate-file.service';

@Component({
  selector: 'app-duplicate-files-dialog',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TooltipModule, PaginatorModule],
  templateUrl: './duplicate-files-dialog.component.html',
  styleUrls: ['./duplicate-files-dialog.component.scss']
})
export class DuplicateFilesDialogComponent implements OnInit {
  duplicateFiles$: Observable<{ hash: string; files: DuplicateFileNotification[] }[]>;
  totalRecords = 0;
  first = 0;
  rows = 15;

  constructor(
    public ref: DynamicDialogRef,
    public config: DynamicDialogConfig,
    private duplicateFileService: DuplicateFileService
  ) {
    this.duplicateFiles$ = this.config.data.duplicateFiles$.pipe(
      map((files: DuplicateFileNotification[] | null) => {
        if (!files) return [];

        // Group files by hash
        const groupedFiles = files.reduce((acc, file) => {
          if (!acc[file.hash]) {
            acc[file.hash] = [];
          }
          acc[file.hash].push(file);
          return acc;
        }, {} as { [hash: string]: DuplicateFileNotification[] });

        const groups = Object.entries(groupedFiles).map(([hash, files]) => ({
          hash,
          files: files.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
        }));

        this.totalRecords = groups.length;
        return groups.slice(this.first, this.first + this.rows);
      })
    );
  }

  ngOnInit() {}

  onPageChange(event: any) {
    this.first = event.first;
    this.rows = event.rows;
    this.duplicateFiles$ = this.config.data.duplicateFiles$.pipe(
      map((files: DuplicateFileNotification[] | null) => {
        if (!files) return [];

        const groupedFiles = files.reduce((acc, file) => {
          if (!acc[file.hash]) {
            acc[file.hash] = [];
          }
          acc[file.hash].push(file);
          return acc;
        }, {} as { [hash: string]: DuplicateFileNotification[] });

        const groups = Object.entries(groupedFiles).map(([hash, files]) => ({
          hash,
          files: files.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
        }));

        this.totalRecords = groups.length;
        return groups.slice(this.first, this.first + this.rows);
      })
    );
  }

  acknowledgeAndClose() {
    this.duplicateFileService.clearDuplicateFiles();
    this.ref.close();
  }
}
