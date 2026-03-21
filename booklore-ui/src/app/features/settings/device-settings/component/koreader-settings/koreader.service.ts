import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {catchError} from 'rxjs/operators';


export interface KoreaderUser {
  username: string;
  password: string;
  syncEnabled: boolean;
  syncWithGrimmoryReader?: boolean;
  // TODO(grimmory-cleanup): Remove once the backend no longer returns the legacy Booklore KOReader sync field.
  syncWithBookloreReader?: boolean;
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

  toggleSyncProgressWithGrimmoryReader(enabled: boolean): Observable<void> {
    return this.http.patch<void>(`${this.url}/me/sync-progress-with-grimmory`, null, {
      params: {enabled: enabled.toString()}
    }).pipe(
      // TODO(grimmory-cleanup): Remove the legacy endpoint fallback after all supported backends expose the Grimmory route.
      catchError(() => this.http.patch<void>(`${this.url}/me/sync-progress-with-booklore`, null, {
        params: {enabled: enabled.toString()}
      }))
    );
  }
}
