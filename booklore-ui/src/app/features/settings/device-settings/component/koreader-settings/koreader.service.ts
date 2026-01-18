import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';


export interface KoreaderUser {
  username: string;
  password: string;
  syncEnabled: boolean;
  syncWithBookloreReader: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class KoreaderService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/koreader-users`;

  private http = inject(HttpClient);

  createUser(username: string, password: string): Observable<KoreaderUser> {
    const payload: unknown = {username, password};
    return this.http.put<KoreaderUser>(`${this.url}/me`, payload);
  }

  getUser(): Observable<KoreaderUser> {
    return this.http.get<KoreaderUser>(`${this.url}/me`);
  }

  toggleSync(enabled: boolean): Observable<void> {
    return this.http.patch<void>(`${this.url}/me/sync`, null, {
      params: {enabled: enabled.toString()}
    });
  }

  toggleSyncProgressWithBookloreReader(enabled: boolean): Observable<void> {
    return this.http.patch<void>(`${this.url}/me/sync-progress-with-booklore`, null, {
      params: {enabled: enabled.toString()}
    });
  }
}
