import {inject, Injectable, Injector} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {RxStompService} from '../../shared/websocket/rx-stomp.service';
import {API_CONFIG} from '../../config/api-config';

@Injectable({
  providedIn: 'root',
})
export class AuthService {

  private apiUrl = `${API_CONFIG.BASE_URL}/api/v1/auth`;
  private rxStompService?: RxStompService;

  private http = inject(HttpClient);
  private injector = inject(Injector);

  login(credentials: { username: string; password: string }): Observable<any> {
    return this.http.post<{ accessToken: string; refreshToken: string }>(`${this.apiUrl}/login`, credentials).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveTokens(response.accessToken, response.refreshToken);
          this.getRxStompService().activate();
        }
      })
    );
  }

  remoteLogin(): Observable<any> {
    return this.http.get<{ accessToken: string; refreshToken: string }>(`${this.apiUrl}/remote`).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveTokens(response.accessToken, response.refreshToken);
          this.getRxStompService().activate();
        }
      })
    );
  }

  refreshToken(): Observable<{ accessToken: string; refreshToken: string }> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<{ accessToken: string; refreshToken: string }>(`${this.apiUrl}/refresh`, {refreshToken}).pipe(
      tap((response) => {
        if (response.accessToken && response.refreshToken) {
          this.saveTokens(response.accessToken, response.refreshToken);
        }
      })
    );
  }

  saveTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
  }

  getToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('refreshToken');
  }

  logout(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    this.getRxStompService().deactivate();
  }

  private getRxStompService(): RxStompService {
    if (!this.rxStompService) {
      this.rxStompService = this.injector.get(RxStompService);
    }
    return this.rxStompService;
  }
}
