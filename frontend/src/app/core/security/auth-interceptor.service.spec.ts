import {HttpErrorResponse, HttpHandlerFn, HttpRequest, HttpResponse} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {Observable, Subject, firstValueFrom, of, throwError} from 'rxjs';

import {API_CONFIG} from '../config/api-config';
import {AuthService} from '../../shared/service/auth.service';
import {AuthInterceptorService} from './auth-interceptor.service';

describe('AuthInterceptorService', () => {
  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
    internalRefreshToken: vi.fn<() => Observable<{accessToken: string; refreshToken: string}>>(),
    saveInternalTokens: vi.fn<(accessToken: string, refreshToken: string) => void>(),
    logout: vi.fn<() => void>(),
  };

  const apiUrl = `${API_CONFIG.BASE_URL}/api/v1`;
  let interceptor: (request: HttpRequest<unknown>, next: HttpHandlerFn) => Observable<unknown>;

  beforeEach(() => {
    vi.restoreAllMocks();
    authService.getInternalAccessToken.mockReset();
    authService.internalRefreshToken.mockReset();
    authService.saveInternalTokens.mockReset();
    authService.logout.mockReset();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: authService},
      ]
    });

    interceptor = (request, next) => TestBed.runInInjectionContext(
      () => AuthInterceptorService(request, next)
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('adds the bearer token to API requests', async () => {
    authService.getInternalAccessToken.mockReturnValue('token-123');
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({status: 200, body: request.headers.get('Authorization')})));

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(next).toHaveBeenCalledOnce();
    expect((response as HttpResponse<string>).body).toBe('Bearer token-123');
  });

  it('does not add auth headers to non-api requests', async () => {
    authService.getInternalAccessToken.mockReturnValue('token-123');
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({status: 200, body: request.headers.has('Authorization')})));

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${API_CONFIG.BASE_URL}/assets/logo.svg`),
      next
    ));

    expect((response as HttpResponse<boolean>).body).toBe(false);
  });

  it('forwards api requests unchanged when no token is available', async () => {
    authService.getInternalAccessToken.mockReturnValue(null);
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({status: 200, body: request.headers.has('Authorization')})));

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(next).toHaveBeenCalledOnce();
    expect((response as HttpResponse<boolean>).body).toBe(false);
  });

  it('retries a 401 request after a successful refresh', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.internalRefreshToken.mockReturnValue(of({accessToken: 'fresh-token', refreshToken: 'refresh-token'}));

    const next = vi.fn((request: HttpRequest<unknown>) => {
      const authHeader = request.headers.get('Authorization');
      if (authHeader === 'Bearer fresh-token') {
        return of(new HttpResponse({status: 200, body: authHeader}));
      }
      return throwError(() => new HttpErrorResponse({status: 401}));
    });

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(authService.internalRefreshToken).toHaveBeenCalledOnce();
    expect(authService.saveInternalTokens).toHaveBeenCalledWith('fresh-token', 'refresh-token');
    expect((response as HttpResponse<string>).body).toBe('Bearer fresh-token');
  });

  it('retries a 401 request without persisting tokens when the refresh response is incomplete', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.internalRefreshToken.mockReturnValue(of({accessToken: 'fresh-token', refreshToken: ''}));

    const next = vi.fn((request: HttpRequest<unknown>) => {
      if (request.headers.get('Authorization') === 'Bearer fresh-token') {
        return of(new HttpResponse({status: 200, body: request.headers.get('Authorization')}));
      }
      return throwError(() => new HttpErrorResponse({status: 401}));
    });

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(authService.saveInternalTokens).not.toHaveBeenCalled();
    expect((response as HttpResponse<string>).body).toBe('Bearer fresh-token');
  });

  it('logs out when the refresh request fails', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.internalRefreshToken.mockReturnValue(
      throwError(() => new HttpErrorResponse({status: 500}))
    );

    const next = vi.fn(() => throwError(() => new HttpErrorResponse({status: 401})));

    await expect(firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ))).rejects.toBeInstanceOf(HttpErrorResponse);

    expect(authService.logout).toHaveBeenCalledOnce();
  });

  it('rethrows non-401 errors without logging out', async () => {
    authService.getInternalAccessToken.mockReturnValue('token-123');
    const next = vi.fn(() => throwError(() => new HttpErrorResponse({status: 500})));

    await expect(firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ))).rejects.toBeInstanceOf(HttpErrorResponse);

    expect(authService.internalRefreshToken).not.toHaveBeenCalled();
    expect(authService.logout).not.toHaveBeenCalled();
  });

  it('queues concurrent 401s behind a single refresh operation', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    const refreshSubject = new Subject<{accessToken: string; refreshToken: string}>();
    authService.internalRefreshToken.mockReturnValue(refreshSubject.asObservable());

    const next = vi.fn((request: HttpRequest<unknown>) => {
      const authHeader = request.headers.get('Authorization');
      if (authHeader === 'Bearer refreshed-token') {
        return of(new HttpResponse({status: 200, body: authHeader}));
      }
      return throwError(() => new HttpErrorResponse({status: 401}));
    });

    const firstRequest = firstValueFrom(interceptor(new HttpRequest('GET', `${apiUrl}/books`), next));
    const secondRequest = firstValueFrom(interceptor(new HttpRequest('GET', `${apiUrl}/libraries`), next));

    refreshSubject.next({accessToken: 'refreshed-token', refreshToken: 'refresh-token'});
    refreshSubject.complete();

    const [firstResponse, secondResponse] = await Promise.all([firstRequest, secondRequest]);

    expect(authService.internalRefreshToken).toHaveBeenCalledOnce();
    expect((firstResponse as HttpResponse<string>).body).toBe('Bearer refreshed-token');
    expect((secondResponse as HttpResponse<string>).body).toBe('Bearer refreshed-token');
  });

  describe('isExcludedAuthRequest', () => {
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({status: 200, body: request.headers.has('Authorization')})));

    it('excludes /api/v1/auth/login', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('POST', `${API_CONFIG.BASE_URL}/api/v1/auth/login`, null), next));
      expect((response as HttpResponse<boolean>).body).toBe(false);
    });

    it('excludes /api/v1/auth/refresh', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('POST', `${API_CONFIG.BASE_URL}/api/v1/auth/refresh`, null), next));
      expect((response as HttpResponse<boolean>).body).toBe(false);
    });

    it('excludes /api/v1/auth/remote', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('GET', `${API_CONFIG.BASE_URL}/api/v1/auth/remote`), next));
      expect((response as HttpResponse<boolean>).body).toBe(false);
    });

    it('excludes /api/v1/auth/oidc/state', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('GET', `${API_CONFIG.BASE_URL}/api/v1/auth/oidc/state`), next));
      expect((response as HttpResponse<boolean>).body).toBe(false);
    });

    it('excludes /api/v1/auth/oidc/callback', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('POST', `${API_CONFIG.BASE_URL}/api/v1/auth/oidc/callback`, null), next));
      expect((response as HttpResponse<boolean>).body).toBe(false);
    });

    it('does NOT exclude /api/v1/auth/login-history (exact match test)', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('GET', `${API_CONFIG.BASE_URL}/api/v1/auth/login-history`), next));
      expect((response as HttpResponse<boolean>).body).toBe(true);
    });

    it('does NOT exclude /api/v1/auth/login?query=1 (exact match test)', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('GET', `${API_CONFIG.BASE_URL}/api/v1/auth/login?query=1`), next));
      expect((response as HttpResponse<boolean>).body).toBe(false); // login with query is still login
    });

    it('does NOT exclude /api/v1/auth/logout', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('POST', `${API_CONFIG.BASE_URL}/api/v1/auth/logout`, null), next));
      expect((response as HttpResponse<boolean>).body).toBe(true);
    });
  });
});
