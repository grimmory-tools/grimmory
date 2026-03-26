import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AppSettingsService} from '../../shared/service/app-settings.service';
import {API_CONFIG} from '../config/api-config';
import {OidcService} from './oidc.service';

describe('OidcService', () => {
  let service: OidcService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AppSettingsService,
          useValue: {},
        },
        OidcService,
      ]
    });

    service = TestBed.inject(OidcService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('generates PKCE verifier and challenge pairs', async () => {
    const digestSpy = vi.fn(async (_algorithm: string, value: Uint8Array) => value);
    const getRandomValuesSpy = vi.fn((array: Uint8Array) => {
      array.set(Uint8Array.from({length: array.length}, (_, index) => index + 1));
      return array;
    });
    vi.stubGlobal('crypto', {
      getRandomValues: getRandomValuesSpy,
      subtle: {digest: digestSpy},
    });

    const result = await service.generatePkce();

    expect(getRandomValuesSpy).toHaveBeenCalledOnce();
    expect(digestSpy).toHaveBeenCalledOnce();
    expect(result.codeVerifier).toBeTruthy();
    expect(result.codeChallenge).toBeTruthy();
  });

  it('generates random URL-safe strings', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (array: Uint8Array) => {
        array.set(Uint8Array.from({length: array.length}, (_, index) => index + 10));
        return array;
      },
      subtle: {digest: vi.fn()},
    });

    const value = service.generateRandomString();

    expect(value).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it('builds an auth url directly when the authorization endpoint is provided', async () => {
    const authUrl = await service.buildAuthUrl(
      'https://issuer.example',
      'client-id',
      'challenge',
      'state-token',
      'nonce-token',
      'https://issuer.example/authorize',
      'openid profile'
    );

    expect(authUrl).toContain('https://issuer.example/authorize?');
    expect(authUrl).toContain('client_id=client-id');
    expect(authUrl).toContain('scope=openid+profile');
    expect(authUrl).toContain('state=state-token');
  });

  it('builds an auth url from the OIDC discovery document when the endpoint is omitted', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      json: async () => ({authorization_endpoint: 'https://issuer.example/discovered-authorize'})
    }));

    const authUrl = await service.buildAuthUrl(
      'https://issuer.example/',
      'client-id',
      'challenge',
      'state-token',
      'nonce-token'
    );

    expect(fetch).toHaveBeenCalledWith('https://issuer.example/.well-known/openid-configuration');
    expect(authUrl).toContain('https://issuer.example/discovered-authorize?');
  });

  it('fetches the backend-generated OIDC state token', async () => {
    const statePromise = service.fetchState();
    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/oidc/state`);
    request.flush({state: 'server-state'});

    await expect(statePromise).resolves.toBe('server-state');
  });

  it('posts the callback payload to exchange an OIDC code for tokens', () => {
    service.exchangeCode('code-123', 'verifier', 'nonce', 'state').subscribe();

    const request = httpTestingController.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/oidc/callback`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      code: 'code-123',
      codeVerifier: 'verifier',
      redirectUri: `${window.location.origin}/oauth2-callback`,
      nonce: 'nonce',
      state: 'state',
    });
    request.flush({accessToken: 'access', refreshToken: 'refresh', isDefaultPassword: 'false'});
  });

  it('stores, retrieves, and removes pkce state entries', () => {
    service.storePkceState({codeVerifier: 'verifier', state: 'state', nonce: 'nonce'});

    expect(service.retrievePkceState('state')).toEqual({
      codeVerifier: 'verifier',
      state: 'state',
      nonce: 'nonce',
    });
    expect(service.retrievePkceState('state')).toBeNull();
  });

  it('returns null when stored PKCE state is missing or malformed', () => {
    expect(service.retrievePkceState('missing')).toBeNull();

    sessionStorage.setItem('oidc_pkce_bad', '{not-json');

    expect(service.retrievePkceState('bad')).toBeNull();
  });
});
