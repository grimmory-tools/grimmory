import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';

export interface KoboSyncSettings {
  token: string;
  syncEnabled: boolean;
  progressMarkAsReadingThreshold?: number;
  progressMarkAsFinishedThreshold?: number;
  autoAddToShelf: boolean;
  hardcoverApiKey?: string;
  hardcoverSyncEnabled?: boolean;
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
    return this.http.put<KoboSyncSettings>(`${this.baseUrl}/token`, null);
  }

  updateSettings(settings: KoboSyncSettings): Observable<KoboSyncSettings> {
    return this.http.put<KoboSyncSettings>(`${this.baseUrl}`, settings);
  }
}
