import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';

export interface KoboSyncSettings {
  token: string;
  syncEnabled: boolean;
  progressMarkAsReadingThreshold?: number;
  progressMarkAsFinishedThreshold?: number;
}

@Injectable({
  providedIn: 'root'
})
export class KoboService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/kobo-settings`;
  private readonly http = inject(HttpClient);

  getUser(): Observable<KoboSyncSettings> {
    return this.http.get<KoboSyncSettings>(`${this.baseUrl}`);
  }

  createOrUpdateToken(): Observable<KoboSyncSettings> {
    return this.http.put<KoboSyncSettings>(`${this.baseUrl}`, null);
  }

  toggleSync(enabled: boolean): Observable<void> {
    const params = new HttpParams().set('enabled', enabled.toString());
    return this.http.put<void>(`${this.baseUrl}/sync`, null, { params });
  }

  updateProgressThresholds(readingThreshold?: number, finishedThreshold?: number): Observable<KoboSyncSettings> {
    let params = new HttpParams();
    if (readingThreshold !== undefined && readingThreshold !== null) {
      params = params.set('readingThreshold', readingThreshold.toString());
    }
    if (finishedThreshold !== undefined && finishedThreshold !== null) {
      params = params.set('finishedThreshold', finishedThreshold.toString());
    }
    return this.http.put<KoboSyncSettings>(`${this.baseUrl}/progress-thresholds`, null, { params });
  }
}
