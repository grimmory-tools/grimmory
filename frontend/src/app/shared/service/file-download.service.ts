import {inject, Injectable} from '@angular/core';
import {AuthService} from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class FileDownloadService {
  private authService = inject(AuthService);

  downloadFile(url: string, filename: string): void {
    this.authService.ensureAccessToken().subscribe({
      next: token => this.triggerDownload(url, filename, token),
      error: () => this.triggerDownload(url, filename, this.authService.getInternalAccessToken())
    });
  }

  private triggerDownload(url: string, filename: string, token: string | null): void {
    const href = token
      ? `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
      : url;
    const link = document.createElement('a');
    link.href = href;
    link.download = filename;
    link.click();
  }
}
