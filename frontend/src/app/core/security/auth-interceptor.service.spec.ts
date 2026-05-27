import { HttpErrorResponse, HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Observable, Subject, firstValueFrom, of, throwError } from 'rxjs';

import { API_CONFIG } from '../config/api-config';
import { AuthService } from '../../shared/service/auth.service';
import { AuthInterceptorService } from './auth-interceptor.service';

describe('AuthInterceptorService', () => {
  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
    ensureAccessToken: vi.fn<(options?: {forceRefresh?: boolean}) => Observable<string>>(),
    logout: vi.fn<() => void>(),
  };

  const apiUrl = `${API_CONFIG.BASE_URL}/api/v1`;
  let interceptor: (request: HttpRequest<unknown>, next: HttpHandlerFn) => Observable<unknown>;

  beforeEach(() => {
    vi.restoreAllMocks();
    authService.getInternalAccessToken.mockReset();
    authService.ensureAccessToken.mockReset();
    authService.logout.mockReset();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
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
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({ status: 200, body: request.headers.get('Authorization') })));

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(next).toHaveBeenCalledOnce();
    expect((response as HttpResponse<string>).body).toBe('Bearer token-123');
  });

  it('does not add auth headers to non-api requests', async () => {
    authService.getInternalAccessToken.mockReturnValue('token-123');
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({ status: 200, body: request.headers.has('Authorization') })));

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${API_CONFIG.BASE_URL}/assets/logo.svg`),
      next
    ));

    expect((response as HttpResponse<boolean>).body).toBe(false);
  });

  it('forwards api requests unchanged when no token is available', async () => {
    authService.getInternalAccessToken.mockReturnValue(null);
    const next = vi.fn((request: HttpRequest<unknown>) => of(new HttpResponse({ status: 200, body: request.headers.has('Authorization') })));

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(next).toHaveBeenCalledOnce();
    expect((response as HttpResponse<boolean>).body).toBe(false);
  });

  it('retries a 401 request after a successful refresh', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.ensureAccessToken.mockReturnValue(of('fresh-token'));

    const next = vi.fn((request: HttpRequest<unknown>) => {
      const authHeader = request.headers.get('Authorization');
      if (authHeader === 'Bearer fresh-token') {
        return of(new HttpResponse({ status: 200, body: authHeader }));
      }
      return throwError(() => new HttpErrorResponse({ status: 401 }));
    });

    const response = await firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ));

    expect(authService.ensureAccessToken).toHaveBeenCalledWith({forceRefresh: true});
    expect((response as HttpResponse<string>).body).toBe('Bearer fresh-token');
  });

  it('logs out when AuthService cannot refresh a complete token pair', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.ensureAccessToken.mockReturnValue(throwError(() => new Error('Authentication response did not include a complete token pair')));
    const next = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 401 })));

    await expect(firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ))).rejects.toBeInstanceOf(Error);

    expect(authService.ensureAccessToken).toHaveBeenCalledWith({forceRefresh: true});
    expect(authService.logout).toHaveBeenCalledOnce();
  });

  it('does not log out when the retried request fails after refresh succeeds', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.ensureAccessToken.mockReturnValue(of('fresh-token'));

    const next = vi.fn((request: HttpRequest<unknown>) => {
      if (request.headers.get('Authorization') === 'Bearer fresh-token') {
        return throwError(() => new HttpErrorResponse({ status: 500 }));
      }
      return throwError(() => new HttpErrorResponse({ status: 401 }));
    });

    await expect(firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ))).rejects.toBeInstanceOf(HttpErrorResponse);

    expect(authService.ensureAccessToken).toHaveBeenCalledWith({forceRefresh: true});
    expect(authService.logout).not.toHaveBeenCalled();
  });

  it('logs out when the refresh request fails', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    authService.ensureAccessToken.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );

    const next = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 401 })));

    await expect(firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ))).rejects.toBeInstanceOf(HttpErrorResponse);

    expect(authService.logout).toHaveBeenCalledOnce();
  });

  it('rethrows non-401 errors without logging out', async () => {
    authService.getInternalAccessToken.mockReturnValue('token-123');
    const next = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 500 })));

    await expect(firstValueFrom(interceptor(
      new HttpRequest('GET', `${apiUrl}/books`),
      next
    ))).rejects.toBeInstanceOf(HttpErrorResponse);

    expect(authService.ensureAccessToken).not.toHaveBeenCalled();
    expect(authService.logout).not.toHaveBeenCalled();
  });

  it('delegates concurrent 401 refresh coordination to AuthService', async () => {
    authService.getInternalAccessToken.mockReturnValue('expired-token');
    const refreshSubject = new Subject<string>();
    authService.ensureAccessToken.mockReturnValue(refreshSubject.asObservable());

    const next = vi.fn((request: HttpRequest<unknown>) => {
      const authHeader = request.headers.get('Authorization');
      if (authHeader === 'Bearer refreshed-token') {
        return of(new HttpResponse({ status: 200, body: authHeader }));
      }
      return throwError(() => new HttpErrorResponse({ status: 401 }));
    });

    const firstRequest = firstValueFrom(interceptor(new HttpRequest('GET', `${apiUrl}/books`), next));
    const secondRequest = firstValueFrom(interceptor(new HttpRequest('GET', `${apiUrl}/libraries`), next));

    refreshSubject.next('refreshed-token');
    refreshSubject.complete();

    const [firstResponse, secondResponse] = await Promise.all([firstRequest, secondRequest]);

    expect(authService.ensureAccessToken).toHaveBeenCalledTimes(2);
    expect(authService.ensureAccessToken).toHaveBeenCalledWith({forceRefresh: true});
    expect((firstResponse as HttpResponse<string>).body).toBe('Bearer refreshed-token');
    expect((secondResponse as HttpResponse<string>).body).toBe('Bearer refreshed-token');
  });

  describe('isExcludedAuthRequest', () => {
    const next = vi.fn((request: HttpRequest<unknown>) =>
      of(new HttpResponse({ status: 200, body: request.headers.has('Authorization') }))
    );

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

    it('excludes /api/v1/public-settings', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('GET', `${API_CONFIG.BASE_URL}/api/v1/public-settings`), next));
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
      expect((response as HttpResponse<boolean>).body).toBe(false);
    });

    it('does NOT exclude /api/v1/auth/logout', async () => {
      authService.getInternalAccessToken.mockReturnValue('token-123');
      const response = await firstValueFrom(interceptor(new HttpRequest('POST', `${API_CONFIG.BASE_URL}/api/v1/auth/logout`, null), next));
      expect((response as HttpResponse<boolean>).body).toBe(true);
    });
  });
});
