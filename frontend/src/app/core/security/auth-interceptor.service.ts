import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap } from 'rxjs/operators';
import { Observable, throwError, defer } from 'rxjs';
import { AuthService } from '../../shared/service/auth.service';
import { API_CONFIG } from '../config/api-config';

export const AuthInterceptorService: HttpInterceptorFn = (req, next: HttpHandlerFn) => {
  const authService = inject(AuthService);

  const token = authService.getInternalAccessToken();
  const isApiRequest = req.url.startsWith(`${API_CONFIG.BASE_URL}/api/`);
  const authExcludePaths = [
    '/api/v1/auth/login',
    '/api/v1/auth/refresh',
    '/api/v1/auth/remote',
    '/api/v1/auth/oidc/state',
    '/api/v1/auth/oidc/callback',
    '/api/v1/auth/oidc/redirect',
    '/api/v1/auth/oidc/mobile/callback',
    '/api/v1/auth/oidc/backchannel-logout',
    '/api/v1/public-settings',
  ];
  const urlPath = req.url.split('?')[0];
  const isAuthRequest = authExcludePaths.some(path => urlPath === `${API_CONFIG.BASE_URL}${path}`);
  const hasAuthHeader = req.headers.has('Authorization');

  const authReq = (token && isApiRequest && !isAuthRequest && !hasAuthHeader) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isAuthRequest) {
        return handle401Error(authService, authReq, next);
      }
      return throwError(() => error);
    })
  );
};

function handle401Error(authService: AuthService, request: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> {
  return defer(() => {
    return authService.ensureAccessToken({forceRefresh: true}).pipe(
      catchError(err => {
        forceLogout(authService);
        return throwError(() => err);
      }),
      switchMap(accessToken =>
        next(request.clone({
          setHeaders: { Authorization: `Bearer ${accessToken}` }
        }))
      )
    );
  });
}

function forceLogout(authService: AuthService): void {
  authService.logout();
}
