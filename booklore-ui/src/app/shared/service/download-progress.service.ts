import {Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs';

export interface DownloadProgress {
  visible: boolean;
  filename: string;
  progress: number;
  loaded: number;
  total: number;
  cancelSubject?: Subject<void>;
}

@Injectable({
  providedIn: 'root'
})
export class DownloadProgressService {
  private downloadProgressSubject = new BehaviorSubject<DownloadProgress>({
    visible: false,
    filename: '',
    progress: 0,
    loaded: 0,
    total: 0
  });

  private lastUpdateTime = 0;
  private updateThrottleMs = 100;
  private pendingUpdate: DownloadProgress | null = null;
  private throttleTimer: any = null;

  downloadProgress$ = this.downloadProgressSubject.asObservable();

  isDownloadInProgress(): boolean {
    return this.downloadProgressSubject.value.visible;
  }

  startDownload(filename: string, cancelSubject: Subject<void>): void {
    this.lastUpdateTime = 0;
    this.pendingUpdate = null;
    if (this.throttleTimer) {
      clearTimeout(this.throttleTimer);
      this.throttleTimer = null;
    }

    this.downloadProgressSubject.next({
      visible: true,
      filename,
      progress: 0,
      loaded: 0,
      total: 0,
      cancelSubject
    });
  }

  updateProgress(loaded: number, total: number): void {
    const current = this.downloadProgressSubject.value;
    const progress = total > 0 ? Math.min(100, Math.round((loaded / total) * 100)) : 0;
    const now = Date.now();

    const newProgress: DownloadProgress = {
      visible: current.visible,
      filename: current.filename,
      progress,
      loaded,
      total,
      cancelSubject: current.cancelSubject
    };

    if (progress === 100) {
      if (this.throttleTimer) {
        clearTimeout(this.throttleTimer);
        this.throttleTimer = null;
      }
      this.downloadProgressSubject.next(newProgress);
      this.lastUpdateTime = now;
      this.pendingUpdate = null;
      return;
    }

    if (now - this.lastUpdateTime >= this.updateThrottleMs) {
      this.downloadProgressSubject.next(newProgress);
      this.lastUpdateTime = now;
      this.pendingUpdate = null;

      if (this.throttleTimer) {
        clearTimeout(this.throttleTimer);
        this.throttleTimer = null;
      }
    } else {
      this.pendingUpdate = newProgress;

      if (!this.throttleTimer) {
        const remainingTime = this.updateThrottleMs - (now - this.lastUpdateTime);
        this.throttleTimer = setTimeout(() => {
          if (this.pendingUpdate) {
            this.downloadProgressSubject.next(this.pendingUpdate);
            this.lastUpdateTime = Date.now();
            this.pendingUpdate = null;
          }
          this.throttleTimer = null;
        }, remainingTime);
      }
    }
  }

  completeDownload(): void {
    if (this.throttleTimer) {
      clearTimeout(this.throttleTimer);
      this.throttleTimer = null;
    }
    this.pendingUpdate = null;

    this.downloadProgressSubject.next({
      visible: false,
      filename: '',
      progress: 0,
      loaded: 0,
      total: 0
    });
  }

  cancelDownload(): void {
    const current = this.downloadProgressSubject.value;
    current.cancelSubject?.next();
    current.cancelSubject?.complete();
    this.completeDownload();
  }
}
