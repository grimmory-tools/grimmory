import {HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, filter, switchMap, take} from 'rxjs/operators';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {AuthService} from './core/service/auth.service';
import {API_CONFIG} from './config/api-config';

export const AuthInterceptorService: HttpInterceptorFn = (req, next: HttpHandlerFn) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const internalToken = authService.getInternalAccessToken();
  const oidcToken = authService.getOidcAccessToken();
  const token = internalToken || oidcToken;

  const isApiRequest = req.url.startsWith(`${API_CONFIG.BASE_URL}/api/`);

  const authReq = (token && isApiRequest) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 || error.status === 403) {
        return handle401Error(authService, authReq, next, router, !!internalToken);
      }
      return throwError(() => error);
    })
  );
};

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

function handle401Error(authService: AuthService, request: HttpRequest<any>, next: HttpHandlerFn, router: Router, isInternal: boolean): Observable<any> {
  if (!isRefreshing && isInternal) {
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
        forceLogout(authService, router);
        return throwError(() => err);
      })
    );
  }

  if (isRefreshing && isInternal) {
    return refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap(token =>
        next(request.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        }))
      )
    );
  }

  forceLogout(authService, router, isInternal ? 'Session expired, please log in again.' : 'OIDC token expired, please log in again.');
  return throwError(() => new Error('Authentication failed, please log in.'));
}

function forceLogout(authService: AuthService, router: Router, message?: string): void {
  authService.logout();
  router.navigate(['/login']);
  if (message) console.warn(message);
}
