import {HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, filter, switchMap, take} from 'rxjs/operators';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {AuthService} from '../../shared/service/auth.service';
import {API_CONFIG} from '../config/api-config';

export const AuthInterceptorService: HttpInterceptorFn = (req, next: HttpHandlerFn) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getInternalAccessToken();
  const isApiRequest = req.url.startsWith(`${API_CONFIG.BASE_URL}/api/`);

  const authReq = (token && isApiRequest) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        return handle401Error(authService, authReq, next, router);
      }
      return throwError(() => error);
    })
  );
};

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

function handle401Error(authService: AuthService, request: HttpRequest<unknown>, next: HttpHandlerFn, router: Router): Observable<HttpEvent<unknown>> {
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
        forceLogout(authService, router);
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
}

function forceLogout(authService: AuthService, router: Router): void {
  authService.logout();
}
