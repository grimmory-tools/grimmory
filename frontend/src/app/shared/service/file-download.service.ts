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
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([\w%\-.]+)/i);
    if (utf8Match) {
      try {
        return decodeURIComponent(utf8Match[1]);
      } catch {
        // Do nothing in this case and fall back to the ascii filename.
      }
    }
    const match = contentDisposition.match(/filename=(?:"([^"]+)"|([^"; ]+))/i)
    if (match) {
      return match[1] ?? match[2];
    }
    return null;
  }
}
