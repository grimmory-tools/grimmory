import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
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

@Service()
export class KoboService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/kobo-settings`;

  getUser(): Observable<KoboSyncSettings> {
    return this.http.get<KoboSyncSettings>((this.baseUrl));
  }

  createOrUpdateToken(): Observable<KoboSyncSettings> {
    return this.http.put<KoboSyncSettings>(`${this.baseUrl}/token`, null);
  }

  updateSettings(settings: KoboSyncSettings): Observable<KoboSyncSettings> {
    return this.http.put<KoboSyncSettings>((this.baseUrl), settings);
  }
}
