import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';

import {OidcService} from '../../../core/security/oidc.service';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {PublicAppSettings} from '../../service/app-settings.service';
import {AuthService} from '../../service/auth.service';
import {AppSettingsService} from '../../service/app-settings.service';
import {LoginComponent} from './login.component';

function createPublicSettings(overrides: Partial<PublicAppSettings> = {}): PublicAppSettings {
  return {
    oidcEnabled: true,
    remoteAuthEnabled: false,
    oidcForceOnlyMode: false,
    oidcProviderDetails: {
      providerName: 'Pocket ID',
      clientId: 'client-id',
      issuerUri: 'https://issuer.example',
      scopes: 'openid profile email',
      claimMapping: {
        username: 'preferred_username',
        email: 'email',
        name: 'name',
        groups: 'groups',
      }
    },
    ...overrides
  };
}

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let publicSettingsSignal: ReturnType<typeof signal<PublicAppSettings | null>>;

  const authService = {
    clearSessionOnLoginPage: vi.fn(),
    internalLogin: vi.fn(),
  };

  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };

  const oidcService = {
    generatePkce: vi.fn(),
    fetchState: vi.fn(),
    generateRandomString: vi.fn(),
    storePkceState: vi.fn(),
    buildAuthUrl: vi.fn(),
  };

  function configureComponent(
    queryParams: Record<string, string> = {},
    settingsOverrides: Partial<PublicAppSettings> = {},
    autoDetectChanges = true
  ): void {
    publicSettingsSignal = signal<PublicAppSettings | null>(createPublicSettings(settingsOverrides));

    TestBed.configureTestingModule({
      imports: [
        LoginComponent,
        getTranslocoModule(),
      ],
      providers: [
        {provide: AuthService, useValue: authService},
        {provide: Router, useValue: router},
        {provide: OidcService, useValue: oidcService},
        {
          provide: AppSettingsService,
          useValue: {
            publicAppSettings: publicSettingsSignal,
          }
        },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of(queryParams),
          }
        },
      ]
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    if (autoDetectChanges) {
      fixture.detectChanges();
    }
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    sessionStorage.clear();

    authService.clearSessionOnLoginPage.mockReset();
    authService.internalLogin.mockReset();
    router.navigate.mockClear();
    oidcService.generatePkce.mockReset();
    oidcService.fetchState.mockReset();
    oidcService.generateRandomString.mockReset();
    oidcService.storePkceState.mockReset();
    oidcService.buildAuthUrl.mockReset();
  });

  afterEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('clears the session and shows the session revoked message from query params', () => {
    configureComponent({reason: 'session_revoked'});

    expect(authService.clearSessionOnLoginPage).toHaveBeenCalledOnce();
    expect(component.infoMessage).toBe('auth.login.sessionRevoked');
  });

  it('maps known OIDC callback errors to translated messages', () => {
    configureComponent({oidcError: 'exchange_failed'});

    expect(component.errorMessage).toBe('auth.login.oidcErrors.exchangeFailed');
  });

  it('keeps the local login visible when OIDC-only mode is not enabled', () => {
    configureComponent();

    expect(component.oidcEnabled).toBe(true);
    expect(component.oidcName).toBe('Pocket ID');
    expect(component.showLocalLogin).toBe(true);
  });

  it('auto-redirects to OIDC in force-only mode when there is no local override or callback error', async () => {
    configureComponent({}, {oidcForceOnlyMode: true}, false);
    const loginWithOidcSpy = vi.spyOn(component, 'loginWithOidc').mockResolvedValue(undefined);

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.showLocalLogin).toBe(false);
    expect(sessionStorage.getItem('oidc_redirect_count')).toBe('1');
    expect(loginWithOidcSpy).toHaveBeenCalledOnce();
  });

  it('stops the OIDC auto-redirect loop after the maximum retry count', async () => {
    sessionStorage.setItem('oidc_redirect_count', '3');
    configureComponent({}, {oidcForceOnlyMode: true}, false);
    const loginWithOidcSpy = vi.spyOn(component, 'loginWithOidc').mockResolvedValue(undefined);

    fixture.detectChanges();
    await fixture.whenStable();

    expect([
      'auth.login.oidcOnlyRedirectFailed',
      'Unable to reach the identity provider. Please contact your administrator.'
    ]).toContain(component.errorMessage);
    expect(component.showLocalLogin).toBe(false);
    expect(sessionStorage.getItem('oidc_redirect_count')).toBeNull();
    expect(loginWithOidcSpy).not.toHaveBeenCalled();
  });

  it('navigates to change-password when the server marks the password as default', () => {
    configureComponent();
    authService.internalLogin.mockReturnValue(of({isDefaultPassword: 'true'}));

    component.username = 'admin';
    component.password = 'password';
    component.login();

    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('navigates to the dashboard after a successful non-default-password login', () => {
    configureComponent();
    authService.internalLogin.mockReturnValue(of({isDefaultPassword: 'false'}));

    component.username = 'admin';
    component.password = 'password';
    component.login();

    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('shows translated messages for network and rate-limit login failures', () => {
    configureComponent();

    authService.internalLogin.mockReturnValueOnce(throwError(() => ({status: 0})));
    component.login();
    expect(component.errorMessage).toBe('Cannot connect to the server. Please check your connection and try again.');

    authService.internalLogin.mockReturnValueOnce(throwError(() => ({status: 429})));
    component.login();
    expect(component.errorMessage).toBe('Too many failed login attempts. Please try again later.');
  });

  it('uses the backend error message when login fails unexpectedly', () => {
    configureComponent();
    authService.internalLogin.mockReturnValue(
      throwError(() => ({status: 500, error: {message: 'Bad credentials'}}))
    );

    component.login();

    expect(component.errorMessage).toBe('Bad credentials');
  });

  it('requires provider details before starting the OIDC flow', async () => {
    configureComponent();
    publicSettingsSignal.set(createPublicSettings({oidcProviderDetails: undefined as unknown as PublicAppSettings['oidcProviderDetails']}));

    await component.loginWithOidc();

    expect(component.errorMessage).toBe('Failed to initiate OIDC login. Please try again or use local login.');
    expect(component.isOidcLoginInProgress).toBe(false);
  });

  it('builds the OIDC auth URL and redirects the browser', async () => {
    configureComponent();
    oidcService.generatePkce.mockResolvedValue({codeVerifier: 'verifier', codeChallenge: 'challenge'});
    oidcService.fetchState.mockResolvedValue('state-token');
    oidcService.generateRandomString.mockReturnValue('nonce-token');
    oidcService.buildAuthUrl.mockResolvedValue('https://issuer.example/auth');

    await component.loginWithOidc();

    expect(oidcService.storePkceState).toHaveBeenCalledWith({
      codeVerifier: 'verifier',
      state: 'state-token',
      nonce: 'nonce-token',
    });
    expect(oidcService.buildAuthUrl).toHaveBeenCalledWith(
      'https://issuer.example',
      'client-id',
      'challenge',
      'state-token',
      'nonce-token',
      undefined,
      'openid profile email'
    );
    expect(component.errorMessage).toBe('');
  });

  it('shows a translated provider error when OIDC login initialization fails', async () => {
    configureComponent();
    oidcService.generatePkce.mockRejectedValue(new Error('crypto unavailable'));

    await component.loginWithOidc();

    expect(component.errorMessage).toBe('Could not reach the OIDC provider. Please try again later.');
    expect(component.isOidcLoginInProgress).toBe(false);
    expect(component.showLocalLogin).toBe(true);
  });
});
