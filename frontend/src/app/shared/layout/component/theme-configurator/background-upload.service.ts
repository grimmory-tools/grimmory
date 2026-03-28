import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, map, tap} from 'rxjs';
import {API_CONFIG} from '../../../../core/config/api-config';

@Injectable({providedIn: 'root'})
export class BackgroundUploadService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/background`;
  private readonly http = inject(HttpClient);

  uploadFile(file: File): Observable<string> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<{ url: string }>(`${this.baseUrl}/upload`, formData).pipe(
      tap(response => console.log('File upload response:', response)),
      map(resp => resp?.url)
    );
  }

  uploadUrl(url: string): Observable<string> {
    return this.http.post<{ url: string }>(`${this.baseUrl}/url`, {url}).pipe(
      tap(response => console.log('URL upload response:', response)),
      map(resp => resp?.url)
    );
  }
}
