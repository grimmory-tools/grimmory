import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../testing/transloco-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {OidcService} from '../oidc.service';
import {OidcCallbackComponent} from './oidc-callback.component';

describe('OidcCallbackComponent', () => {
  let fixture: ComponentFixture<OidcCallbackComponent>;

  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };
  const oidcService = {
    retrievePkceState: vi.fn(),
    exchangeCode: vi.fn(),
  };
  const authService = {
    saveInternalTokens: vi.fn(),
    initializeWebSocketConnection: vi.fn(),
  };

  function configureComponent(search = ''): void {
    window.history.replaceState({}, '', `/oauth2-callback${search}`);

    TestBed.configureTestingModule({
      imports: [
        OidcCallbackComponent,
        getTranslocoModule(),
      ],
      providers: [
        {provide: Router, useValue: router},
        {provide: OidcService, useValue: oidcService},
        {provide: AuthService, useValue: authService},
      ]
    });

    fixture = TestBed.createComponent(OidcCallbackComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    router.navigate.mockClear();
    oidcService.retrievePkceState.mockReset();
    oidcService.exchangeCode.mockReset();
    authService.saveInternalTokens.mockReset();
    authService.initializeWebSocketConnection.mockReset();
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    window.history.replaceState({}, '', '/');
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('redirects to login when the provider returns an error', () => {
    configureComponent('?error=access_denied&error_description=denied');

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {oidcError: 'denied'},
    });
  });

  it('redirects to login when code or state is missing', () => {
    configureComponent('?state=missing-code');

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {oidcError: 'missing_code'},
    });
  });

  it('redirects to login when there is no stored PKCE state', () => {
    oidcService.retrievePkceState.mockReturnValue(null);

    configureComponent('?code=code-123&state=state-123');

    expect(oidcService.retrievePkceState).toHaveBeenCalledWith('state-123');
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {oidcError: 'missing_pkce_state'},
    });
  });

  it('redirects to login when the returned state does not match the stored PKCE state', () => {
    oidcService.retrievePkceState.mockReturnValue({
      codeVerifier: 'verifier',
      nonce: 'nonce',
      state: 'other-state',
    });

    configureComponent('?code=code-123&state=state-123');

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {oidcError: 'state_mismatch'},
    });
  });

  it('saves tokens and redirects to change-password when the backend flags a default password', () => {
    sessionStorage.setItem('oidc_redirect_count', '2');
    oidcService.retrievePkceState.mockReturnValue({
      codeVerifier: 'verifier',
      nonce: 'nonce',
      state: 'state-123',
    });
    oidcService.exchangeCode.mockReturnValue(of({
      accessToken: 'access',
      refreshToken: 'refresh',
      isDefaultPassword: 'true',
    }));

    configureComponent('?code=code-123&state=state-123');

    expect(authService.saveInternalTokens).toHaveBeenCalledWith('access', 'refresh');
    expect(authService.initializeWebSocketConnection).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem('oidc_redirect_count')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('saves tokens and redirects to the dashboard after a successful exchange', () => {
    oidcService.retrievePkceState.mockReturnValue({
      codeVerifier: 'verifier',
      nonce: 'nonce',
      state: 'state-123',
    });
    oidcService.exchangeCode.mockReturnValue(of({
      accessToken: 'access',
      refreshToken: 'refresh',
      isDefaultPassword: 'false',
    }));

    configureComponent('?code=code-123&state=state-123');

    expect(authService.saveInternalTokens).toHaveBeenCalledWith('access', 'refresh');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('redirects to login with the backend error message when the exchange fails', () => {
    oidcService.retrievePkceState.mockReturnValue({
      codeVerifier: 'verifier',
      nonce: 'nonce',
      state: 'state-123',
    });
    oidcService.exchangeCode.mockReturnValue(
      throwError(() => ({error: {message: 'exchange_failed'}}))
    );

    configureComponent('?code=code-123&state=state-123');

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {oidcError: 'exchange_failed'},
    });
  });
});
