import {inject, Injectable} from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

@Injectable({
  providedIn: 'root',
})
export class SetupRedirectGuard implements CanActivate {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/setup`;

  private http = inject(HttpClient);
  private router = inject(Router);

  canActivate(): Observable<boolean> {
    return this.http.get<{ data: boolean }>(`${this.url}/status`).pipe(
      map(res => {
        if (!res.data) {
          this.router.navigate(['/setup']);
        } else {
          this.router.navigate(['/dashboard']);
        }
        return false;
      })
    );
  }
}
