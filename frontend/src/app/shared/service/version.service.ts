import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
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
  private versionUrl = `${API_CONFIG.BASE_URL}/api/v1/version`;

  constructor(private http: HttpClient) {}

  getVersion(): Observable<AppVersion> {
    return this.http.get<AppVersion>(this.versionUrl);
  }

  getChangelog(): Observable<ReleaseNote[]> {
    return this.http.get<ReleaseNote[]>(`${this.versionUrl}/changelog`);
  }
}
