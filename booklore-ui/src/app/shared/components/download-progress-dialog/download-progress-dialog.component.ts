import {Component, inject, ChangeDetectionStrategy, ChangeDetectorRef, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {Subscription} from 'rxjs';
import {DownloadProgress, DownloadProgressService} from '../../service/download-progress.service';

@Component({
  selector: 'app-download-progress-dialog',
  standalone: true,
  imports: [CommonModule, DialogModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './download-progress-dialog.component.html',
  styleUrl: './download-progress-dialog.component.scss'
})
export class DownloadProgressDialogComponent implements OnDestroy {
  private downloadProgressService = inject(DownloadProgressService);
  private cdr = inject(ChangeDetectorRef);
  private subscription: Subscription;

  currentProgress: DownloadProgress = {
    visible: false,
    filename: '',
    progress: 0,
    loaded: 0,
    total: 0
  };

  constructor() {
    this.subscription = this.downloadProgressService.downloadProgress$.subscribe(progress => {
      this.currentProgress = progress;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  cancelDownload(): void {
    this.downloadProgressService.cancelDownload();
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }
}
