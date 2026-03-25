import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

export interface AuditLog {
  id: number;
  userId: number | null;
  username: string;
  action: string;
  entityType: string | null;
  entityId: number | null;
  description: string;
  ipAddress: string | null;
  countryCode: string | null;
  createdAt: string;
}

export interface PageableResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };
}

@Injectable({
  providedIn: 'root'
})
export class AuditLogService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/audit-logs`;

  getAuditLogs(page: number = 0, size: number = 25, action?: string, username?: string, from?: string, to?: string): Observable<PageableResponse<AuditLog>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (action) {
      params = params.set('action', action);
    }
    if (username) {
      params = params.set('username', username);
    }
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }

    return this.http.get<PageableResponse<AuditLog>>(this.url, {params});
  }

  getDistinctUsernames(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/usernames`);
  }
}
