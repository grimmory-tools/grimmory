import {inject, Service} from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

@Service()
export class SetupRedirectGuard implements CanActivate {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/setup`;

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
