import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { API_CONFIG } from '../../core/config/api-config';
import { PostLoginInitializerService } from '../../core/services/post-login-initializer.service';
import { RxStompService } from '../websocket/rx-stomp.service';
import { AuthService, websocketInitializer } from './auth.service';

describe('AuthService', () => {
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };
  const rxStompService = {
    activate: vi.fn(),
    deactivate: vi.fn(),
    updateConfig: vi.fn(),
  };
  const postLoginInitializer = {
    initialize: vi.fn(() => of(undefined)),
  };

  let service: AuthService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: RxStompService, useValue: rxStompService },
        { provide: PostLoginInitializerService, useValue: postLoginInitializer },
        AuthService,
      ]
    });

    router.navigate.mockClear();
    rxStompService.activate.mockClear();
    rxStompService.deactivate.mockClear();
    rxStompService.updateConfig.mockClear();
    postLoginInitializer.initialize.mockClear();

    service = TestBed.inject(AuthService);
    httpTestingController = TestBed.inject(HttpTestingController);

    // Mock redirectTo for all tests using a narrow test-only type to access protected method
    vi.spyOn(service as unknown as { redirectTo: AuthService['redirectTo'] }, 'redirectTo')
      .mockImplementation(() => undefined);
  });

  afterEach(() => {
    httpTestingController.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('logs in with internal credentials and initializes the authenticated session', () => {
    service.internalLogin({ username: 'admin', password: 'secret' }).subscribe();

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'admin', password: 'secret' });
    request.flush({ accessToken: 'access', refreshToken: 'refresh', isDefaultPassword: false });

    expect(service.getInternalAccessToken()).toBe('access');
    expect(service.getInternalRefreshToken()).toBe('refresh');
    expect(service.token()).toBe('access');
    expect(rxStompService.updateConfig).toHaveBeenCalledOnce();
    expect(rxStompService.activate).toHaveBeenCalledOnce();
    expect(postLoginInitializer.initialize).toHaveBeenCalledOnce();
  });

  it('refreshes the token and persists the new values', () => {
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    service.refreshInternalSession().subscribe();

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    expect(request.request.body).toEqual({ refreshToken: 'refresh-token' });
    request.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    expect(service.getInternalAccessToken()).toBe('new-access');
    expect(service.getInternalRefreshToken()).toBe('new-refresh');
  });

  it('fails refresh when the backend does not return a complete token pair', async () => {
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const refresh = firstValueFrom(service.refreshInternalSession());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    request.flush({ accessToken: 'new-access', refreshToken: '' });

    await expect(refresh).rejects.toThrow('Authentication response did not include a complete token pair');
    expect(service.getInternalAccessToken()).toBeNull();
    expect(service.getInternalRefreshToken()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
  });

  it('does not send a refresh request when no refresh token is available', async () => {
    await expect(firstValueFrom(service.refreshInternalSession())).rejects.toThrow('No refresh token available');

    httpTestingController.expectNone(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
  });

  it('returns the current access token without refreshing when it is still valid', async () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() + 3600000));
    localStorage.setItem('refreshToken_Internal', 'refresh');

    await expect(firstValueFrom(service.ensureAccessToken())).resolves.toBe('access');

    httpTestingController.expectNone(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
  });

  it('force-refreshes the access token even when the current token has not expired', async () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() + 3600000));
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const accessToken = firstValueFrom(service.ensureAccessToken({forceRefresh: true}));

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    expect(request.request.body).toEqual({ refreshToken: 'refresh-token' });
    request.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    await expect(accessToken).resolves.toBe('new-access');
    expect(service.getInternalAccessToken()).toBe('new-access');
    expect(service.getInternalRefreshToken()).toBe('new-refresh');
  });

  it('refreshes an expired session before reporting authentication', async () => {
    localStorage.setItem('accessToken_Internal', 'expired-access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() - 1000));
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const authenticated = firstValueFrom(service.ensureAuthenticated());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    expect(request.request.body).toEqual({ refreshToken: 'refresh-token' });
    request.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    await expect(authenticated).resolves.toBe(true);
    expect(service.getInternalAccessToken()).toBe('new-access');
    expect(service.getInternalRefreshToken()).toBe('new-refresh');
    expect(rxStompService.updateConfig).toHaveBeenCalledOnce();
    expect(rxStompService.activate).toHaveBeenCalledOnce();
  });

  it('shares concurrent refresh attempts through one backend request', async () => {
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const firstRefresh = firstValueFrom(service.refreshInternalSession());
    const secondRefresh = firstValueFrom(service.refreshInternalSession());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    request.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    await expect(Promise.all([firstRefresh, secondRefresh])).resolves.toEqual([
      { accessToken: 'new-access', refreshToken: 'new-refresh' },
      { accessToken: 'new-access', refreshToken: 'new-refresh' },
    ]);
    expect(service.getInternalAccessToken()).toBe('new-access');
    expect(service.getInternalRefreshToken()).toBe('new-refresh');
  });

  it('does not restore tokens or websocket state when a refresh resolves after logout', async () => {
    localStorage.setItem('accessToken_Internal', 'expired-access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() - 1000));
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const refresh = firstValueFrom(service.refreshInternalSession());

    const refreshRequest = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);

    service.logout();

    const logoutRequest = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/logout`);
    expect(logoutRequest.request.body).toEqual({ refreshToken: 'refresh-token' });

    refreshRequest.flush({ accessToken: 'late-access', refreshToken: 'late-refresh' });
    logoutRequest.flush({ logoutUrl: null });

    await expect(refresh).rejects.toThrow('Refresh response belongs to a stale session');
    expect(service.getInternalAccessToken()).toBeNull();
    expect(service.getInternalRefreshToken()).toBeNull();
    expect(rxStompService.updateConfig).not.toHaveBeenCalled();
    expect(rxStompService.activate).not.toHaveBeenCalled();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
  });

  it('keeps the current session when re-login happens while refresh is in flight', async () => {
    localStorage.setItem('accessToken_Internal', 'expired-access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() - 1000));
    localStorage.setItem('refreshToken_Internal', 'old-refresh');

    const accessToken = firstValueFrom(service.ensureAccessToken());

    const refreshRequest = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    expect(refreshRequest.request.body).toEqual({ refreshToken: 'old-refresh' });

    service.saveInternalTokens('current-access', 'current-refresh');

    refreshRequest.flush({ accessToken: 'late-access', refreshToken: 'late-refresh' });

    await expect(accessToken).resolves.toBe('current-access');
    expect(service.getInternalAccessToken()).toBe('current-access');
    expect(service.getInternalRefreshToken()).toBe('current-refresh');
    expect(rxStompService.deactivate).not.toHaveBeenCalled();
  });

  it('clears the session when expired credentials cannot be refreshed', async () => {
    localStorage.setItem('accessToken_Internal', 'expired-access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() - 1000));
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const authenticated = firstValueFrom(service.ensureAuthenticated());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    request.flush('nope', { status: 401, statusText: 'Unauthorized' });

    await expect(authenticated).resolves.toBe(false);
    expect(service.getInternalAccessToken()).toBeNull();
    expect(service.getInternalRefreshToken()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
  });

  it('clears the session when refresh returns an incomplete token pair', async () => {
    localStorage.setItem('accessToken_Internal', 'expired-access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() - 1000));
    localStorage.setItem('refreshToken_Internal', 'refresh-token');

    const authenticated = firstValueFrom(service.ensureAuthenticated());

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    request.flush({ accessToken: 'new-access', refreshToken: '' });

    await expect(authenticated).resolves.toBe(false);
    expect(service.getInternalAccessToken()).toBeNull();
    expect(service.getInternalRefreshToken()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
  });

  it('does not refresh when the access token is still current', async () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() + 3600000));
    localStorage.setItem('refreshToken_Internal', 'refresh');

    await expect(firstValueFrom(service.ensureAuthenticated())).resolves.toBe(true);

    httpTestingController.expectNone(`${API_CONFIG.BASE_URL}/api/v1/auth/refresh`);
    expect(rxStompService.updateConfig).not.toHaveBeenCalled();
    expect(rxStompService.activate).not.toHaveBeenCalled();
  });

  it('handles remote login the same way as an internal login', () => {
    service.remoteLogin().subscribe();

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/remote`);
    expect(request.request.method).toBe('GET');
    request.flush({ accessToken: 'remote-access', refreshToken: 'remote-refresh', isDefaultPassword: false });

    expect(service.getInternalAccessToken()).toBe('remote-access');
    expect(rxStompService.activate).toHaveBeenCalledOnce();
    expect(postLoginInitializer.initialize).toHaveBeenCalledOnce();
  });

  it('logs out locally when the backend does not return a remote logout url', async () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('refreshToken_Internal', 'refresh');

    service.logout();

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/logout`);
    request.flush({ logoutUrl: null });

    await Promise.resolve();

    expect(service.token()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
    expect((service as unknown as { redirectTo: AuthService['redirectTo'] }).redirectTo).toHaveBeenCalledWith('/login', true);
  });

  it('logs out locally when the backend logout request fails', async () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('refreshToken_Internal', 'refresh');

    service.logout();

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/logout`);
    request.flush('boom', { status: 500, statusText: 'Server Error' });

    await Promise.resolve();

    expect(service.token()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
    expect((service as unknown as { redirectTo: AuthService['redirectTo'] }).redirectTo).toHaveBeenCalledWith('/login', true);
  });

  it('clears the session and navigates with a reason during force logout', () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('refreshToken_Internal', 'refresh');

    service.forceLogout('session_revoked');

    expect(service.token()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
    expect((service as unknown as { redirectTo: AuthService['redirectTo'] }).redirectTo).toHaveBeenCalledWith('/login?reason=session_revoked', true);
  });

  it('clears the session when the login page is opened', () => {
    localStorage.setItem('accessToken_Internal', 'access');
    localStorage.setItem('refreshToken_Internal', 'refresh');

    service.clearSessionOnLoginPage();

    expect(service.token()).toBeNull();
    expect(rxStompService.deactivate).toHaveBeenCalledOnce();
  });

  it('skips websocket initialization when there is no access token', () => {
    service.initializeWebSocketConnection();

    expect(rxStompService.updateConfig).not.toHaveBeenCalled();
    expect(postLoginInitializer.initialize).not.toHaveBeenCalled();
  });

  it('skips websocket initialization when the access token is expired', () => {
    localStorage.setItem('accessToken_Internal', 'expired-access');
    localStorage.setItem('accessToken_Internal_Expiry', String(Date.now() - 1000));

    service.initializeWebSocketConnection();

    expect(rxStompService.updateConfig).not.toHaveBeenCalled();
    expect(postLoginInitializer.initialize).not.toHaveBeenCalled();
  });

  it('initializes the websocket connection and post-login hooks only once', () => {
    service.saveInternalTokens('access', 'refresh');

    service.initializeWebSocketConnection();
    service.initializeWebSocketConnection();

    expect(rxStompService.updateConfig).toHaveBeenCalledTimes(2);
    expect(rxStompService.activate).toHaveBeenCalledTimes(2);
    expect(postLoginInitializer.initialize).toHaveBeenCalledOnce();
  });

  it('provides a websocket initializer wrapper', () => {
    const initializeSpy = vi.spyOn(service, 'initializeWebSocketConnection').mockImplementation(() => undefined);

    websocketInitializer(service)();

    expect(initializeSpy).toHaveBeenCalledOnce();
  });

  it('continues after post-login initialization failures', () => {
    postLoginInitializer.initialize.mockReturnValue(
      throwError(() => new Error('initializer failed'))
    );
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    service.saveInternalTokens('access', 'refresh');
    service.initializeWebSocketConnection();

    expect(errorSpy).toHaveBeenCalledOnce();
    expect(rxStompService.activate).toHaveBeenCalledOnce();
  });
});
