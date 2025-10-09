import {Injectable, inject} from '@angular/core';
import {HttpClient, HttpEvent, HttpEventType, HttpResponse} from '@angular/common/http';
import {MessageService} from 'primeng/api';
import {DownloadProgressService} from './download-progress.service';
import {Observable, Subject, throwError} from 'rxjs';
import {catchError, finalize, takeUntil, tap} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class FileDownloadService {
  private http = inject(HttpClient);
  private downloadProgressService = inject(DownloadProgressService);
  private messageService = inject(MessageService);

  downloadFile(url: string, defaultFilename: string): void {
    const cancelSubject = new Subject<void>();

    this.initiateDownload(url)
      .pipe(
        takeUntil(cancelSubject),
        tap(event => this.handleDownloadProgress(event, defaultFilename, cancelSubject)),
        finalize(() => this.downloadProgressService.completeDownload()),
        catchError(error => {
          this.handleDownloadError(error);
          return throwError(() => error);
        })
      )
      .subscribe();
  }

  private initiateDownload(url: string): Observable<HttpEvent<Blob>> {
    return this.http.get(url, {
      responseType: 'blob',
      observe: 'events',
      reportProgress: true
    });
  }

  private handleDownloadProgress(
    event: HttpEvent<Blob>,
    defaultFilename: string,
    cancelSubject: Subject<void>
  ): void {
    if (event.type === HttpEventType.Response) {
      this.handleDownloadComplete(event, defaultFilename);
    } else if (event.type === HttpEventType.DownloadProgress) {
      this.updateProgress(event, defaultFilename, cancelSubject);
    }
  }

  private updateProgress(
    event: any,
    defaultFilename: string,
    cancelSubject: Subject<void>
  ): void {
    if (event.total) {
      if (!this.downloadProgressService.isDownloadInProgress()) {
        this.downloadProgressService.startDownload(defaultFilename, cancelSubject);
      }
      this.downloadProgressService.updateProgress(event.loaded, event.total);
    }
  }

  private handleDownloadComplete(response: HttpResponse<Blob>, defaultFilename: string): void {
    const filename = this.extractFilenameFromResponse(response, defaultFilename);
    const blob = response.body;

    if (!blob) {
      throw new Error('No file content received');
    }

    this.triggerBrowserDownload(blob, filename);
    this.showSuccessMessage(filename);
  }

  private extractFilenameFromResponse(response: HttpResponse<Blob>, defaultFilename: string): string {
    const contentDisposition = response.headers.get('Content-Disposition');
    return contentDisposition
      ? contentDisposition.match(/filename="(.+?)"/)?.[1] || defaultFilename
      : defaultFilename;
  }

  private triggerBrowserDownload(blob: Blob, filename: string): void {
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename;
    link.style.display = 'none';

    document.body.appendChild(link);
    link.click();

    setTimeout(() => {
      document.body.removeChild(link);
      window.URL.revokeObjectURL(objectUrl);
    }, 100);
  }

  private handleDownloadError(error: any): void {
    if (error?.name !== 'AbortError') {
      this.messageService.add({
        severity: 'error',
        summary: 'Download Failed',
        detail: 'An error occurred while downloading the file. Please try again.',
        life: 5000
      });
    }
  }

  private showSuccessMessage(filename: string): void {
    this.messageService.add({
      severity: 'success',
      summary: 'Download Complete',
      detail: `${filename} has been downloaded successfully.`,
      life: 3000
    });
  }
}
