import {Component, inject, Input, OnDestroy, signal} from '@angular/core';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {Book} from '../../../../book/model/book.model';
import {SidecarMetadata, SidecarService, SidecarSyncStatus} from '../../../service/sidecar.service';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {Tag} from 'primeng/tag';
import {Tooltip} from 'primeng/tooltip';
import {DatePipe, JsonPipe} from '@angular/common';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-sidecar-viewer',
  standalone: true,
  templateUrl: './sidecar-viewer.component.html',
  styleUrls: ['./sidecar-viewer.component.scss'],
  imports: [Button, Tag, Tooltip, JsonPipe, DatePipe, TranslocoDirective]
})
export class SidecarViewerComponent implements OnDestroy {
  @Input()
  set book(value: Book | null) {
    if (!value) {
      this.currentBookId = null;
      this.sidecarContent = null;
      this.syncStatus = 'NOT_APPLICABLE';
      this.loading.set(false);
      this.error = null;
      return;
    }

    this.currentBookId = value.id;
    this.loadSidecarData(value.id);
  }

  private sidecarService = inject(SidecarService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private destroy$ = new Subject<void>();

  sidecarContent: SidecarMetadata | null = null;
  syncStatus: SidecarSyncStatus = 'NOT_APPLICABLE';
  loading = signal(false);
  exporting = signal(false);
  importing = signal(false);
  currentBookId: number | null = null;
  error: string | null = null;

  loadSidecarData(bookId: number): void {
    this.loading.set(true);
    this.error = null;

    this.sidecarService.getSyncStatus(bookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (response) => {
        this.syncStatus = response.status;

        if (response.status !== 'MISSING' && response.status !== 'NOT_APPLICABLE') {
          this.loadSidecarContent(bookId);
        } else {
          this.sidecarContent = null;
          this.loading.set(false);
        }
      },
      error: (err) => {
        this.syncStatus = 'NOT_APPLICABLE';
        this.sidecarContent = null;
        this.loading.set(false);
        console.error('Failed to get sync status:', err);
      }
    });
  }

  private loadSidecarContent(bookId: number): void {
    this.sidecarService.getSidecarContent(bookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (content) => {
        this.sidecarContent = content;
        this.loading.set(false);
      },
      error: (err) => {
        this.sidecarContent = null;
        this.loading.set(false);
        if (err.status !== 404) {
          console.error('Failed to load sidecar content:', err);
        }
      }
    });
  }

  exportToSidecar(): void {
    if (!this.currentBookId) return;

    this.exporting.set(true);
    this.sidecarService.exportToSidecar(this.currentBookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.sidecar.toast.exportSuccessSummary'),
          detail: this.t.translate('metadata.sidecar.toast.exportSuccessDetail')
        });
        this.loadSidecarData(this.currentBookId!);
        this.exporting.set(false);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.sidecar.toast.exportFailedSummary'),
          detail: this.t.translate('metadata.sidecar.toast.exportFailedDetail')
        });
        this.exporting.set(false);
        console.error('Export failed:', err);
      }
    });
  }

  importFromSidecar(): void {
    if (!this.currentBookId) return;

    this.importing.set(true);
    this.sidecarService.importFromSidecar(this.currentBookId).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.sidecar.toast.importSuccessSummary'),
          detail: this.t.translate('metadata.sidecar.toast.importSuccessDetail')
        });
        this.loadSidecarData(this.currentBookId!);
        this.importing.set(false);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.sidecar.toast.importFailedSummary'),
          detail: this.t.translate('metadata.sidecar.toast.importFailedDetail')
        });
        this.importing.set(false);
        console.error('Import failed:', err);
      }
    });
  }

  getSyncStatusSeverity(): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    switch (this.syncStatus) {
      case 'IN_SYNC':
        return 'success';
      case 'OUTDATED':
        return 'warn';
      case 'CONFLICT':
        return 'danger';
      case 'MISSING':
        return 'secondary';
      default:
        return 'info';
    }
  }

  getSyncStatusLabel(): string {
    switch (this.syncStatus) {
      case 'IN_SYNC':
        return this.t.translate('metadata.sidecar.syncStatusInSync');
      case 'OUTDATED':
        return this.t.translate('metadata.sidecar.syncStatusOutdated');
      case 'CONFLICT':
        return this.t.translate('metadata.sidecar.syncStatusConflict');
      case 'MISSING':
        return this.t.translate('metadata.sidecar.syncStatusMissing');
      case 'NOT_APPLICABLE':
        return this.t.translate('metadata.sidecar.syncStatusNA');
      default:
        return this.t.translate('metadata.sidecar.syncStatusUnknown');
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
