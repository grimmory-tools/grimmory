import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';

export interface KoboSyncSettings {
  token: string;
  syncEnabled: boolean;
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
}
