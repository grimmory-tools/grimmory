import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class FileDownloadService {
  private http = inject(HttpClient);

  downloadFile(url: string, filename: string): void {
    this.http.get(url, {responseType: 'blob', observe: 'response'}).subscribe(response => {
      const blob = response.body;
      if (!blob) return;

      const resolvedFilename = this.extractFilename(response.headers.get('Content-Disposition')) ?? filename;
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = resolvedFilename;
      link.click();
      URL.revokeObjectURL(objectUrl);
    });
  }

  private extractFilename(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;
    const match = contentDisposition.match(/filename\*=UTF-8''([\w%\-.]+)/i);
    return match ? decodeURIComponent(match[1]) : null;
  }
}
