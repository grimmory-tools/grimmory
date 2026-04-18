import {HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {catchError, filter, switchMap, take} from 'rxjs/operators';
import {BehaviorSubject, Observable, throwError, defer} from 'rxjs';
import {AuthService} from '../../shared/service/auth.service';
import {API_CONFIG} from '../config/api-config';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

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
    if (!isRefreshing) {
      isRefreshing = true;
      refreshTokenSubject.next(null);

      return authService.internalRefreshToken().pipe(
        switchMap(response => {
          isRefreshing = false;
          const { accessToken, refreshToken } = response;
          if (accessToken && refreshToken) {
            authService.saveInternalTokens(accessToken, refreshToken);
            refreshTokenSubject.next(accessToken);
          }
          return next(request.clone({
            setHeaders: { Authorization: `Bearer ${accessToken}` }
          }));
        }),
        catchError(err => {
          isRefreshing = false;
          forceLogout(authService);
          return throwError(() => err);
        })
      );
    }

    return refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap(token =>
        next(request.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        }))
      )
    );
  });
}

function forceLogout(authService: AuthService): void {
  authService.logout();
}
