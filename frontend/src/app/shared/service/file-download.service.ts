import {inject, Injectable} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {AuthService} from './auth.service';
import {API_CONFIG} from '../../core/config/api-config';

@Injectable({
  providedIn: 'root'
})
export class FileDownloadService {
  private readonly document = inject(DOCUMENT);
  private authService = inject(AuthService);

  downloadFile(url: string, filename: string): void {
    this.authService.ensureAccessToken().subscribe({
      next: token => this.triggerDownload(url, filename, token),
      error: () => undefined
    });
  }

  private triggerDownload(url: string, filename: string, token: string): void {
    const link = this.document.createElement('a');
    link.href = this.appendToken(url, token);
    link.download = filename;
    link.click();
  }

  private appendToken(url: string, token: string): string {
    if (!url.startsWith(`${API_CONFIG.BASE_URL}/api/`)) {
      return url;
    }
    return `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`;
  }
}
