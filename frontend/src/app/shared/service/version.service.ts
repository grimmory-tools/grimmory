import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError, Observable, of} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface AppVersion {
  current: string;
  latest: string;
}

export interface ReleaseNote {
  version: string;
  name: string;
  changelog: string;
  url: string;
  publishedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class VersionService {
  private http = inject(HttpClient);
  private versionUrl = `${API_CONFIG.BASE_URL}/api/v1/version`;

  getVersion(): Observable<AppVersion> {
    return this.http.get<AppVersion>(this.versionUrl, {
      params: new HttpParams().set('_', Date.now().toString()),
    }).pipe(catchError(() => of({current: 'unknown', latest: 'unknown'})));
  }

  getChangelog(): Observable<ReleaseNote[]> {
    return this.http.get<ReleaseNote[]>(`${this.versionUrl}/changelog`);
  }
}
