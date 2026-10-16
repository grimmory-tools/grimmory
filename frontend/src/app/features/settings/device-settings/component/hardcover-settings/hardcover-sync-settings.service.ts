import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';

export interface HardcoverSyncSettings {
  hardcoverApiKey?: string;
  hardcoverSyncEnabled?: boolean;
}

@Service()
export class HardcoverSyncSettingsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/hardcover-sync-settings`;

  getSettings(): Observable<HardcoverSyncSettings> {
    return this.http.get<HardcoverSyncSettings>(this.baseUrl);
  }

  updateSettings(settings: HardcoverSyncSettings): Observable<HardcoverSyncSettings> {
    return this.http.put<HardcoverSyncSettings>(this.baseUrl, settings);
  }
}
